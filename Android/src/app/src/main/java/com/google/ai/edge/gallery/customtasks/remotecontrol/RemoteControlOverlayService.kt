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

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A service that displays a Material You floating overlay for AI interaction
 * and visualizes AI gestures.
 */
class RemoteControlOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

  companion object {
    private const val TAG = "AGRCOverlayService"
    private var instance: RemoteControlOverlayService? = null

    fun setVisible(visible: Boolean) {
      instance?.updateVisibility(visible)
    }

    fun showClick(x: Float, y: Float) {
      instance?.visualizeClick(x, y)
    }

    fun showSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
      instance?.visualizeSwipe(x1, y1, x2, y2)
    }
  }

  private lateinit var windowManager: WindowManager
  private var floatingComposeView: ComposeView? = null
  private var gestureView: GestureVisualizationView? = null
  private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

  // Lifecycle members
  private val lifecycleRegistry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle get() = lifecycleRegistry
  private val _viewModelStore = ViewModelStore()
  override val viewModelStore: ViewModelStore get() = _viewModelStore
  private val savedStateRegistryController = SavedStateRegistryController.create(this)
  override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

  override fun onCreate() {
    super.onCreate()
    instance = this
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

    lifecycleRegistry.currentState = Lifecycle.State.CREATED
    savedStateRegistryController.performRestore(null)

    showFloatingButton()
    showGestureOverlay()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    lifecycleRegistry.currentState = Lifecycle.State.STARTED
    return super.onStartCommand(intent, flags, startId)
  }

  private fun updateVisibility(visible: Boolean) {
    serviceScope.launch {
      floatingComposeView?.visibility = if (visible) View.VISIBLE else View.GONE
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @SuppressLint("ClickableViewAccessibility")
  private fun showFloatingButton() {
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 100
      y = 200
    }

    floatingComposeView = ComposeView(this).apply {
      setViewTreeLifecycleOwner(this@RemoteControlOverlayService)
      setViewTreeSavedStateRegistryOwner(this@RemoteControlOverlayService)
      setViewTreeViewModelStoreOwner(this@RemoteControlOverlayService)

      setContent {
        GalleryTheme {
          FloatingUI(
            onToggleFocus = { focused ->
              if (focused) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
              } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              }
              try {
                windowManager.updateViewLayout(this@apply, params)
              } catch (e: Exception) {
                Log.e(TAG, "Failed to update layout", e)
              }
            },
            onDrag = { dx, dy ->
              params.x += dx.toInt()
              params.y += dy.toInt()
              try {
                windowManager.updateViewLayout(this@apply, params)
              } catch (e: Exception) {
                Log.e(TAG, "Failed to update layout during drag", e)
              }
            }
          )
        }
      }
    }

    windowManager.addView(floatingComposeView, params)
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  private fun FloatingUI(onToggleFocus: (Boolean) -> Unit, onDrag: (Float, Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val response by RemoteControlAIEngine.response.collectAsState()
    val processing by RemoteControlAIEngine.processing.collectAsState()
    var promptText by remember { mutableStateOf("") }

    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier.widthIn(max = 300.dp)
    ) {
      Column(modifier = Modifier.padding(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          // Draggable AI Icon
          Surface(
            shape = CircleShape,
            color = if (processing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .setOnDragListener(onDrag) {
                expanded = !expanded
                onToggleFocus(expanded)
              }
          ) {
            Icon(
              Icons.Default.SmartToy,
              contentDescription = "AI",
              modifier = Modifier.padding(12.dp),
              tint = if (processing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            )
          }

          if (expanded) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (processing) "Thinking..." else "AI Remote",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        if (expanded) {
          Spacer(modifier = Modifier.height(12.dp))

          // Response area
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = response.ifEmpty { "Waiting for command..." },
              style = MaterialTheme.typography.bodyMedium,
              color = if (response.startsWith("Error") || response.startsWith("Fatal"))
                        MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
              fontSize = 13.sp
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Input area
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = promptText,
              onValueChange = { promptText = it },
              placeholder = { Text("Ask AI...", fontSize = 14.sp) },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(16.dp),
              singleLine = true,
              enabled = !processing
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = {
                if (promptText.isNotBlank()) {
                  RemoteControlAIEngine.processPrompt(promptText)
                  promptText = ""
                }
              },
              enabled = !processing && promptText.isNotBlank(),
              modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .size(40.dp)
            ) {
              Icon(
                Icons.AutoMirrored.Default.Send,
                contentDescription = "Send",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
        }
      }
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun Modifier.setOnDragListener(onDrag: (Float, Float) -> Unit, onClick: () -> Unit): Modifier {
    var initialX = 0f
    var initialY = 0f
    var isDragging = false

    return this.pointerInteropFilter { event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = event.rawX
          initialY = event.rawY
          isDragging = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = event.rawX - initialX
          val dy = event.rawY - initialY
          if (abs(dx) > 10 || abs(dy) > 10) {
            onDrag(dx, dy)
            initialX = event.rawX
            initialY = event.rawY
            isDragging = true
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (!isDragging) onClick()
          true
        }
        else -> false
      }
    }
  }

  private fun showGestureOverlay() {
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    )

    gestureView = GestureVisualizationView(this)
    windowManager.addView(gestureView, params)
  }

  private fun visualizeClick(x: Float, y: Float) {
    serviceScope.launch {
      gestureView?.addClick(x, y)
    }
  }

  private fun visualizeSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
    serviceScope.launch {
      gestureView?.addSwipe(x1, y1, x2, y2)
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    instance = null
    serviceScope.cancel()
    if (floatingComposeView != null) windowManager.removeView(floatingComposeView)
    if (gestureView != null) windowManager.removeView(gestureView)
  }

  /** View to draw temporary gesture visualizations. */
  private class GestureVisualizationView(context: Context) : View(context) {
    private val paint = Paint().apply {
      color = Color.parseColor("#8000FBFF") // Semi-transparent cyan
      style = Paint.Style.STROKE
      strokeWidth = 8f
      isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
      color = Color.parseColor("#3000FBFF") // More transparent cyan
      style = Paint.Style.FILL
      isAntiAlias = true
    }

    private var clickPoint: Pair<Float, Float>? = null
    private var swipeLine: Pair<Pair<Float, Float>, Pair<Float, Float>>? = null

    fun addClick(x: Float, y: Float) {
      clickPoint = x to y
      invalidate()
      postDelayed({
        clickPoint = null
        invalidate()
      }, 1000)
    }

    fun addSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
      swipeLine = (x1 to y1) to (x2 to y2)
      invalidate()
      postDelayed({
        swipeLine = null
        invalidate()
      }, 1500)
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      clickPoint?.let { (x, y) ->
        canvas.drawCircle(x, y, 50f, fillPaint)
        canvas.drawCircle(x, y, 50f, paint)
      }
      swipeLine?.let { (start, end) ->
        canvas.drawLine(start.first, start.second, end.first, end.second, paint)
        canvas.drawCircle(start.first, start.second, 20f, fillPaint)
        canvas.drawCircle(end.first, end.second, 30f, paint)
      }
    }
  }
}
