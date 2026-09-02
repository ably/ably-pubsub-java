package io.ably.lib.uts.infra.integration

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal HS256 Ably JWT signer, built on JDK crypto only (no external JWT library).
 *
 * Exists for tests that need token claims the native Ably token format cannot carry —
 * notably `x-ably-clientType=server`: on token auth the realtime service accepts a
 * server-side declaration only from that signed claim (a `-server` agent entry alone is
 * rejected with error 40167), and the native token format cannot carry the claim yet. So a
 * JWT is the one way a token-authenticated client can declare the server side, and JWT-based
 * tests can run on the server UTS leg while native-token tests remain skipped (see
 * assumeSideSupportsTokenAuth).
 */
object AblyJwt {
    /**
     * Signs a JWT with the given Ably API key (`keyName:keySecret`), valid for [ttlSeconds],
     * with wildcard capability, and the optional Ably claims.
     */
    fun sign(
        keyStr: String,
        clientId: String? = null,
        clientType: String? = null,
        ttlSeconds: Long = 3600,
    ): String {
        val keyName = keyStr.substringBefore(':')
        val keySecret = keyStr.substringAfter(':')
        val now = System.currentTimeMillis() / 1000
        val header = """{"typ":"JWT","alg":"HS256","kid":"$keyName"}"""
        val claims = buildString {
            append("""{"iat":$now,"exp":${now + ttlSeconds},"x-ably-capability":"{\"*\":[\"*\"]}"""")
            if (clientId != null) append(""","x-ably-clientId":"$clientId"""")
            if (clientType != null) append(""","x-ably-clientType":"$clientType"""")
            append("}")
        }
        val enc = Base64.getUrlEncoder().withoutPadding()
        val signingInput = enc.encodeToString(header.toByteArray(Charsets.UTF_8)) + "." +
            enc.encodeToString(claims.toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(keySecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        val signature = enc.encodeToString(mac.doFinal(signingInput.toByteArray(Charsets.UTF_8)))
        return "$signingInput.$signature"
    }
}
