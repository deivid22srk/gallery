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
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A Service that manages the floating overlay UI using Jetpack Compose.
 */
@AndroidEntryPoint
class RemoteControlOverlayService : android.app.Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

  @Inject lateinit var aiEngine: RemoteControlAIEngine

  private lateinit var windowManager: WindowManager
  private var overlayView: View? = null
  private var visualizationView: GestureVisualizationView? = null

  private val lifecycleRegistry = LifecycleRegistry(this)
  override val viewModelStore = ViewModelStore()
  private val savedStateRegistryController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle get() = lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

  companion object {
    private var instance: RemoteControlOverlayService? = null

    fun setVisible(visible: Boolean) {
      instance?.overlayView?.visibility = if (visible) View.VISIBLE else View.GONE
      instance?.visualizationView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showClick(x: Float, y: Float) {
      instance?.visualizationView?.showClick(x, y)
    }

    fun showSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
      instance?.visualizationView?.showSwipe(x1, y1, x2, y2)
    }
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    savedStateRegistryController.performRestore(null)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    setupOverlay()
    setupVisualizationOverlay()
  }

  override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    return START_NOT_STICKY
  }

  private fun setupOverlay() {
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 100
      y = 100
    }

    val composeView = ComposeView(this).apply {
      setContent {
        RemoteControlFloatingUI(
          aiEngine = aiEngine,
          onDrag = { dx, dy ->
            params.x += dx.toInt()
            params.y += dy.toInt()
            windowManager.updateViewLayout(this, params)
          },
          onFocusRequest = { focusable ->
            if (focusable) {
              params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
              params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager.updateViewLayout(this, params)
          }
        )
      }
    }

    // Initialize the ViewTree owners so Compose can work correctly.
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeViewModelStoreOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)

    overlayView = composeView
    windowManager.addView(overlayView, params)
  }

  private fun setupVisualizationOverlay() {
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    )
    visualizationView = GestureVisualizationView(this)
    windowManager.addView(visualizationView, params)
  }

  override fun onDestroy() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    overlayView?.let { windowManager.removeView(it) }
    visualizationView?.let { windowManager.removeView(it) }
    instance = null
    super.onDestroy()
  }

  override fun onBind(intent: android.content.Intent?) = null
}
