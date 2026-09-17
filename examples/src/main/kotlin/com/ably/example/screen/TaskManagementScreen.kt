package com.ably.example.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.example.getRealtimeChannel
import com.ably.example.observeMap
import com.ably.example.observeRootObject
import io.ably.pubsub.liveobjects.value.LiveMapValue
import io.ably.pubsub.realtime.RealtimeClient
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun TaskManagementScreen(realtimeClient: RealtimeClient) {
  var taskText by remember { mutableStateOf("") }
  var editingTaskId by remember { mutableStateOf<String?>(null) }
  var editingText by remember { mutableStateOf("") }

  val channel = getRealtimeChannel(realtimeClient, "objects-live-map")
  val root = observeRootObject(channel)

  val (taskIdToTask, liveTasks) = observeMap(root, "tasks")

  val taskEntries = remember(taskIdToTask) {
    taskIdToTask.entries.sortedBy { it.key }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Task Management",
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )

    ObjectsSyncStatusRow(channel, root)

    Card(
      modifier = Modifier.fillMaxWidth(),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
      shape = RoundedCornerShape(12.dp)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(
          value = taskText,
          onValueChange = { taskText = it },
          label = { Text("Enter new task") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            // Disabled until the tasks map is bound, so input is never cleared
            // without the task actually being enqueued
            enabled = liveTasks != null,
            onClick = {
              if (taskText.isNotBlank()) {
                val taskId = "${System.currentTimeMillis()}_${Uuid.random().toHexString()}"
                // Fire-and-forget: the map subscription refreshes the task list on ack
                liveTasks?.set(taskId, LiveMapValue.of(taskText.trim()))
                taskText = ""
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Task")
          }

          OutlinedButton(
            onClick = {
              taskIdToTask.forEach { task ->
                liveTasks?.remove(task.key)
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("Remove All")
          }
        }
      }
    }

    Card(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
      shape = RoundedCornerShape(12.dp)
    ) {
      Column(
        modifier = Modifier.padding(16.dp)
      ) {
        Text(
          text = "Tasks (${taskIdToTask.size})",
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(bottom = 12.dp)
        )

        if (taskIdToTask.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No tasks yet. Add one above!",
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(taskIdToTask.size) { index ->
              val task = taskEntries.elementAt(index)
              TaskItemCard(
                task = task,
                isEditing = editingTaskId == task.key,
                editingText = editingText,
                onEditingTextChange = { editingText = it },
                onEdit = {
                  editingTaskId = task.key
                  editingText = task.value
                },
                onSave = {
                  liveTasks?.set(task.key, LiveMapValue.of(editingText.trim()))
                  editingTaskId = null
                  editingText = ""
                },
                onCancel = {
                  editingTaskId = null
                  editingText = ""
                },
                onDelete = {
                  liveTasks?.remove(task.key)
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TaskItemCard(
  task: Map.Entry<String, String>,
  isEditing: Boolean,
  editingText: String,
  onEditingTextChange: (String) -> Unit,
  onEdit: () -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(all = 2.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    shape = RoundedCornerShape(8.dp)
  ) {
    if (isEditing) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = editingText,
          onValueChange = onEditingTextChange,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
          ) {
            Text("Save")
          }
          OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
          ) {
            Text("Cancel")
          }
        }
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = task.value,
          modifier = Modifier.weight(1f),
          fontSize = 16.sp
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IconButton(onClick = onEdit) {
            Icon(
              Icons.Default.Edit,
              contentDescription = "Edit",
              tint = MaterialTheme.colorScheme.primary
            )
          }
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Default.Delete,
              contentDescription = "Delete",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }
  }
}
