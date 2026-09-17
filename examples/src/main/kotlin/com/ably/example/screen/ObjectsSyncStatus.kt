package com.ably.example.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.example.observeObjectsSyncState
import io.ably.pubsub.liveobjects.path.types.LiveMapPathObject
import io.ably.pubsub.liveobjects.state.ObjectStateEvent
import io.ably.pubsub.realtime.Channel

/**
 * Shows the channel's objects synchronization progress: a spinner with
 * "Objects syncing..." while the initial sync (or a re-sync) is in flight, and a
 * check mark with "Objects synced" once local state matches the channel.
 *
 * Sync-state events fire on transitions only, so a sync that completed before this
 * composable subscribed emits nothing; a non-null [root] proves `channel.object.get()`
 * has completed, which implies SYNCED. A later event (e.g. a re-sync after reattach)
 * takes precedence over that inference.
 */
@Composable
fun ObjectsSyncStatusRow(channel: Channel, root: LiveMapPathObject?) {
  val syncState = observeObjectsSyncState(channel)
  val synced = syncState?.let { it == ObjectStateEvent.SYNCED } ?: (root != null)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.testTag("objects_sync_status")
  ) {
    if (synced) {
      Icon(
        Icons.Default.CheckCircle,
        contentDescription = "Objects synced",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = "Objects synced",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      CircularProgressIndicator(
        modifier = Modifier.size(14.dp),
        strokeWidth = 2.dp
      )
      Text(
        text = "Objects syncing...",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
