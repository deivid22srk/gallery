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

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * A shared engine to handle AI inference for remote control, independent of UI lifecycle.
 */
object RemoteControlAIEngine {
  private const val TAG = "AGRCAIEngine"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var currentModel: Model? = null
  private val _processing = MutableStateFlow(false)
  val processing = _processing.asStateFlow()

  private val _response = MutableStateFlow("")
  val response = _response.asStateFlow()

  /** Initializes the engine with the selected model. */
  fun initialize(model: Model) {
    currentModel = model
  }

  /** Processes the user prompt by sending it to the AI along with a screenshot. */
  fun processPrompt(prompt: String) {
    val model = currentModel ?: return
    val instance = model.instance as? LlmModelInstance ?: return

    scope.launch {
      Log.d(TAG, "Processing prompt: $prompt")
      _processing.value = true

      val contents = mutableListOf<Content>()

      // Get the latest screenshot.
      ScreenCaptureService.lastScreenshot?.let { contents.add(Content.ImageBytes(it.toJpegByteArray())) }

      if (prompt.trim().isNotEmpty()) {
        contents.add(Content.Text(prompt))
      }

      _response.value = "Thinking..."
      var fullResponse = ""

      instance.conversation
        .sendMessageAsync(Contents.of(contents))
        .catch {
          Log.e(TAG, "Inference failed", it)
          _response.value = "Error: ${it.message}"
        }
        .onCompletion { _processing.value = false }
        .collect {
          fullResponse += it.toString()
          _response.value = fullResponse
          Log.d(TAG, "Model response: $it")
        }
    }
  }

  private fun Bitmap.toJpegByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 80, stream)
    return stream.toByteArray()
  }
}
