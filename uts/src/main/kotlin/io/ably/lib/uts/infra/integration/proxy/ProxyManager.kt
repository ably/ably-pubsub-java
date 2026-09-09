package io.ably.lib.uts.infra.integration.proxy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

// GitHub release downloads 302-redirect to the asset CDN; Ktor CIO follows redirects by default.
private val client = HttpClient(CIO) {
    followRedirects = true
    // Finite timeouts so a stalled download/health endpoint fails fast instead of hanging the suite.
    // requestTimeout is generous to allow the binary download; connect/socket are tighter.
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
}

/**
 * Manages the lifecycle of the `uts-proxy` binary used for integration tests.
 *
 * Downloads the binary from GitHub releases on first use, caching it at
 * `~/.cache/uts-proxy/<version>/uts-proxy`. The download is serialised across OS processes by a
 * `FileLock` on `uts-proxy.lock`, and within a JVM by a [Mutex]. Note: only the *download* is
 * cross-process locked — process startup relies on the shared health check on [CONTROL_PORT], so
 * proxy suites should run single-fork (`maxParallelForks = 1`) to avoid two workers racing to bind
 * the control port.
 *
 * The spawned process is reaped by a JVM shutdown hook registered in `init`; [stopProxy] stops it
 * explicitly.
 *
 * To run against a locally built proxy instead of downloading a release, point
 * [localDistributive] at a local `uts-proxy` binary or a `.tar.gz` distributive — set
 * either the `uts.proxy.localPath` system property or the `UTS_PROXY_LOCAL_PATH`
 * environment variable. When set, the download and checksum verification are skipped.
 *
 * Call [ensureProxy] in `@BeforeAll` / `setUpAll()` for every proxy integration test suite.
 */
object ProxyManager {

    private const val PROXY_VERSION = "v0.3.0"
    private const val VERSION_BARE = "0.3.0"
    const val CONTROL_PORT = 10100
    private const val GITHUB_BASE =
        "https://github.com/ably/uts-proxy/releases/download/$PROXY_VERSION"

    private val CHECKSUMS = mapOf(
        "uts-proxy_${VERSION_BARE}_darwin_amd64.tar.gz" to
            "1355526543c3022f87efb7f564f55200b78edc68d84c7dba2e49f63429e3b788",
        "uts-proxy_${VERSION_BARE}_darwin_arm64.tar.gz" to
            "a948f99b7daf9b3bffff742f6405637d40a79947389309eed5f87e59026de9a5",
        "uts-proxy_${VERSION_BARE}_linux_amd64.tar.gz"  to
            "de741ba21f3630fea4f59714d00585638d565005599ecd84179931eba248f280",
        "uts-proxy_${VERSION_BARE}_linux_arm64.tar.gz"  to
            "15b5ca87c40c2c4ff350c94af1911cea0ad6be5a2d890ba41029bc4b8bc52c61",
    )

    private val os: String by lazy {
        val name = System.getProperty("os.name").lowercase()
        when {
            name.contains("mac")   -> "darwin"
            name.contains("linux") -> "linux"
            else -> error("Unsupported OS for uts-proxy: ${System.getProperty("os.name")}")
        }
    }

    private val arch: String by lazy {
        when (System.getProperty("os.arch").lowercase()) {
            "amd64", "x86_64"   -> "amd64"
            "aarch64", "arm64"  -> "arm64"
            else -> error("Unsupported arch for uts-proxy: ${System.getProperty("os.arch")}")
        }
    }

    private val archiveName: String get() = "uts-proxy_${VERSION_BARE}_${os}_${arch}.tar.gz"

    private val cacheDir: Path
        get() = Path.of(System.getProperty("user.home"), ".cache", "uts-proxy", PROXY_VERSION)

    private val binaryPath: Path get() = cacheDir.resolve("uts-proxy")

