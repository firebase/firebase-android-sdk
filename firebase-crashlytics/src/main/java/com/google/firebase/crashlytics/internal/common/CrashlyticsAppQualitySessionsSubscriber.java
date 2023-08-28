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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.sessions.api.SessionSubscriber;

/**
 * App Quality Sessions subscriber for Crashlytics to subscribe to and store the
 * appQualitySessionId, which is different than the Crashlytics sessionId.
 */
public class CrashlyticsAppQualitySessionsSubscriber implements SessionSubscriber {
  private final DataCollectionArbiter dataCollectionArbiter;
  private final CrashlyticsAppQualitySessionsStore appQualitySessionsStore;

  public CrashlyticsAppQualitySessionsSubscriber(
      DataCollectionArbiter dataCollectionArbiter, FileStore fileStore) {
    this.dataCollectionArbiter = dataCollectionArbiter;
    appQualitySessionsStore = new CrashlyticsAppQualitySessionsStore(fileStore);
  }

  /** Gets the App Quality Sessions session id for the given Crashlytics session id. */
  @Nullable
  public String getAppQualitySessionId(@NonNull String sessionId) {
    return appQualitySessionsStore.getAppQualitySessionId(sessionId);
  }

  /** Called when the Crashlytics session id changes or closes. */
  public void setSessionId(@Nullable String sessionId) {
    appQualitySessionsStore.rotateSessionId(sessionId);
  }

  /** Called by the Sessions sdk when the App Quality Sessions session id changes. */
  @Override
  public void onSessionChanged(@NonNull SessionDetails sessionDetails) {
    Logger.getLogger().d("App Quality Sessions session changed: " + sessionDetails);
    appQualitySessionsStore.rotateAppQualitySessionId(sessionDetails.getSessionId());
  }

  @Override
  public boolean isDataCollectionEnabled() {
    return dataCollectionArbiter.isAutomaticDataCollectionEnabled();
  }

  @NonNull
  @Override
  public SessionSubscriber.Name getSessionSubscriberName() {
    return SessionSubscriber.Name.CRASHLYTICS;
  }
}
