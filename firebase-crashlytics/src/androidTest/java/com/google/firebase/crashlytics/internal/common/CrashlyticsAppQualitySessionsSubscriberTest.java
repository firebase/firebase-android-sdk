// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.sessions.api.SessionSubscriber.SessionDetails;

public final class CrashlyticsAppQualitySessionsSubscriberTest extends CrashlyticsTestCase {
  private static final String SESSION_ID = "64e61da7023800012303a14eecd3f58d";
  private static final String APP_QUALITY_SESSION_ID = "79fd5d2e08ef4ea9a4f378879e53af2e";

  private CrashlyticsAppQualitySessionsSubscriber aqsSubscriber;

  @Override
  protected void setUp() throws Exception {
    // The files created by each test case will get cleaned up in super.tearDown().
    aqsSubscriber =
        new CrashlyticsAppQualitySessionsSubscriber(
            mock(DataCollectionArbiter.class), new FileStore(getContext()));
  }

  public void testGetAppQualitySessionId_returnsLatestAqsId() {
    aqsSubscriber.setSessionId(SESSION_ID);

    aqsSubscriber.onSessionChanged(createSessionDetails("aqs id 1"));
    aqsSubscriber.onSessionChanged(createSessionDetails("aqs id 2"));
    aqsSubscriber.onSessionChanged(createSessionDetails("aqs id 3"));
    aqsSubscriber.onSessionChanged(createSessionDetails(APP_QUALITY_SESSION_ID));

    assertThat(aqsSubscriber.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  private static SessionDetails createSessionDetails(@NonNull String appQualitySessionId) {
    return new SessionDetails(appQualitySessionId);
  }
}
