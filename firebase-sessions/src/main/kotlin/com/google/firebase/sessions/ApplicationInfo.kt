/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import com.google.firebase.FirebaseApp
import com.google.firebase.encoders.json.NumberedEnum

/** Enum denoting different development environments. */
internal enum class LogEnvironment(override val number: Int) : NumberedEnum {
  /** Unknown environment. */
  LOG_ENVIRONMENT_UNKNOWN(0),

  /** Autopush environment. */
  LOG_ENVIRONMENT_AUTOPUSH(1),

  /** Staging environment. */
  LOG_ENVIRONMENT_STAGING(2),

  /** Production environment. */
  LOG_ENVIRONMENT_PROD(3),
}

internal data class AndroidApplicationInfo(
  /** The package name of the application/bundle name. */
  val packageName: String,

  /** The version of the application. */
  val versionName: String,
)

internal data class ApplicationInfo(
  /** The Firebase Application ID of the application. */
  val appId: String,

  /** The model of the device that runs the application. */
  val deviceModel: String,

  /** The SDK version of the sessions library. */
  val sessionSdkVersion: String,

  /** The logging environment for the events. */
  val logEnvironment: LogEnvironment,

  /** The android application information for the app. */
  val androidAppInfo: AndroidApplicationInfo,
)

internal fun getApplicationInfo(firebaseApp: FirebaseApp): ApplicationInfo {
  val packageName = firebaseApp.applicationContext.packageName
  val packageInfo = firebaseApp.applicationContext.packageManager.getPackageInfo(packageName, 0)

  return ApplicationInfo(
    appId = firebaseApp.options.applicationId,
    deviceModel = "",
    sessionSdkVersion = BuildConfig.VERSION_NAME,
    logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
    androidAppInfo =
      AndroidApplicationInfo(packageName = packageName, versionName = packageInfo.versionName)
  )
}
