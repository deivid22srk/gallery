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
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.customtasks.remotecontrol.RemoteControlAccessibilityService.Companion.captureScreenshot
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The AI engine that manages interactions between the user's prompt and the multimodal model.
 */
@Singleton
class RemoteControlAIEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var currentModel: Model? = null
  private val tools = RemoteControlTools()

  private val _status = MutableStateFlow<String>("Idle")
  val status: StateFlow<String> = _status

  private val _isProcessing = MutableStateFlow(false)
  val isProcessing: StateFlow<Boolean> = _isProcessing

  private val history = mutableListOf<Message>()
  private val jniMutex = Mutex()

  private val systemPrompt = """
    You are an Android AI assistant with full device control.
    Your goal is to help users by performing actions on their behalf based on the visual context.

    You have access to the following tools:
    1. click(x, y): Taps on a point. Coordinates are 0-100 normalized (percentage of screen).
    2. swipe(x1, y1, x2, y2): Swipes from start to end points.
    3. scroll(direction): Swipes up, down, left, or right to scroll.
    4. back(): Performs the back system action.
    5. home(): Performs the home system action.
    6. wait(): Waits for the screen to update.

    When a user gives a prompt, examine the screenshot and decide the next best action.
    Always provide a short reasoning followed by a TOOL CALL in the format:
    [REASONING] I need to open the app... [TOOL] click(15, 20)

    If the task is finished, say [DONE].
  """.trimIndent()

  /** Sets the model to be used for inference. */
  fun setModel(model: Model) {
    if (currentModel?.name == model.name && model.instance != null) return

    currentModel = model
    _status.value = "Model set: ${model.name}"

    // Model initialization is now handled by RemoteControlTask or on-demand
    if (model.instance == null) {
        LlmChatModelHelper.initialize(
            context = context,
            model = model,
            supportImage = true,
            supportAudio = false,
            onDone = { error ->
                if (error.isEmpty()) {
                    _status.value = "Model Loaded"
                } else {
                    _status.value = "Error: $error"
                }
            },
            systemInstruction = Contents.of(systemPrompt)
        )
    }
  }

  /**
   * Starts a task for the AI engine to perform.
   */
  fun startTask(userPrompt: String) {
    scope.launch {
      val model = currentModel
      if (model == null || model.instance == null) {
        _status.value = "Error: Model not loaded"
        return@launch
      }

      if (_isProcessing.value) return@launch
      _isProcessing.value = true
      _status.value = "Analyzing..."

      try {
        // Reset conversation to clear history for a new task
        LlmChatModelHelper.resetConversation(
            model = model,
            supportImage = true,
            supportAudio = false,
            systemInstruction = Contents.of(systemPrompt)
        )

        var step = 0
        var lastResponse = ""

        while (step < 10 && !lastResponse.contains("[DONE]")) {
          val screenshot = captureScreenshot()
          if (screenshot == null) {
            _status.value = "Error: Screenshot failed"
            break
          }

          val response = processStep(model, if (step == 0) userPrompt else "Next step?", screenshot)
          lastResponse = response

          _status.value = "AI: $response"
          executeTool(response)

          step++
          withContext(Dispatchers.IO) { Thread.sleep(1200) } // Wait for UI update
        }

        if (lastResponse.contains("[DONE]")) {
          _status.value = "Task completed!"
        } else if (step >= 10) {
          _status.value = "Step limit reached"
        }

      } catch (e: Exception) {
        Log.e("RemoteControlAIEngine", "Error in task", e)
        _status.value = "Error: ${e.localizedMessage}"
      } finally {
        _isProcessing.value = false
      }
    }
  }

  /**
   * Processes a single step using the LLM.
   */
  private suspend fun processStep(model: Model, prompt: String, screenshot: Bitmap): String {
    val deferred = CompletableDeferred<String>()

    val contents = mutableListOf<Content>()
    contents.add(Content.ImageBytes(screenshot.toPngByteArray()))
    contents.add(Content.Text(prompt))

    val instance = model.instance as LlmModelInstance

    jniMutex.withLock {
        instance.conversation.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                private val fullResponse = StringBuilder()
                override fun onMessage(message: Message) {
                    fullResponse.append(message.toString())
                }
                override fun onDone() {
                    deferred.complete(fullResponse.toString())
                }
                override fun onError(throwable: Throwable) {
                    deferred.completeExceptionally(throwable)
                }
            }
        )
    }

    return deferred.await()
  }

  /**
   * Parses and executes tool calls from the AI response.
   */
  private fun executeTool(response: String) {
    val toolRegex = "\\[TOOL\\]\\s*(\\w+)\\(([^)]*)\\)".toRegex()
    val match = toolRegex.find(response) ?: return

    val toolName = match.groups[1]?.value ?: return
    val args = match.groups[2]?.value?.split(",")?.map { it.trim() } ?: emptyList()

    when (toolName) {
      "click" -> {
        val x = args.getOrNull(0)?.toFloatOrNull() ?: return
        val y = args.getOrNull(1)?.toFloatOrNull() ?: return
        tools.click(x, y)
      }
      "swipe" -> {
        val x1 = args.getOrNull(0)?.toFloatOrNull() ?: return
        val y1 = args.getOrNull(1)?.toFloatOrNull() ?: return
        val x2 = args.getOrNull(2)?.toFloatOrNull() ?: return
        val y2 = args.getOrNull(3)?.toFloatOrNull() ?: return
        tools.swipe(x1, y1, x2, y2, 500)
      }
      "scroll" -> {
        val direction = args.getOrNull(0) ?: "down"
        tools.scroll(direction)
      }
      "back" -> tools.goBack()
      "home" -> tools.goHome()
    }
  }

  private fun Bitmap.toPngByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 80, stream) // Slightly higher compression for faster JNI
    return stream.toByteArray()
  }
}
