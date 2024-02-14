/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution

import androidx.annotation.Keep
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar

/** Returns the [FirebaseAppDistribution] instance of the default [FirebaseApp]. */
val Firebase.appDistribution: FirebaseAppDistribution
  get() = FirebaseAppDistribution.getInstance()

/**
 * Destructuring declaration for [AppDistributionRelease] to provide binaryType.
 *
 * @return the binaryType of the [AppDistributionRelease]
 */
operator fun AppDistributionRelease.component1() = binaryType

/**
 * Destructuring declaration for [AppDistributionRelease] to provide displayVersion.
 *
 * @return the displayVersion of the [AppDistributionRelease]
 */
operator fun AppDistributionRelease.component2() = displayVersion

/**
 * Destructuring declaration for [AppDistributionRelease] to provide versionCode.
 *
 * @return the versionCode of the [AppDistributionRelease]
 */
operator fun AppDistributionRelease.component3() = versionCode

/**
 * Destructuring declaration for [AppDistributionRelease] to provide releaseNotes.
 *
 * @return the releaseNotes of the [AppDistributionRelease]
 */
operator fun AppDistributionRelease.component4() = releaseNotes

/**
 * Destructuring declaration for [UpdateProgress] to provide apkBytesDownloaded.
 *
 * @return the apkBytesDownloaded of the [UpdateProgress]
 */
operator fun UpdateProgress.component1() = apkBytesDownloaded

/**
 * Destructuring declaration for [UpdateProgress] to provide apkFileTotalBytes.
 *
 * @return the apkFileTotalBytes of the [UpdateProgress]
 */
operator fun UpdateProgress.component2() = apkFileTotalBytes

/**
 * Destructuring declaration for [UpdateProgress] to provide updateStatus.
 *
 * @return the updateStatus of the [UpdateProgress]
 */
operator fun UpdateProgress.component3() = updateStatus

/** @suppress */
@Keep
class FirebaseAppDistributionKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
