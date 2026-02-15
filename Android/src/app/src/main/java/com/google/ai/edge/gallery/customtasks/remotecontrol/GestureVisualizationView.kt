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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * A transparent view that visualizes gestures performed by the AI.
 */
class GestureVisualizationView(context: Context) : View(context) {

  private val paint = Paint().apply {
    color = Color.CYAN
    strokeWidth = 10f
    style = Paint.Style.STROKE
    isAntiAlias = true
    alpha = 150
  }

  private val dotPaint = Paint().apply {
    color = Color.CYAN
    style = Paint.Style.FILL
    isAntiAlias = true
    alpha = 200
  }

  private var gesture: VisualGesture? = null

  sealed class VisualGesture {
    data class Click(val x: Float, val y: Float) : VisualGesture()
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : VisualGesture()
  }

  fun showClick(x: Float, y: Float) {
    gesture = VisualGesture.Click(x, y)
    invalidate()
    postDelayed({
      gesture = null
      invalidate()
    }, 500)
  }

  fun showSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
    gesture = VisualGesture.Swipe(x1, y1, x2, y2)
    invalidate()
    postDelayed({
      gesture = null
      invalidate()
    }, 1000)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    when (val g = gesture) {
      is VisualGesture.Click -> {
        canvas.drawCircle(g.x, g.y, 40f, paint)
        canvas.drawCircle(g.x, g.y, 10f, dotPaint)
      }
      is VisualGesture.Swipe -> {
        canvas.drawLine(g.x1, g.y1, g.x2, g.y2, paint)
        canvas.drawCircle(g.x1, g.y1, 20f, paint)
        canvas.drawCircle(g.x2, g.y2, 10f, dotPaint)
      }
      null -> {}
    }
  }
}
