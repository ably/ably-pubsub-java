package io.ably.lib.liveobjects

/**
 * @spec RTO5 - SyncTracker class for tracking objects sync status
 */
internal class ObjectsSyncTracker(syncChannelSerial: String?) {
  private val syncSerial: String?
  internal val syncId: String?
  internal val syncCursor: String?

  /**
   * RTO5a6 - true when the channelSerial is present (non-empty) but malformed: it does not contain
   * the `:` separator required by RTO5a1 and so cannot be split into a `<sequence id>` and a
   * `<cursor value>`. Per RTO5a6 the caller handles such a serial as if it were absent (RTO5a5) and
   * should log a warning; the flag exists so the caller can emit that warning.
   *
   * An absent/empty channelSerial (RTO5a5, a valid single-message sync) is NOT malformed.
   */
  internal val isMalformed: Boolean

  init {
    val parsed = parseSyncChannelSerial(syncChannelSerial)
    syncId = parsed.syncId
    syncCursor = parsed.syncCursor
    isMalformed = parsed.isMalformed
    // RTO5a6 - normalize a malformed serial to null so hasSyncStarted/hasSyncEnded take the same absent-serial branch as RTO5a5; isMalformed is kept only for the caller's warning
    syncSerial = if (parsed.isMalformed) null else syncChannelSerial
  }

  /**
   * Checks if a new sync sequence has started.
   *
   * @param prevSyncId The previously stored sync ID
   * @return true if a new sync sequence has started, false otherwise
   *
   * A malformed serial is normalized to a null [syncSerial] in `init`, so it takes the same
   * absent-serial branch here (RTO5a6 -> RTO5a5).
   *
   * Spec: RTO5a5, RTO5a2, RTO5a6
   */
  internal fun hasSyncStarted(prevSyncId: String?): Boolean {
    return syncSerial.isNullOrEmpty() || prevSyncId != syncId
  }

  /**
   * Checks if the current sync sequence has ended.
   *
   * @return true if the sync sequence has ended, false otherwise
   *
   * A malformed serial is normalized to a null [syncSerial] in `init`, so it takes the same
   * absent-serial branch here (RTO5a6 -> RTO5a5).
   *
   * Spec: RTO5a5, RTO5a4, RTO5a6
   */
  internal fun hasSyncEnded(): Boolean {
    return syncSerial.isNullOrEmpty() || syncCursor.isNullOrEmpty()
  }

  companion object {
    /** Parsed form of a `channelSerial`, distinguishing absent (RTO5a5) from malformed (RTO5a6). */
    private data class ParsedSyncChannelSerial(
      val syncId: String?,
      val syncCursor: String?,
      val isMalformed: Boolean,
    )

    /**
     * Parses sync channel serial to extract syncId and syncCursor.
     *
     * @param syncChannelSerial The sync channel serial to parse
     * @return the parsed syncId/syncCursor, plus [ParsedSyncChannelSerial.isMalformed] flagging the
     *   RTO5a6 present-but-unparseable case (distinct from the RTO5a5 absent-serial case)
     */
    private fun parseSyncChannelSerial(syncChannelSerial: String?): ParsedSyncChannelSerial {
      // RTO5a5 - an absent/empty channelSerial is valid (self-contained sync), not malformed
      if (syncChannelSerial.isNullOrEmpty()) {
        return ParsedSyncChannelSerial(syncId = null, syncCursor = null, isMalformed = false)
      }

      // RTO5a1 - syncChannelSerial is a two-part identifier: <sequence id>:<cursor value>
      val match = Regex("^([\\w-]+):(.*)$").find(syncChannelSerial)
      return if (match != null) {
        ParsedSyncChannelSerial(
          syncId = match.groupValues[1],
          syncCursor = match.groupValues[2],
          isMalformed = false,
        )
      } else {
        // RTO5a6 - present but lacks the `:` separator to split into <sequence id>:<cursor value>; flag it so the caller warns and treats it as absent
        ParsedSyncChannelSerial(syncId = null, syncCursor = null, isMalformed = true)
      }
    }
  }
}
