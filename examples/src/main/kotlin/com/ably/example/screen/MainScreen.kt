package com.ably.example.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.ably.pubsub.realtime.RealtimeClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(realtimeClient: RealtimeClient) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isSandbox = realtimeClient.options.environment == "sandbox"

    val tabs = listOf(
        TabItem("Color Voting", Icons.Default.Favorite),
        TabItem("Task Management", Icons.AutoMirrored.Filled.List),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("Ably Live Objects Demo ${if (isSandbox) "(sandbox)" else ""}")
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        )

        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.title) },
                    icon = { Icon(tab.icon, contentDescription = tab.title) }
                )
            }
        }

        when (selectedTab) {
            0 -> ColorVotingScreen(realtimeClient)
            1 -> TaskManagementScreen(realtimeClient)
        }
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector,
)
