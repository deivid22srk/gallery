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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * A custom task that allows an AI to control the device via accessibility gestures and screen capture.
 */
class RemoteControlTask @Inject constructor() : CustomTask {
  private val tools = listOf(RemoteControlTools())

  override val task =
    Task(
      id = BuiltInTaskId.LLM_REMOTE_CONTROL,
      label = "AI Remote Control",
      description = "Allow AI to control your phone via gestures and screen sharing.",
      category = Category.LLM,
      icon = Icons.Outlined.TouchApp,
      models = mutableListOf(),
      experimental = true,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = true,
      supportAudio = false,
      onDone = { error ->
        if (error.isEmpty()) {
          RemoteControlAIEngine.initialize(model)
        }
        onDone(error)
      },
      systemInstruction =
        Contents.of(
          "You are a focused AI assistant that controls an Android phone. " +
            "Execute user tasks by strictly using tool calls. " +
            "You will receive screenshots. Analyze them and use 'click', 'swipe', 'scroll', 'goBack', or 'goHome'. " +
            "IMPORTANT: Do not engage in casual conversation. Do not explain your actions. " +
            "Just call the necessary tools to achieve the goal. " +
            "If the task requires multiple steps, call one tool at a time."
        ),
      tools = tools,
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val customTaskData = data as CustomTaskData
    val modelManagerUiState by customTaskData.modelManagerViewModel.uiState.collectAsState()
    val model = modelManagerUiState.selectedModel
    RemoteControlScreen(model = model, modelManagerViewModel = customTaskData.modelManagerViewModel)
  }
}
