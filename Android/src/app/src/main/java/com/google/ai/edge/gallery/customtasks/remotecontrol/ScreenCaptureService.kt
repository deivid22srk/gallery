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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * A foreground service that uses MediaProjection to capture screen frames efficiently.
 */
class ScreenCaptureService : Service() {

  companion object {
    private const val TAG = "AGRCScreenCapture"
    private const val CHANNEL_ID = "screen_capture_channel"
    private const val NOTIFICATION_ID = 101
    private const val CAPTURE_INTERVAL_MS = 500L // Capture at 2 FPS to save battery/CPU.

    @Volatile
    var lastScreenshot: Bitmap? = null
      private set
  }

  private var projection: MediaProjection? = null
  private var imageReader: ImageReader? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var handlerThread: HandlerThread? = null
  private var handler: Handler? = null
  private var lastCaptureTime = 0L

  override fun onCreate() {
    super.onCreate()
    handlerThread = HandlerThread("ScreenCaptureThread")
    handlerThread?.start()
    handler = Handler(handlerThread!!.looper)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "Starting ScreenCaptureService")
    val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
    val resultData = intent?.getParcelableExtra<Intent>("resultData")

    if (resultCode != -1 && resultData != null) {
      startForeground(NOTIFICATION_ID, createNotification())

      val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
      projection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

      startCapture()
    } else {
      Log.e(TAG, "Invalid resultCode or resultData")
      stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun startCapture() {
    val metrics = resources.displayMetrics
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val density = metrics.densityDpi

    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    virtualDisplay =
      projection?.createVirtualDisplay(
        "ScreenCapture",
        width,
        height,
        density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader?.surface,
        null,
        handler,
      )

    imageReader?.setOnImageAvailableListener(
      { reader ->
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_INTERVAL_MS) {
          reader.acquireLatestImage()?.close()
          return@setOnImageAvailableListener
        }
        lastCaptureTime = now

        val image = reader.acquireLatestImage()
        if (image != null) {
          try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap =
              Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888,
              )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

            // Thread-safe update of the last screenshot.
            val oldBitmap = lastScreenshot
            lastScreenshot = croppedBitmap
            // We don't recycle oldBitmap immediately as it might be in use by the AI engine.
            // But we should be careful about memory.
            // In a more robust implementation, we'd use a pool or a double-buffer.

          } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
          } finally {
            image.close()
          }
        }
      },
      handler,
    )
  }

  private fun createNotification(): Notification {
    val channel =
      NotificationChannel(CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("AI Remote Control")
      .setContentText("Capturing screen for AI...")
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    Log.d(TAG, "Destroying ScreenCaptureService")
    virtualDisplay?.release()
    imageReader?.close()
    projection?.stop()
    handlerThread?.quitSafely()
    super.onDestroy()
  }
}
