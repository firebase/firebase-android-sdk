/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.ai.ondevice

import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceUnknownException
import com.google.firebase.ai.ondevice.interop.GenerationConfig
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ModelPreference as MlKitModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage as MlKitModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Entry point for Firebase AI On-Device functionality.
 *
 * *Note:* This class **does not** provides any inference-related functionality for the on-device AI
 * model.
 */
public object FirebaseAIOnDevice {
  /**
   * Checks the current status / availability of the on-device AI model.
   *
   * @param option The configuration option for the model.
   * @return An [OnDeviceModelStatus] object indicating the current state of the model.
   */
  public suspend fun checkStatus(option: OnDeviceModelOption): OnDeviceModelStatus {
    return OnDeviceModelStatus.fromFeatureStatus(
      Generation.getClient(option.toMlKit()).checkStatus()
    )
  }

  /**
   * Initiates the download of the on-device AI model.
   *
   * This method returns a [Flow] that emits [DownloadStatus] updates as the download progresses.
   * Consumers should collect the flow to start the download process, and optionally process any
   * updates on the download state, progress, and completion or failure.
   *
   * @param option The configuration option for the model.
   * @return A [Flow] of [DownloadStatus] objects representing the download lifecycle.
   */
  public fun download(option: OnDeviceModelOption): Flow<DownloadStatus> {
    return Generation.getClient(option.toMlKit()).download().map { DownloadStatus.fromMlKit(it) }
  }
}

/** Options for configuring the on-device AI model. */
public class OnDeviceModelOption private constructor(private val value: String) {
  override fun toString(): String = value

  public companion object {
    /** Selects the latest stable model. */
    @JvmField public val STABLE: OnDeviceModelOption = OnDeviceModelOption("stable")

    /** Selects the latest preview model with full performance. */
    @JvmField public val PREVIEW: OnDeviceModelOption = OnDeviceModelOption("preview")

    /** Selects the latest preview model optimized for speed. */
    @JvmField public val PREVIEW_FAST: OnDeviceModelOption = OnDeviceModelOption("preview_fast")
  }
}

internal fun OnDeviceModelOption.toMlKit(): com.google.mlkit.genai.prompt.GenerationConfig =
  generationConfig {
    modelConfig = modelConfig {
      when (this@toMlKit) {
        OnDeviceModelOption.STABLE -> {
          releaseStage = MlKitModelReleaseStage.STABLE
          preference = MlKitModelPreference.FULL
        }
        OnDeviceModelOption.PREVIEW -> {
          releaseStage = MlKitModelReleaseStage.PREVIEW
          preference = MlKitModelPreference.FULL
        }
        OnDeviceModelOption.PREVIEW_FAST -> {
          releaseStage = MlKitModelReleaseStage.PREVIEW
          preference = MlKitModelPreference.FAST
        }
        else -> throw IllegalArgumentException("Unknown option: ${this@toMlKit}")
      }
    }
  }

/** Represents the current status of the on-device AI model. */
public class OnDeviceModelStatus private constructor(private val status: Int) {
  public companion object {
    /** The on-device model is unavailable on the device. */
    @JvmField
    public val UNAVAILABLE: OnDeviceModelStatus = OnDeviceModelStatus(FeatureStatus.UNAVAILABLE)

    /** The on-device model is available for download. */
    @JvmField
    public val DOWNLOADABLE: OnDeviceModelStatus = OnDeviceModelStatus(FeatureStatus.DOWNLOADABLE)

    /** The on-device model is currently being downloaded. */
    @JvmField
    public val DOWNLOADING: OnDeviceModelStatus = OnDeviceModelStatus(FeatureStatus.DOWNLOADING)

    /** The on-device model is available and ready for use. */
    @JvmField
    public val AVAILABLE: OnDeviceModelStatus = OnDeviceModelStatus(FeatureStatus.AVAILABLE)

    internal fun fromFeatureStatus(featureStatus: Int): OnDeviceModelStatus =
      when (featureStatus) {
        FeatureStatus.UNAVAILABLE -> UNAVAILABLE
        FeatureStatus.DOWNLOADABLE -> DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> DOWNLOADING
        FeatureStatus.AVAILABLE -> AVAILABLE
        else -> UNAVAILABLE
      }
  }
}

/**
 * An abstract class representing the status of an on-device model download operation.
 *
 * This class has several concrete subclasses, each representing a specific stage or outcome of the
 * download process.
 */
public abstract class DownloadStatus {
  /**
   * Represents when a download has just started.
   *
   * @property bytesToDownload The total number of bytes expected to be downloaded.
   */
  public class DownloadStarted internal constructor(public val bytesToDownload: Long) :
    DownloadStatus()

  /**
   * Represents when a download is actively in progress.
   *
   * @property totalBytesDownloaded The total number of bytes downloaded so far.
   */
  public class DownloadInProgress internal constructor(public val totalBytesDownloaded: Long) :
    DownloadStatus()

  /**
   * Represents when a download has failed.
   *
   * @property exception The [FirebaseAIOnDeviceException] that describes the reason for the
   * download failure.
   */
  public class DownloadFailed
  internal constructor(public val exception: FirebaseAIOnDeviceException) : DownloadStatus()

  /** Represents when a download has successfully completed. */
  public class DownloadCompleted : DownloadStatus()

  internal companion object {
    internal fun fromMlKit(status: com.google.mlkit.genai.common.DownloadStatus) =
      when (status) {
        is com.google.mlkit.genai.common.DownloadStatus.DownloadStarted ->
          DownloadStarted(status.bytesToDownload)
        is com.google.mlkit.genai.common.DownloadStatus.DownloadProgress ->
          DownloadInProgress(status.totalBytesDownloaded)
        is com.google.mlkit.genai.common.DownloadStatus.DownloadCompleted -> DownloadCompleted()
        is com.google.mlkit.genai.common.DownloadStatus.DownloadFailed ->
          DownloadFailed(FirebaseAIOnDeviceException.from(status.e))
        else -> DownloadFailed(FirebaseAIOnDeviceUnknownException("Unknown download status"))
      }
  }
}
