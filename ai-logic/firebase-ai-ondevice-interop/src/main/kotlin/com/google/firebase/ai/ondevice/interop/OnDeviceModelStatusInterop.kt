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

/** Represents the status of an on-device AI model. */
public class OnDeviceModelStatusInterop private constructor(private val value: String) {
  override fun toString(): String = value

  override fun equals(other: Any?): Boolean =
    other is OnDeviceModelStatusInterop && value == other.value

  override fun hashCode(): Int = value.hashCode()

  public companion object {
    /** The on-device model is unavailable on the device. */
    @JvmField
    public val UNAVAILABLE: OnDeviceModelStatusInterop = OnDeviceModelStatusInterop("UNAVAILABLE")

    /** The on-device model is available for download. */
    @JvmField
    public val DOWNLOADABLE: OnDeviceModelStatusInterop = OnDeviceModelStatusInterop("DOWNLOADABLE")

    /** The on-device model is currently being downloaded. */
    @JvmField
    public val DOWNLOADING: OnDeviceModelStatusInterop = OnDeviceModelStatusInterop("DOWNLOADING")

    /** The on-device model is available and ready for use. */
    @JvmField
    public val AVAILABLE: OnDeviceModelStatusInterop = OnDeviceModelStatusInterop("AVAILABLE")
  }
}
