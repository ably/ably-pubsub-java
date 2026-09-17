package io.ably.pubsub.liveobjects.integration.helpers.fixtures

import io.ably.pubsub.liveobjects.integration.helpers.HttpObjects

/**
 * Creates a comprehensive test fixture object tree focused on user-context counters.
 *
 * This method establishes a hierarchical structure of live counter objects for testing
 * counter operations in a realistic user engagement context, creating various types of
 * counters and establishing references between them through nested maps.
 *
 * **Object Tree Structure:**
 * ```
 * userMap (Map)
 * ├── profileViews → Counter(value=127)
 * ├── postLikes → Counter(value=45)
 * ├── commentCount → Counter(value=23)
 * ├── followingCount → Counter(value=89)
 * ├── followersCount → Counter(value=156)
 * ├── loginStreak → Counter(value=7)
 * └── engagementMetrics → Map{
 *     ├── "totalShares" → Counter(value=34)
 *     ├── "totalBookmarks" → Counter(value=67)
 *     ├── "totalReactions" → Counter(value=189)
 *     └── "dailyActiveStreak" → Counter(value=12)
 *     }
 * ```
 *
 * @param channelName The channel where the counter object tree will be created
 * @return The object ID of the root test map containing all counter references
 */
internal fun HttpObjects.createUserMapWithCountersObject(channelName: String): String {
  // Create the main test map first
  val testMapObjectId = createMap(channelName)

  // Create various user-context relevant counters
  val profileViewsCounterObjectId = createCounter(channelName, 127.0)
  val postLikesCounterObjectId = createCounter(channelName, 45.0)
  val commentCountCounterObjectId = createCounter(channelName, 23.0)
  val followingCountCounterObjectId = createCounter(channelName, 89.0)
  val followersCountCounterObjectId = createCounter(channelName, 156.0)
  val loginStreakCounterObjectId = createCounter(channelName, 7.0)

  // Create engagement metrics nested map with counters
  val engagementMetricsMapObjectId = createUserEngagementMatrixMap(channelName)

  // Set up the main test map structure with references to all created counters
  setMapRef(channelName, testMapObjectId, "profileViews", profileViewsCounterObjectId)
  setMapRef(channelName, testMapObjectId, "postLikes", postLikesCounterObjectId)
  setMapRef(channelName, testMapObjectId, "commentCount", commentCountCounterObjectId)
  setMapRef(channelName, testMapObjectId, "followingCount", followingCountCounterObjectId)
  setMapRef(channelName, testMapObjectId, "followersCount", followersCountCounterObjectId)
  setMapRef(channelName, testMapObjectId, "loginStreak", loginStreakCounterObjectId)
  setMapRef(channelName, testMapObjectId, "engagementMetrics", engagementMetricsMapObjectId)

  return testMapObjectId
}

/**
 * Creates a user engagement matrix map object with counter references for testing.
 *
 * This method creates a simple engagement metrics map containing counter objects
 * that track various user engagement metrics. The map contains references to
 * counter objects representing different types of user interactions and activities.
 *
 * **Object Structure:**
 * ```
 * userEngagementMatrixMap (Map)
 * ├── "totalShares" → Counter(value=34)
 * ├── "totalBookmarks" → Counter(value=67)
 * ├── "totalReactions" → Counter(value=189)
 * └── "dailyActiveStreak" → Counter(value=12)
 * ```
 *
 * @param channelName The channel where the user engagement matrix map will be created
 * @return The object ID of the created user engagement matrix map
 */
internal fun HttpObjects.createUserEngagementMatrixMap(channelName: String): String {
  return createMap(
    channelName,
    data = mapOf(
      "totalShares" to DataFixtures.mapRef(createCounter(channelName, 34.0)),
      "totalBookmarks" to DataFixtures.mapRef(createCounter(channelName, 67.0)),
      "totalReactions" to DataFixtures.mapRef(createCounter(channelName, 189.0)),
      "dailyActiveStreak" to DataFixtures.mapRef(createCounter(channelName, 12.0))
    )
  )
}
