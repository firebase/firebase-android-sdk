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

package com.google.firebase.ai.type

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Helper class for streaming video from the camera.
 *
 * @see VideoHelper.build
 * @see LiveSession.startVideoConversation
 */
@PublicPreviewAPI
internal class VideoHelper(
  private val cameraManager: CameraManager,
) {
  private var cameraDevice: CameraDevice? = null
  private var imageReader: ImageReader? = null
  private var session: CameraCaptureSession? = null
  private val scope = CoroutineScope(Dispatchers.Default)

  private var released: Boolean = false

  /**
   * Release the system resources on the camera.
   *
   * Once a [VideoHelper] has been "released", it can _not_ be used again.
   *
   * This method can safely be called multiple times, as it won't do anything if this instance has
   * already been released.
   */
  fun release() {
    if (released) return
    released = true

    session?.close()
    imageReader?.close()
    cameraDevice?.close()
    scope.cancel()
  }

  /**
   * Start perpetually streaming the camera, and return the bytes read in a flow.
   *
   * Returns an empty flow if this [VideoHelper] has been [released][release].
   */
  @RequiresPermission(Manifest.permission.CAMERA)
  suspend fun start(cameraId: String): Flow<ByteArray> {
    if (released) return emptyFlow()

    cameraDevice = openCamera(cameraId)
    val cameraDevice = cameraDevice ?: return emptyFlow()

    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
    val streamConfigurationMap =
      characteristics.get(
        android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
      )
    val outputSizes = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)
    val size = outputSizes?.maxByOrNull { it.width * it.height } ?: return emptyFlow()

    imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
    val imageReader = imageReader ?: return emptyFlow()

    session = createCaptureSession(cameraDevice, imageReader)
    val session = session ?: return emptyFlow()

    val captureRequest =
      session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
        addTarget(imageReader.surface)
      }
    session.setRepeatingRequest(captureRequest.build(), null, null)

    return callbackFlow {
      val listener =
        ImageReader.OnImageAvailableListener { reader ->
          val image = reader.acquireLatestImage()
          if (image != null) {
            scope.launch {
              val buffer = image.planes[0].buffer
              val bytes = ByteArray(buffer.remaining())
              buffer.get(bytes)
              image.close()

              val scaledBytes = scaleAndCompressImage(bytes)
              trySend(scaledBytes)
            }
          }
        }
      imageReader.setOnImageAvailableListener(listener, null)

      awaitClose { imageReader.setOnImageAvailableListener(null, null) }
    }
  }

  private fun scaleAndCompressImage(bytes: ByteArray): ByteArray {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    val width = options.outWidth
    val height = options.outHeight
    val largestDimension = max(width, height)

    var inSampleSize = 1
    if (largestDimension > 2048) {
      val halfLargestDimension = largestDimension / 2
      while ((halfLargestDimension / inSampleSize) >= 2048) {
        inSampleSize *= 2
      }
    }

    options.inSampleSize = inSampleSize
    options.inJustDecodeBounds = false
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    val scaledWidth = bitmap.width
    val scaledHeight = bitmap.height
    val scaledLargestDimension = max(scaledWidth, scaledHeight)
    if (scaledLargestDimension > 2048) {
      val scaleFactor = 2048.0f / scaledLargestDimension
      val newWidth = (scaledWidth * scaleFactor).toInt()
      val newHeight = (scaledHeight * scaleFactor).toInt()
      bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    return outputStream.toByteArray()
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  private suspend fun openCamera(cameraId: String): CameraDevice =
    suspendCancellableCoroutine { cont ->
      val handler = Handler(Looper.getMainLooper())
      cameraManager.openCamera(
        cameraId,
        object : CameraDevice.StateCallback() {
          override fun onOpened(camera: CameraDevice) {
            cont.resume(camera)
          }

          override fun onDisconnected(camera: CameraDevice) {
            camera.close()
          }

          override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cont.resumeWithException(RuntimeException("Failed to open camera. Error: $error"))
          }
        },
        handler
      )
    }

  private suspend fun createCaptureSession(
    cameraDevice: CameraDevice,
    imageReader: ImageReader
  ): CameraCaptureSession = suspendCancellableCoroutine { cont ->
    cameraDevice.createCaptureSession(
      listOf(imageReader.surface),
      object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
          cont.resume(session)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
          cont.resumeWithException(RuntimeException("Failed to create capture session."))
        }
      },
      null
    )
  }

  companion object {
    private val TAG = VideoHelper::class.java.simpleName

    /**
     * Creates an instance of [VideoHelper] with the camera manager initialized.
     *
     * A separate build method is necessary so that we can properly propagate the required manifest
     * permission, and throw exceptions when needed.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun build(cameraManager: CameraManager): VideoHelper {
      return VideoHelper(cameraManager)
    }
  }
}
