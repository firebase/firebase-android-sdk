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
import com.google.firebase.encoders.json.JsonDataEncoderBuilder
import com.google.firebase.sessions.api.SessionSubscriber
import com.google.firebase.sessions.settings.SessionsSettings

/** Contains functions for [SessionEvent]s. */
internal object SessionEvents {
  /** JSON [DataEncoder] for [SessionEvent]s. */
  internal val SESSION_EVENT_ENCODER: DataEncoder =
    JsonDataEncoderBuilder()
      .configureWith(AutoSessionEventEncoder.CONFIG)
      .ignoreNullValues(true)
      .build()

  /** Construct a Session Start event. */
  fun buildSession(
    firebaseApp: FirebaseApp,
    sessionDetails: SessionDetails,
    sessionsSettings: SessionsSettings,
    subscribers: Map<SessionSubscriber.Name, SessionSubscriber> = emptyMap(),
    firebaseInstallationId: String = "",
    firebaseAuthenticationToken: String = "",
  ) =
    SessionEvent(
      eventType = EventType.SESSION_START,
      sessionData =
        SessionInfo(
          sessionDetails.sessionId,
          sessionDetails.firstSessionId,
          sessionDetails.sessionIndex,
          eventTimestampUs = sessionDetails.sessionStartTimestampUs,
          DataCollectionStatus(
            performance = toDataCollectionState(subscribers[SessionSubscriber.Name.PERFORMANCE]),
            crashlytics = toDataCollectionState(subscribers[SessionSubscriber.Name.CRASHLYTICS]),
            sessionSamplingRate = sessionsSettings.samplingRate,
          ),
          firebaseInstallationId,
          firebaseAuthenticationToken,
        ),
      applicationInfo = getApplicationInfo(firebaseApp),
    )

  fun getApplicationInfo(firebaseApp: FirebaseApp): ApplicationInfo {
    val context = firebaseApp.applicationContext
    val packageName = context.packageName
    @Suppress("DEPRECATION") // TODO(mrober): Use ApplicationInfoFlags when target sdk set to 33
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
          versionName = packageInfo.versionName ?: buildVersion,
          appBuildVersion = buildVersion,
          deviceManufacturer = Build.MANUFACTURER,
          ProcessDetailsProvider.getCurrentProcessDetails(firebaseApp.applicationContext),
          ProcessDetailsProvider.getAppProcessDetails(firebaseApp.applicationContext),
        ),
    )
  }

  private fun toDataCollectionState(subscriber: SessionSubscriber?): DataCollectionState =
    if (subscriber == null) {
      DataCollectionState.COLLECTION_SDK_NOT_INSTALLED
    } else if (subscriber.isDataCollectionEnabled) {
      DataCollectionState.COLLECTION_ENABLED
    } else {
      DataCollectionState.COLLECTION_DISABLED
    }
}
