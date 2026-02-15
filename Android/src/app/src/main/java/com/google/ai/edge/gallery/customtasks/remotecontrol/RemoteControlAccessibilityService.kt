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
import android.graphics.Bitmap
import android.graphics.Path
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * An AccessibilityService that allows the AI to perform gestures and capture screenshots.
 */
class RemoteControlAccessibilityService : AccessibilityService() {

  companion object {
    private const val TAG = "AGRCAccService"
    var instance: RemoteControlAccessibilityService? = null
      private set

    /** Captures a fresh screenshot using Accessibility API, hiding the overlay temporarily. */
    suspend fun captureScreenshot(): CaptureResult {
      val service = instance ?: return CaptureResult.Error("Accessibility Service not enabled. Please enable it in Settings.")

      // Hide floating UI
      RemoteControlOverlayService.setVisible(false)
      delay(500) // Wait for UI to hide and system to refresh

      val result = service.takeAccessibilityScreenshot()

      // Show floating UI
      RemoteControlOverlayService.setVisible(true)

      return result
    }
  }

  sealed class CaptureResult {
    data class Success(val bitmap: Bitmap) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
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
    RemoteControlOverlayService.showClick(x, y)
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
    RemoteControlOverlayService.showSwipe(x1, y1, x2, y2)
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

  /** Uses the Accessibility API to take a screenshot without requiring MediaProjection. */
  private suspend fun takeAccessibilityScreenshot(): CaptureResult {
    val deferred = CompletableDeferred<CaptureResult>()

    try {
      takeScreenshot(
        Display.DEFAULT_DISPLAY,
        ContextCompat.getMainExecutor(this),
        object : TakeScreenshotCallback {
          override fun onSuccess(screenshot: ScreenshotResult) {
            val hardwareBuffer = screenshot.hardwareBuffer
            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
            hardwareBuffer.close()

            if (bitmap != null) {
              // Convert hardware bitmap to software bitmap for analysis and scale it down.
              val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
              val scale = 0.5f
              val scaled = Bitmap.createScaledBitmap(
                softwareBitmap,
                (softwareBitmap.width * scale).toInt(),
                (softwareBitmap.height * scale).toInt(),
                true
              )
              deferred.complete(CaptureResult.Success(scaled))
            } else {
              deferred.complete(CaptureResult.Error("Failed to process captured bitmap."))
            }
          }

          override fun onFailure(errorCode: Int) {
            val reason = when (errorCode) {
              AccessibilityService.ERROR_TAKESCREENSHOT_INTERNAL_ERROR -> "Internal error"
              AccessibilityService.ERROR_TAKESCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "No accessibility access"
              AccessibilityService.ERROR_TAKESCREENSHOT_INTERVAL_TOO_SHORT -> "Capture interval too short"
              AccessibilityService.ERROR_TAKESCREENSHOT_INVALID_DISPLAY -> "Invalid display"
              else -> "Unknown error ($errorCode)"
            }
            Log.e(TAG, "Screenshot failed: $reason")
            deferred.complete(CaptureResult.Error("Screenshot failed: $reason"))
          }
        }
      )
    } catch (e: Exception) {
      return CaptureResult.Error("Exception during capture: ${e.localizedMessage}")
    }

    return withTimeoutOrNull(5000) {
      deferred.await()
    } ?: CaptureResult.Error("Screenshot timed out.")
  }
}
