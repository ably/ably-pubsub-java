package io.ably.pubsub.liveobjects.unit

import io.ably.pubsub.liveobjects.path.PathSegments
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Encodes the stored-vs-supplied path invariants that the root convention relies on
 * (empty stored path = root = zero segments, while a user-supplied "" is one empty segment,
 * matching ably-js `at("")`). Spec: RTPO4a, RTPO4b, RTPO4c, RTPO5c, RTPO6b, RTPO6c
 */
class PathSegmentsTest {

  @Test
  fun testAppendKeyToRootYieldsBareKey() {
    // RTPO5c - appending to the root's empty stored path must not produce a leading dot
    assertEquals("a", PathSegments.appendKey("", "a"))
  }

  @Test
  fun testAppendKeyEscapesDotsInKey() {
    // RTPO4b - a raw key containing dots is a single segment; its dots get escaped
    assertEquals("a.b\\.c", PathSegments.appendKey("a", "b.c"))
  }

  @Test
  fun testParseHonoursEscapedDots() {
    // RTPO6b - `\.` is a literal dot inside a segment, not a separator
    assertEquals(listOf("a.b", "c"), PathSegments.parse("a\\.b.c"))
  }

  @Test
  fun testParseStoredEmptyIsRoot() {
    // RTPO4c - the root PathObject stores "" which represents ZERO segments
    assertEquals(emptyList<String>(), PathSegments.parseStored(""))
  }

  @Test
  fun testParseEmptyIsOneEmptySegment() {
    // user-supplied "" means one empty segment, matching ably-js `at("")`
    assertEquals(listOf(""), PathSegments.parse(""))
  }

  @Test
  fun testJoinEmptyListIsRootPath() {
    // RTPO4c - inverse of parseStored("")
    assertEquals("", PathSegments.join(emptyList()))
  }

  @Test
  fun testAppendPathSplitsOnUnescapedDots() {
    // RTPO6c - a supplied sub-path is dot-delimited; escaped dots stay within their segment
    assertEquals("a.b.c", PathSegments.appendPath("a", "b.c"))
    assertEquals("a.b\\.c", PathSegments.appendPath("a", "b\\.c"))
    assertEquals("b.c", PathSegments.appendPath("", "b.c"))
  }

  @Test
  fun testNonDotEscapeKeepsBackslash() {
    // ably-js parity: only escaped dots are unescaped; `\x` keeps its backslash
    assertEquals(listOf("a\\b", "c"), PathSegments.parse("a\\b.c"))
    // a trailing lone backslash is kept
    assertEquals(listOf("a\\"), PathSegments.parse("a\\"))
  }

  @Test
  fun testBackslashKeysRoundTrip() {
    // join escapes backslashes (unlike ably-js display-only _escapePath) because the joined
    // string IS the storage here and gets re-parsed. A key ending in `\` must not collide
    // with the escaped-dot separator.
    val stored = PathSegments.appendKey(PathSegments.appendKey("", "a\\"), "b")
    assertEquals("a\\\\.b", stored)
    assertEquals(listOf("a\\", "b"), PathSegments.parseStored(stored))

    // a single key containing `\.` (chars a,\,.,b) stays one segment through the round-trip
    val storedSingle = PathSegments.appendKey("", "a\\.b")
    assertEquals("a\\\\\\.b", storedSingle)
    assertEquals(listOf("a\\.b"), PathSegments.parseStored(storedSingle))
  }

  @Test
  fun testUserParseKeepsDoubleBackslash() {
    // stored-path strict unescaping must not leak into user-supplied sub-path parsing:
    // ably-js `at("a\\b")` keeps both backslashes, and so does parse()
    assertEquals(listOf("a\\\\b"), PathSegments.parse("a\\\\b"))
  }

  @Test
  fun testStoredPathRoundTrip() {
    // join(parseStored(p)) == p for stored paths produced by appendKey/appendPath
    val stored = PathSegments.appendKey(PathSegments.appendKey("", "a.b"), "c")
    assertEquals("a\\.b.c", stored)
    assertEquals(listOf("a.b", "c"), PathSegments.parseStored(stored))
    assertEquals(stored, PathSegments.join(PathSegments.parseStored(stored)))
  }
}