    /**
     * Optional path to a locally built `uts-proxy` binary or `.tar.gz` distributive, taken
     * from the `uts.proxy.localPath` system property or the `UTS_PROXY_LOCAL_PATH`
     * environment variable. When present, the release download + checksum check are bypassed.
     */
    private val localDistributive: Path?
        get() = (System.getProperty("uts.proxy.localPath")
            ?: System.getenv("UTS_PROXY_LOCAL_PATH"))
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }

    @Volatile private var proxyProcess: Process? = null
    private val mutex = Mutex()

    init {
        // A ProcessBuilder child does NOT die with the parent JVM, so kill it explicitly on exit.
        Runtime.getRuntime().addShutdownHook(
            Thread {
                proxyProcess?.destroyForcibly()
                runCatching { client.close() }
            },
        )
    }

    /**
     * Ensures the `uts-proxy` process is running on [CONTROL_PORT].
     *
     * If the proxy is already healthy (e.g. started by a previous test class in the same run),
     * this is a no-op. Otherwise it downloads + verifies the binary and starts the process.
     *
     * @param timeoutMs Maximum real-time milliseconds to wait for the process to become healthy.
     */
    suspend fun ensureProxy(timeoutMs: Int = 15_000): Unit = mutex.withLock {
        if (isHealthy()) return
        ensureBinary()
        proxyProcess = withContext(Dispatchers.IO) {
            ProcessBuilder(binaryPath.toString(), "--port", "$CONTROL_PORT")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
        waitForHealth(timeoutMs.toLong())
    }

    /**
     * Stops the shared proxy process if one is running.
     *
     * The process is normally left running for the lifetime of the test run (it is reused across
     * suites) and reaped by the JVM shutdown hook. This method is exposed for explicit teardown.
     */
    fun stopProxy() {
        proxyProcess?.destroyForcibly()
        proxyProcess = null
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    internal suspend fun isHealthy(): Boolean = runCatching {
        client.get("http://localhost:$CONTROL_PORT/health").status.value == 200
    }.getOrDefault(false)

    private suspend fun waitForHealth(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) return
            delay(200)
        }
        proxyProcess?.destroyForcibly()
        proxyProcess = null
        error("uts-proxy did not become healthy within ${timeoutMs}ms")
    }

    /** Ensures the binary is present in the cache, downloading and extracting if needed. */
    private suspend fun ensureBinary() = withContext(Dispatchers.IO) {
        localDistributive?.let { installLocalDistributive(it); return@withContext }
        Files.createDirectories(cacheDir)
        // FileLock serialises across multiple Gradle test worker JVMs.
        val lockFile = cacheDir.resolve("uts-proxy.lock")
        FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
            channel.lock().use {
                val file = binaryPath.toFile()
                // The archive (not the extracted binary) is checksum-verified at download time, and
                // the cache dir is keyed on PROXY_VERSION, so a present+executable binary is a hit.
                // (Comparing the binary's hash to CHECKSUMS — the *archive* hash — could never match.)
                if (file.exists() && file.canExecute()) {
                    return@withContext  // already cached and valid
                }
                val archiveBytes = downloadArchive()
                verifyChecksum(archiveBytes)
                val binary = extractFromTarGz(archiveBytes)
                Files.write(binaryPath, binary, CREATE, TRUNCATE_EXISTING)
                binaryPath.toFile().setExecutable(true)
            }
        }
    }

    /**
     * Installs a locally provided distributive into the cache, skipping download + checksum.
     * The path may be a raw `uts-proxy` binary or a `.tar.gz` archive containing one.
     */
    private fun installLocalDistributive(path: Path) {
        require(Files.exists(path)) { "Local uts-proxy distributive not found at $path" }
        System.err.println("Using local uts-proxy distributive: $path")
        Files.createDirectories(cacheDir)
        val binary = if (path.fileName.toString().endsWith(".tar.gz")) {
            extractFromTarGz(Files.readAllBytes(path))
        } else {
            Files.readAllBytes(path)
        }
        Files.write(binaryPath, binary, CREATE, TRUNCATE_EXISTING)
        binaryPath.toFile().setExecutable(true)
    }

    private suspend fun downloadArchive(): ByteArray {
        System.err.println("Downloading uts-proxy $PROXY_VERSION ($archiveName)…")
        val response = client.get("$GITHUB_BASE/$archiveName")
        check(response.status.value == 200) {
            "Failed to download uts-proxy from $GITHUB_BASE/$archiveName: HTTP ${response.status.value}"
        }
        return response.body()
    }

    private fun verifyChecksum(bytes: ByteArray) {
        val expected = CHECKSUMS[archiveName]
            ?: error("No checksum for $archiveName — unsupported platform/arch")
        val actual = sha256Hex(bytes)
        check(actual == expected) {
            "Checksum mismatch for $archiveName: expected $expected, got $actual"
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Extracts the `uts-proxy` binary from a `.tar.gz` archive using only JDK stdlib.
     *
     * TAR format: sequential 512-byte header blocks each followed by file-data blocks
     * (padded to a multiple of 512). We parse only the fields we need:
     * - offset   0–99 : filename (null-terminated)
     * - offset 124–135: file size in octal ASCII
     * - offset    156 : entry type ('0'/NUL = regular file, '5' = directory, …)
     */
    private fun extractFromTarGz(archiveBytes: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(archiveBytes)).use { gzip ->
            val headerBuf = ByteArray(512)
            while (true) {
                // Read one header block (exactly 512 bytes)
                var totalRead = 0
                while (totalRead < 512) {
                    val n = gzip.read(headerBuf, totalRead, 512 - totalRead)
                    if (n < 0) break
                    totalRead += n
                }
                // End-of-archive: two consecutive zero-filled 512-byte blocks
                if (totalRead < 512 || headerBuf.all { it == 0.toByte() }) break

                // Filename (null-terminated, strip leading ./ or /)
                val nameEnd = (0 until 100).firstOrNull { headerBuf[it] == 0.toByte() } ?: 100
                val name = String(headerBuf, 0, nameEnd).trimStart('.', '/')

                // File size (octal ASCII at offset 124, 12 bytes)
                val sizeStr = String(headerBuf, 124, 12).trimEnd(' ').trim()
                val size = if (sizeStr.isEmpty()) 0L else sizeStr.toLong(8)

                // Entry type flag at offset 156
                val typeFlag = headerBuf[156].toInt().toChar()
                val isRegularFile = typeFlag == '0' || typeFlag == ' '

                if (isRegularFile && name == "uts-proxy" && size > 0) {
                    val content = ByteArray(size.toInt())
                    var read = 0
                    while (read < size) {
                        val n = gzip.read(content, read, (size - read).toInt())
                        if (n < 0) error("Unexpected end of archive while reading uts-proxy entry")
                        read += n
                    }
                    return content
                }

                // Skip this entry's data blocks (size rounded up to 512-byte boundary).
                // Read-and-discard rather than skip(): InputStream.skip() may return 0 before EOF,
                // which would mis-align the stream and break parsing of later entries.
                val dataBytes = (size + 511) / 512 * 512
                var skipped = 0L
                val skipBuf = ByteArray(8192)
                while (skipped < dataBytes) {
                    val toRead = minOf(skipBuf.size.toLong(), dataBytes - skipped).toInt()
                    val n = gzip.read(skipBuf, 0, toRead)
                    if (n < 0) error("Unexpected end of archive while skipping entry '$name'")
                    skipped += n
                }
            }
        }
        error("uts-proxy binary not found in archive '$archiveName'")
    }
}
