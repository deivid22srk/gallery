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

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam

/**
 * Tools that the AI can use to control the device screen.
 */
class RemoteControlTools {

  @Tool(description = "Clicks on the screen at the specified coordinates (x, y). Coordinates are in pixels.")
  fun click(
    @ToolParam(description = "The x-coordinate in pixels.") x: Float,
    @ToolParam(description = "The y-coordinate in pixels.") y: Float,
  ): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }

    // Visualize first
    RemoteControlOverlayService.showClick(x, y)

    service.click(x, y)
    return mapOf("result" to "success", "action" to "click", "x" to x.toString(), "y" to y.toString())
  }

  @Tool(description = "Swipes on the screen from (x1, y1) to (x2, y2).")
  fun swipe(
    @ToolParam(description = "The start x-coordinate.") x1: Float,
    @ToolParam(description = "The start y-coordinate.") y1: Float,
    @ToolParam(description = "The end x-coordinate.") x2: Float,
    @ToolParam(description = "The end y-coordinate.") y2: Float,
    @ToolParam(description = "The duration of the swipe in milliseconds.") duration: Int,
  ): Map<String, String> {
    val service = RemoteControlAccessibilityService.instance
    if (service == null) {
      return mapOf("result" to "error", "message" to "Accessibility Service not connected")
    }

    // Visualize first
    RemoteControlOverlayService.showSwipe(x1, y1, x2, y2)

    service.swipe(x1, y1, x2, y2, duration.toLong())
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

    // For simplicity, we implement scroll as a swipe in the opposite direction.
    // Coordinates are rough estimates for visualization.
    when (direction.lowercase()) {
      "up" -> {
        RemoteControlOverlayService.showSwipe(500f, 800f, 500f, 200f)
        service.swipe(500f, 800f, 500f, 200f)
      }
      "down" -> {
        RemoteControlOverlayService.showSwipe(500f, 200f, 500f, 800f)
        service.swipe(500f, 200f, 500f, 800f)
      }
      "left" -> {
        RemoteControlOverlayService.showSwipe(800f, 500f, 200f, 500f)
        service.swipe(800f, 500f, 200f, 500f)
      }
      "right" -> {
        RemoteControlOverlayService.showSwipe(200f, 500f, 800f, 500f)
        service.swipe(200f, 500f, 800f, 500f)
      }
    }
    return mapOf("result" to "success", "action" to "scroll", "direction" to direction)
  }
}
