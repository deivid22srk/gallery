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
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * A service that displays a draggable floating overlay button and prompt input.
 */
class FloatingControlService : Service() {

  private lateinit var windowManager: WindowManager
  private var floatingView: View? = null
  private var isExpanded = false

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    showFloatingButton()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun showFloatingButton() {
    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
      )

    params.gravity = Gravity.TOP or Gravity.START
    params.x = 100
    params.y = 100

    val layout = LinearLayout(this)
    layout.orientation = LinearLayout.VERTICAL
    layout.setBackgroundColor(0xBB000000.toInt())
    layout.setPadding(8, 8, 8, 8)

    val button = Button(this)
    button.text = "AI"
    layout.addView(button)

    val promptLayout = LinearLayout(this)
    promptLayout.orientation = LinearLayout.HORIZONTAL
    promptLayout.visibility = View.GONE

    val editText = EditText(this)
    editText.hint = "Enter prompt..."
    editText.layoutParams =
      LinearLayout.LayoutParams(300, LinearLayout.LayoutParams.WRAP_CONTENT)
    promptLayout.addView(editText)

    val sendButton = Button(this)
    sendButton.text = "Send"
    promptLayout.addView(sendButton)

    layout.addView(promptLayout)

    // Make it draggable
    button.setOnTouchListener(object : View.OnTouchListener {
      private var initialX: Int = 0
      private var initialY: Int = 0
      private var initialTouchX: Float = 0f
      private var initialTouchY: Float = 0f

      override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
          MotionEvent.ACTION_DOWN -> {
            initialX = params.x
            initialY = params.y
            initialTouchX = event.rawX
            initialTouchY = event.rawY
            return true
          }
          MotionEvent.ACTION_MOVE -> {
            params.x = initialX + (event.rawX - initialTouchX).toInt()
            params.y = initialY + (event.rawY - initialTouchY).toInt()
            windowManager.updateViewLayout(layout, params)
            return true
          }
          MotionEvent.ACTION_UP -> {
            if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
              v.performClick()
            }
            return true
          }
        }
        return false
      }
    })

    button.setOnClickListener {
      isExpanded = !isExpanded
      if (isExpanded) {
        promptLayout.visibility = View.VISIBLE
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
      } else {
        promptLayout.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      }
      windowManager.updateViewLayout(layout, params)
    }

    sendButton.setOnClickListener {
      val prompt = editText.text.toString()
      if (prompt.isNotEmpty()) {
        android.util.Log.d("AGRCFloating", "Sending prompt to AI engine: $prompt")
        RemoteControlAIEngine.processPrompt(prompt)
        editText.setText("")

        // Auto-collapse after sending
        button.performClick()
      }
    }

    floatingView = layout
    windowManager.addView(layout, params)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    if (floatingView != null) {
      windowManager.removeView(floatingView)
    }
  }
}
