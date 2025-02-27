/*
 * Copyright 2022 Google LLC
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

package com.googletest.firebase.appdistribution.testapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import java.util.*

class ScreenshotDetectionFeedbackTrigger(
  private val activity: Activity,
  private val additionalFormText: Int,
  handler: Handler
) : ContentObserver(handler) {
  private val seenImages = HashSet<Uri>()

  override fun onChange(selfChange: Boolean, uri: Uri?) {
    if (uri == null || !isExternalContent(uri) || seenImages.contains(uri)) {
      return
    }
    maybeStartFeedbackForScreenshot(uri)
  }

  private fun maybeStartFeedbackForScreenshot(uri: Uri) {
    if (!isPermissionGranted()) {
      Log.i(TAG, "Screenshot detection disabled because storage permission is denied")
    }
    try {
      val cursor = activity.contentResolver.query(uri, contentProjection, null, null, null)
      cursor?.use {
        if (cursor.moveToFirst()) {
          if (shouldCheckIfPending && isPending(cursor)) {
            Log.i(TAG, "Ignoring pending image: $uri")
            return
          }
          seenImages.add(uri)
          val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
          Log.i(TAG, "Path: $path")
          if (path.lowercase(Locale.getDefault()).contains("screenshot")) {
            Firebase.appDistribution.startFeedback(additionalFormText, uri)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Could not determine if media change was due to taking a screenshot", e)
    }
  }

  fun listenForScreenshots() {
    activity.contentResolver?.registerContentObserver(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      /* notifyForDescendants= */ true,
      this
    )
  }

  fun stopListeningForScreenshots() {
    activity.contentResolver?.unregisterContentObserver(this)
  }

  private fun isPermissionGranted() =
    ContextCompat.checkSelfPermission(activity, screenshotReadPermission) ==
      PackageManager.PERMISSION_GRANTED

  companion object {
    private const val TAG = "ScreenshotDetectionFeedbackTrigger"

    val screenshotReadPermission =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
      else Manifest.permission.READ_EXTERNAL_STORAGE

    private val shouldCheckIfPending = Build.VERSION.SDK_INT >= 29
    private val contentProjection =
      if (shouldCheckIfPending)
        arrayOf(MediaStore.Images.Media.DATA, MediaStore.MediaColumns.IS_PENDING)
      else arrayOf(MediaStore.Images.Media.DATA)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isPending(cursor: Cursor): Boolean {
      val pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
      return cursor.getInt(pendingColumn) == 1
    }

    private fun isExternalContent(uri: Uri) =
      uri.toString().matches("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/\\d+".toRegex())
  }
}
