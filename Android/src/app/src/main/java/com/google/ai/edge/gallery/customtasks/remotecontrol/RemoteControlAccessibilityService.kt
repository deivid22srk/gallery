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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * An AccessibilityService that allows the AI to perform gestures on the screen.
 */
class RemoteControlAccessibilityService : AccessibilityService() {

  companion object {
    private const val TAG = "AGRCAccService"
    var instance: RemoteControlAccessibilityService? = null
      private set
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    Log.d(TAG, "Service Connected")
    instance = this
  }

  override fun onUnbind(intent: android.content.Intent?): Boolean {
    Log.d(TAG, "Service Unbound")
    instance = null
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    super.onDestroy()
    instance = null
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // No-op.
  }

  override fun onInterrupt() {
    // No-op.
  }

  /** Performs a click at the specified coordinates. */
  fun click(x: Float, y: Float) {
    val path = Path()
    path.moveTo(x, y)
    val gesture =
      GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        .build()
    dispatchGesture(gesture, null, null)
  }

  /** Performs a swipe from (x1, y1) to (x2, y2) over the specified duration. */
  fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 500) {
    val path = Path()
    path.moveTo(x1, y1)
    path.lineTo(x2, y2)
    val gesture =
      GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        .build()
    dispatchGesture(gesture, null, null)
  }

  /** Performs the system back action. */
  fun performBack() {
    performGlobalAction(GLOBAL_ACTION_BACK)
  }

  /** Performs the system home action. */
  fun performHome() {
    performGlobalAction(GLOBAL_ACTION_HOME)
  }
}
