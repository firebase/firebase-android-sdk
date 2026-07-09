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

package com.google.firebase.ai.ondevice.interop

/** Represents the status of a model download operation. */
public open class DownloadStatusInterop {
  /**
   * Indicates that the download has started.
   *
   * @property bytesToDownload The total number of bytes that need to be downloaded.
   */
  public class DownloadStarted(public val bytesToDownload: Long) : DownloadStatusInterop()

  /**
   * Indicates that the download is in progress.
   *
   * @property totalBytesDownloaded The total number of bytes downloaded so far.
   */
  public class DownloadInProgress(public val totalBytesDownloaded: Long) : DownloadStatusInterop()

  /**
   * Indicates that the download has failed.
   *
   * @property exception The exception that caused the download to fail.
   */
  public class DownloadFailed(public val exception: FirebaseAIOnDeviceException) :
    DownloadStatusInterop()

  /** Indicates that the download has completed successfully. */
  public class DownloadCompleted : DownloadStatusInterop()
}
