/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.customtasks.remotecontrol

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlFloatingUI(
    aiEngine: RemoteControlAIEngine,
    onDrag: (Float, Float) -> Unit,
    onFocusRequest: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var userPrompt by remember { mutableStateOf("") }
    val status by aiEngine.status.collectAsState()
    val isProcessing by aiEngine.isProcessing.collectAsState()

    val scale by animateFloatAsState(if (isProcessing) 1.1f else 1.0f)

    Surface(
        modifier = Modifier
            .padding(8.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        shape = if (expanded) RoundedCornerShape(24.dp) else CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!expanded) {
                IconButton(
                    onClick = {
                        expanded = true
                        onFocusRequest(true)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isProcessing) Icons.Default.Send else Icons.Default.PlayArrow,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "AI Remote Control",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    IconButton(onClick = {
                        expanded = false
                        onFocusRequest(false)
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                TextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = { Text("What should I do?") },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(8.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (userPrompt.isNotBlank()) {
                            aiEngine.startTask(userPrompt)
                            onFocusRequest(false)
                        }
                    })
                )

                Button(
                    onClick = {
                        if (userPrompt.isNotBlank()) {
                            aiEngine.startTask(userPrompt)
                            onFocusRequest(false)
                        }
                    },
                    enabled = !isProcessing && userPrompt.isNotBlank(),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Start Task")
                }
            }

            AnimatedVisibility(visible = isProcessing || status != "Idle") {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(4.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
