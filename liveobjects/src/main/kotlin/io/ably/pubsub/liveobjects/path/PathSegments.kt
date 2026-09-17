package io.ably.pubsub.liveobjects.path

/**
 * Dot-delimited path <-> segment list conversions. A dot inside a segment is escaped as `\.`
 * (RTPO4b); parsing honours the escape (RTPO6b). Mirrors ably-js pathobject.ts#at / #_escapePath.
 *
 * Root convention: the root PathObject stores the empty string, which represents ZERO segments
 * (RTPO4c) - unlike ably-js, which stores segment arrays and never parses a stored path. Every
 * helper below therefore treats an empty *stored base path* as zero segments via [parseStored];
 * [parse] itself is only ever given user-supplied sub-paths (where "" means one empty segment,
 * matching ably-js `at("")`) or non-empty stored paths.
 */
internal object PathSegments {

  /**
   * RTPO6b - split on unescaped dots; `\.` yields a literal dot; any other `\x` keeps the
   * backslash; a trailing lone `\` is kept. `""` parses to one empty segment (ably-js parity).
   * Manual scanner (no regex lookbehind), ported from ably-js pathobject.ts#at.
   */
  internal fun parse(path: String): List<String> = parse(path, strict = false)

  private fun parse(path: String, strict: Boolean): List<String> {
    val segments = mutableListOf<String>()
    val currentSegment = StringBuilder()
    var escaping = false
    for (char in path) {
      if (escaping) {
        // user-supplied paths keep the escape character unless it escapes a dot, replicating
        // ably-js behaviour where only escaped dots are unescaped; stored paths were produced
        // by [join], which escapes both '.' and '\', so strict mode unescapes both
        if (char != '.' && !(strict && char == '\\')) currentSegment.append('\\')
        currentSegment.append(char)
        escaping = false
        continue
      }
      when (char) {
        '\\' -> escaping = true
        '.' -> {
          segments.add(currentSegment.toString())
          currentSegment.setLength(0)
        }
        else -> currentSegment.append(char)
      }
    }
    if (escaping) {
      currentSegment.append('\\')
    }
    segments.add(currentSegment.toString())
    return segments
  }

  /**
   * RTPO4a/RTPO4b - join segments, escaping dots inside segments. Empty list -> "" (RTPO4c).
   * Backslashes are escaped too (deviation from ably-js `_escapePath`, which escapes only dots):
   * ably-js stores segment ARRAYS and its escaped string is display-only, but here the joined
   * string IS the storage and gets re-parsed by [parseStored] on every resolution. Without
   * doubling backslashes, a key ending in `\` collides with the escaped-dot separator
   * (`["a\", "b"]` -> `a\.b` -> re-parses as `["a.b"]`), breaking lookups and subscriptions.
   */
  internal fun join(segments: List<String>): String =
    segments.joinToString(".") { it.replace("\\", "\\\\").replace(".", "\\.") }

  /**
   * Stored-path parsing: empty stored path = root = zero segments. Use for `this.path`, never
   * raw [parse]. Stored paths only ever come from [join], so this inverts join's full escaping
   * (`\\` -> `\` as well as `\.` -> `.`).
   */
  internal fun parseStored(path: String): List<String> =
    if (path.isEmpty()) emptyList() else parse(path, strict = true)

  /** RTPO5c - append one raw key (escaping it) to an existing stored path. */
  internal fun appendKey(path: String, key: String): String = join(parseStored(path) + key)

  /** RTPO6c - append a dot-delimited sub-path to an existing stored path. */
  internal fun appendPath(path: String, subPath: String): String = join(parseStored(path) + parse(subPath))
}
