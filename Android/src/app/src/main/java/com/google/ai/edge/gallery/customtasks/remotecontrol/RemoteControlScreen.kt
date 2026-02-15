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
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * The main screen for the AI Remote Control task.
 */
@Composable
fun RemoteControlScreen(
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  viewModel: RemoteControlViewModel = hiltViewModel(),
) {
  val context = LocalContext.current

  LaunchedEffect(model) { viewModel.setModel(model) }

  Column(modifier = Modifier.padding(16.dp)) {
    Text(stringResource(R.string.remote_control_title), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.remote_control_intro))
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      "Note: Accessibility and Overlay permissions are required to allow the local AI to \"see\" and control the screen. Your data stays on your device.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    Button(onClick = {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      context.startActivity(intent)
    }) {
      Text(stringResource(R.string.remote_control_enable_accessibility))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = {
      if (!Settings.canDrawOverlays(context)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        context.startActivity(intent)
      }
    }) {
      Text(stringResource(R.string.remote_control_grant_overlay))
    }

    Spacer(modifier = Modifier.height(8.dp))

    val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
    val modelInitialized = modelManagerUiState.isModelInitialized(model)
    if (!modelInitialized) {
      Text("Please wait for the model to initialize...", color = MaterialTheme.colorScheme.error)
      Spacer(modifier = Modifier.height(8.dp))
    }

    Button(
      onClick = {
        context.startService(Intent(context, RemoteControlOverlayService::class.java))
      },
      enabled = modelInitialized
    ) {
      Text(stringResource(R.string.remote_control_start))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = {
      context.stopService(Intent(context, RemoteControlOverlayService::class.java))
    }) {
      Text(stringResource(R.string.remote_control_stop))
    }
  }
}
