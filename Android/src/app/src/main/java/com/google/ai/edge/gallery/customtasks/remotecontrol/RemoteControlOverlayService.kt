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
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A service that displays a draggable floating overlay button and visualizes AI gestures.
 * The overlay is invisible to screen recordings.
 */
class RemoteControlOverlayService : Service() {

  companion object {
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
  private var floatingView: View? = null
  private var gestureView: GestureVisualizationView? = null
  private var isExpanded = false
  private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

  override fun onCreate() {
    super.onCreate()
    instance = this
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    showFloatingButton()
    showGestureOverlay()
  }

  private fun updateVisibility(visible: Boolean) {
    floatingView?.visibility = if (visible) View.VISIBLE else View.GONE
    // We keep gesture overlay visible but empty most of the time.
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun showFloatingButton() {
    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT,
      )

    params.gravity = Gravity.TOP or Gravity.START
    params.x = 100
    params.y = 200

    val mainLayout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val shape = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 40f
        setColor(Color.parseColor("#CC000000"))
      }
      background = shape
      setPadding(16, 16, 16, 16)
    }

    val aiButton = Button(this).apply {
      text = "AI"
      setBackgroundColor(Color.parseColor("#4285F4"))
      setTextColor(Color.WHITE)
    }
    mainLayout.addView(aiButton)

    val expandedLayout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
    }

    val responseText = TextView(this).apply {
      setTextColor(Color.WHITE)
      textSize = 14f
      setPadding(0, 16, 0, 16)
      text = "Ready"
    }
    expandedLayout.addView(responseText)

    val inputLayout = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
    }

    val editText = EditText(this).apply {
      hint = "Ask AI..."
      setHintTextColor(Color.LTGRAY)
      setTextColor(Color.WHITE)
      layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    inputLayout.addView(editText)

    val sendButton = Button(this).apply {
      text = "Go"
    }
    inputLayout.addView(sendButton)

    expandedLayout.addView(inputLayout)
    mainLayout.addView(expandedLayout)

    // Make it draggable
    aiButton.setOnTouchListener(object : View.OnTouchListener {
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
            windowManager.updateViewLayout(mainLayout, params)
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

    aiButton.setOnClickListener {
      isExpanded = !isExpanded
      if (isExpanded) {
        expandedLayout.visibility = View.VISIBLE
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
      } else {
        expandedLayout.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      }
      windowManager.updateViewLayout(mainLayout, params)
    }

    sendButton.setOnClickListener {
      val prompt = editText.text.toString()
      if (prompt.isNotEmpty()) {
        RemoteControlAIEngine.processPrompt(prompt)
        editText.setText("")
      }
    }

    serviceScope.launch {
      RemoteControlAIEngine.response.collect { response ->
        responseText.text = response
      }
    }

    floatingView = mainLayout
    windowManager.addView(mainLayout, params)
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
    instance = null
    serviceScope.cancel()
    if (floatingView != null) windowManager.removeView(floatingView)
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
    private var swipeLine: Triple<Pair<Float, Float>, Pair<Float, Float>, Float>? = null

    fun addClick(x: Float, y: Float) {
      clickPoint = x to y
      invalidate()
      postDelayed({
        clickPoint = null
        invalidate()
      }, 1000)
    }

    fun addSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
      swipeLine = Triple(x1 to y1, x2 to y2, 0f)
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
      swipeLine?.let { (start, end, _) ->
        canvas.drawLine(start.first, start.second, end.first, end.second, paint)
        canvas.drawCircle(start.first, start.second, 20f, fillPaint)
        canvas.drawCircle(end.first, end.second, 30f, paint)
      }
    }
  }
}
