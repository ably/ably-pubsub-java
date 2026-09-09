package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.types.AblyException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Derived from UTS spec `objects/unit/path_object_mutations.md` — PathObject write operations
 * (`RTPO15`–`RTPO18`, `RTPO3c2`): set/remove/increment/decrement delegation, default amounts,
 * wrong-type failures (92007) and unresolvable-path failures (92005).
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`. Wrong-type
 * write failures translate through the never-throwing `PathObject` casts per
 * `objects-mapping.md` §7/§12 (RTTS5d) — the typed view lacks the wrong method, so the test
 * casts to the view carrying it and asserts the *operation* throws.
 */
class PathObjectMutationsTest {

    /**
     * @UTS objects/unit/RTPO15/set-delegates-to-map-0
     */
    @Test
    fun `RTPO15 - set delegates to InternalLiveMap set`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.set("name", LiveMapValue.of("Bob")).await()

        assertEquals("Bob", root.get("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO15/set-nested-path-0
     */
    @Test
    fun `RTPO15 - set on nested path`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("profile").asLiveMap().set("email", LiveMapValue.of("bob@example.com")).await()

        assertEquals("bob@example.com", root.get("profile").asLiveMap().get("email").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO15d/set-non-map-throws-0
     */
    @Test
    fun `RTPO15d - set on non-InternalLiveMap throws 92007`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.get("score").asLiveMap().set("key", LiveMapValue.of("value")).await()
        }

        assertEquals(92007, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO16/remove-delegates-to-map-0
     */
    @Test
    fun `RTPO16 - remove delegates to InternalLiveMap remove`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.remove("name").await()

        assertNull(root.get("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO16d/remove-non-map-throws-0
     */
    @Test
    fun `RTPO16d - remove on non-InternalLiveMap throws 92007`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.get("score").asLiveMap().remove("key").await()
        }

        assertEquals(92007, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO17/increment-delegates-to-counter-0
     */
    @Test
    fun `RTPO17 - increment delegates to InternalLiveCounter increment`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(25).await()

        assertEquals(125.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO17/increment-default-amount-0
     */
    @Test
    fun `RTPO17 - increment defaults to 1`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment().await()

        assertEquals(101.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO17d/increment-non-counter-throws-0
     */
    @Test
    fun `RTPO17d - increment on non-InternalLiveCounter throws 92007`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.asLiveCounter().increment(5).await()
        }

        assertEquals(92007, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO18/decrement-delegates-to-counter-0
     */
    @Test
    fun `RTPO18 - decrement delegates to InternalLiveCounter decrement`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().decrement(10).await()

        assertEquals(90.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO18/decrement-default-amount-0
     */
    @Test
    fun `RTPO18 - decrement defaults to 1`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().decrement().await()

        assertEquals(99.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO18d/decrement-non-counter-throws-0
     */
    @Test
    fun `RTPO18d - decrement on non-InternalLiveCounter throws 92007`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.asLiveCounter().decrement(5).await()
        }

        assertEquals(92007, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO3c2/set-unresolvable-throws-0
     */
    @Test
    fun `RTPO3c2 - set on unresolvable path throws 92005`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.get("nonexistent").asLiveMap().get("deep").asLiveMap().set("key", LiveMapValue.of("value")).await()
        }

        assertEquals(92005, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO3c2/increment-unresolvable-throws-0
     */
    @Test
    fun `RTPO3c2 - increment on unresolvable path throws 92005`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.get("nonexistent").asLiveCounter().increment(5).await()
        }

        assertEquals(92005, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }
}
