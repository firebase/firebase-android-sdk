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

import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.encoders.DataEncoder
import com.google.firebase.encoders.FieldDescriptor
import com.google.firebase.encoders.ObjectEncoderContext
import com.google.firebase.encoders.json.JsonDataEncoderBuilder
import com.google.firebase.sessions.settings.SessionsSettings

/** Contains functions for [SessionEvent]s. */
internal object SessionEvents {
  /** JSON [DataEncoder] for [SessionEvent]s. */
  // TODO(mrober): Replace with firebase-encoders-processor when it can encode Kotlin data classes.
  internal val SESSION_EVENT_ENCODER: DataEncoder =
    JsonDataEncoderBuilder()
      .configureWith {
        it.registerEncoder(SessionEvent::class.java) {
          sessionEvent: SessionEvent,
          ctx: ObjectEncoderContext ->
          run {
            ctx.add(FieldDescriptor.of("event_type"), sessionEvent.eventType)
            ctx.add(FieldDescriptor.of("session_data"), sessionEvent.sessionData)
            ctx.add(FieldDescriptor.of("application_info"), sessionEvent.applicationInfo)
          }
        }

        it.registerEncoder(SessionInfo::class.java) {
          sessionInfo: SessionInfo,
          ctx: ObjectEncoderContext ->
          run {
            ctx.add(FieldDescriptor.of("session_id"), sessionInfo.sessionId)
            ctx.add(FieldDescriptor.of("first_session_id"), sessionInfo.firstSessionId)
            ctx.add(FieldDescriptor.of("session_index"), sessionInfo.sessionIndex)
            ctx.add(
              FieldDescriptor.of("firebase_installation_id"),
              sessionInfo.firebaseInstallationId
            )
            ctx.add(FieldDescriptor.of("event_timestamp_us"), sessionInfo.eventTimestampUs)
            ctx.add(FieldDescriptor.of("data_collection_status"), sessionInfo.dataCollectionStatus)
          }
        }

        it.registerEncoder(DataCollectionStatus::class.java) {
          dataCollectionStatus: DataCollectionStatus,
          ctx: ObjectEncoderContext ->
          run {
            ctx.add(FieldDescriptor.of("performance"), dataCollectionStatus.performance)
            ctx.add(FieldDescriptor.of("crashlytics"), dataCollectionStatus.crashlytics)
            ctx.add(
              FieldDescriptor.of("session_sampling_rate"),
              dataCollectionStatus.sessionSamplingRate
            )
          }
        }

        it.registerEncoder(ApplicationInfo::class.java) {
          applicationInfo: ApplicationInfo,
          ctx: ObjectEncoderContext ->
          run {
            ctx.add(FieldDescriptor.of("app_id"), applicationInfo.appId)
            ctx.add(FieldDescriptor.of("device_model"), applicationInfo.deviceModel)
            ctx.add(FieldDescriptor.of("session_sdk_version"), applicationInfo.sessionSdkVersion)
            ctx.add(FieldDescriptor.of("os_version"), applicationInfo.osVersion)
            ctx.add(FieldDescriptor.of("log_environment"), applicationInfo.logEnvironment)
            ctx.add(FieldDescriptor.of("android_app_info"), applicationInfo.androidAppInfo)
          }
        }

        it.registerEncoder(AndroidApplicationInfo::class.java) {
          androidAppInfo: AndroidApplicationInfo,
          ctx: ObjectEncoderContext ->
          run {
            ctx.add(FieldDescriptor.of("package_name"), androidAppInfo.packageName)
            ctx.add(FieldDescriptor.of("version_name"), androidAppInfo.versionName)
            ctx.add(FieldDescriptor.of("app_build_version"), androidAppInfo.appBuildVersion)
            ctx.add(FieldDescriptor.of("device_manufacturer"), androidAppInfo.deviceManufacturer)
          }
        }
      }
      .build()

  /**
   * Construct a Session Start event.
   *
   * Some mutable fields, e.g. firebaseInstallationId, get populated later.
   */
  fun startSession(
    firebaseApp: FirebaseApp,
    sessionDetails: SessionDetails,
    sessionsSettings: SessionsSettings,
  ) =
    SessionEvent(
      eventType = EventType.SESSION_START,
      sessionData =
        SessionInfo(
          sessionDetails.sessionId,
          sessionDetails.firstSessionId,
          sessionDetails.sessionIndex,
          eventTimestampUs = sessionDetails.sessionTimestampUs,
          DataCollectionStatus(sessionSamplingRate = sessionsSettings.samplingRate),
        ),
      applicationInfo = getApplicationInfo(firebaseApp)
    )

  fun getApplicationInfo(firebaseApp: FirebaseApp): ApplicationInfo {
    val context = firebaseApp.applicationContext
    val packageName = context.packageName
    val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
    val buildVersion =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toString()
      } else {
        @Suppress("DEPRECATION") packageInfo.versionCode.toString()
      }

    return ApplicationInfo(
      appId = firebaseApp.options.applicationId,
      deviceModel = Build.MODEL,
      sessionSdkVersion = BuildConfig.VERSION_NAME,
      osVersion = Build.VERSION.RELEASE,
      logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
      androidAppInfo =
        AndroidApplicationInfo(
          packageName = packageName,
          versionName = packageInfo.versionName,
          appBuildVersion = buildVersion,
          deviceManufacturer = Build.MANUFACTURER,
        )
    )
  }
}
