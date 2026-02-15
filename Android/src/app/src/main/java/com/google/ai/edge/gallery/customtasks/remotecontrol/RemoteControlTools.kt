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

import android.content.res.Resources
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam

/**
 * Tools that the AI can use to control the device screen.
 * Coordinates are normalized from 0 to 100 for device independence.
 */
class RemoteControlTools {

  private fun denormalizeX(x: Float): Float {
    return (x / 100f) * Resources.getSystem().displayMetrics.widthPixels
  }

  private fun denormalizeY(y: Float): Float {
    return (y / 100f) * Resources.getSystem().displayMetrics.heightPixels
  }

  @Tool(description = "Clicks on the screen. Coordinates (x, y) are 0-100 (percentage of screen size).")
  fun click(
    @ToolParam(description = "The x-coordinate (0-100).") x: Float,
    @ToolParam(description = "The y-coordinate (0-100).") y: Float,
  ): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }

    val realX = denormalizeX(x)
    val realY = denormalizeY(y)

    // Visualize first
    RemoteControlOverlayService.showClick(realX, realY)

    service.click(realX, realY)
    return mapOf("result" to "success", "action" to "click", "x" to x.toString(), "y" to y.toString())
  }

  @Tool(description = "Swipes on the screen. Coordinates are 0-100 (percentage of screen size).")
  fun swipe(
    @ToolParam(description = "The start x-coordinate (0-100).") x1: Float,
    @ToolParam(description = "The start y-coordinate (0-100).") y1: Float,
    @ToolParam(description = "The end x-coordinate (0-100).") x2: Float,
    @ToolParam(description = "The end y-coordinate (0-100).") y2: Float,
    @ToolParam(description = "The duration of the swipe in milliseconds.") duration: Int,
  ): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }

    val rx1 = denormalizeX(x1)
    val ry1 = denormalizeY(y1)
    val rx2 = denormalizeX(x2)
    val ry2 = denormalizeY(y2)

    // Visualize first
    RemoteControlOverlayService.showSwipe(rx1, ry1, rx2, ry2)

    service.swipe(rx1, ry1, rx2, ry2, duration.toLong())
    return mapOf("result" to "success", "action" to "swipe")
  }

  @Tool(description = "Performs the system back action.")
  fun goBack(): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }
    service.performBack()
    return mapOf("result" to "success", "action" to "back")
  }

  @Tool(description = "Performs the system home action.")
  fun goHome(): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }
    service.performHome()
    return mapOf("result" to "success", "action" to "home")
  }

  @Tool(description = "Scrolls the screen in the specified direction.")
  fun scroll(
    @ToolParam(description = "The direction to scroll. Can be 'up', 'down', 'left', 'right'.")
    direction: String
  ): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }

    val width = Resources.getSystem().displayMetrics.widthPixels.toFloat()
    val height = Resources.getSystem().displayMetrics.heightPixels.toFloat()

    // For simplicity, we implement scroll as a swipe in the opposite direction.
    when (direction.lowercase()) {
      "up" -> {
        val x = width / 2
        val y1 = height * 0.8f
        val y2 = height * 0.2f
        RemoteControlOverlayService.showSwipe(x, y1, x, y2)
        service.swipe(x, y1, x, y2)
      }
      "down" -> {
        val x = width / 2
        val y1 = height * 0.2f
        val y2 = height * 0.8f
        RemoteControlOverlayService.showSwipe(x, y1, x, y2)
        service.swipe(x, y1, x, y2)
      }
      "left" -> {
        val y = height / 2
        val x1 = width * 0.8f
        val x2 = width * 0.2f
        RemoteControlOverlayService.showSwipe(x1, y, x2, y)
        service.swipe(x1, y, x2, y)
      }
      "right" -> {
        val y = height / 2
        val x1 = width * 0.2f
        val x2 = width * 0.8f
        RemoteControlOverlayService.showSwipe(x1, y, x2, y)
        service.swipe(x1, y, x2, y)
      }
    }
    return mapOf("result" to "success", "action" to "scroll", "direction" to direction)
  }
}
