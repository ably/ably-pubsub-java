package io.ably.lib.uts.infra.unit

import io.ably.lib.types.ConnectionDetails

/**
 * Test-only builder DSL for [ConnectionDetails], e.g.
 * `ConnectionDetails { connectionKey = "key-1"; connectionStateTtl = 120_000L }`.
 *
 * [ConnectionDetails]'s no-arg constructor is package-private to `io.ably.lib.types`, so it cannot be
 * invoked directly from this package. We obtain an instance reflectively via [newConnectionDetails] —
 * the same package-private-access technique used by `liveobjects/.../TestUtils.kt`.
 */
fun ConnectionDetails(init: ConnectionDetails.() -> Unit): ConnectionDetails =
    newConnectionDetails().apply(init)

/** Reflectively invokes [ConnectionDetails]'s package-private no-arg constructor. */
private fun newConnectionDetails(): ConnectionDetails =
    ConnectionDetails::class.java.getDeclaredConstructor()
        .apply { isAccessible = true }
        .newInstance()
