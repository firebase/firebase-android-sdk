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
  private static final String NEW_SESSION_ID = "64e61da0007500012284a14eecd3f58d";
  private static final String NEW_APP_QUALITY_SESSION_ID = "f7196b60000a44a092b5cc9e624f551c";

  private CrashlyticsAppQualitySessionsSubscriber aqsSubscriber;

  @Override
  protected void setUp() throws Exception {
    // The files created by each test case will get cleaned up in super.tearDown().
    aqsSubscriber =
        new CrashlyticsAppQualitySessionsSubscriber(
            mock(DataCollectionArbiter.class), new FileStore(getContext()));
  }

  public void testGetAppQualitySessionId_returnsLatestAqsIdForSession() {
    aqsSubscriber.setSessionId(SESSION_ID);

    aqsSubscriber.onSessionChanged(createSessionDetails("aqs id 1"));
    aqsSubscriber.onSessionChanged(createSessionDetails("aqs id 2"));
    aqsSubscriber.onSessionChanged(createSessionDetails("aqs id 3"));
    aqsSubscriber.onSessionChanged(createSessionDetails(APP_QUALITY_SESSION_ID));

    assertThat(aqsSubscriber.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testGetAppQualitySessionId_returnsCorrectAqsIdForEachSession() {
    String session_id_1 = "session id 1";
    String session_id_2 = "session id 2";
    String session_id_3 = "session id 3";
    String new_session_id_1 = "new session id 1";
    String new_session_id_2 = "new session id 2";
    String new_session_id_3 = "new session id 3";

    aqsSubscriber.onSessionChanged(createSessionDetails(APP_QUALITY_SESSION_ID));

    // Rotate the session id multiple times for a single aqs id.
    aqsSubscriber.setSessionId(session_id_1);
    aqsSubscriber.setSessionId(session_id_2);
    aqsSubscriber.setSessionId(session_id_3);
    aqsSubscriber.setSessionId(SESSION_ID);

    // Close the session.
    aqsSubscriber.setSessionId(null);

    // Rotate the aqs id.
    aqsSubscriber.onSessionChanged(createSessionDetails(NEW_APP_QUALITY_SESSION_ID));

    // Rotate the session id multiple times again for the rotated aqs id.
    aqsSubscriber.setSessionId(new_session_id_1);
    aqsSubscriber.setSessionId(new_session_id_2);
    aqsSubscriber.setSessionId(new_session_id_3);
    aqsSubscriber.setSessionId(NEW_SESSION_ID);

    assertThat(aqsSubscriber.getAppQualitySessionId(session_id_1))
        .isEqualTo(APP_QUALITY_SESSION_ID);
    assertThat(aqsSubscriber.getAppQualitySessionId(session_id_2))
        .isEqualTo(APP_QUALITY_SESSION_ID);
    assertThat(aqsSubscriber.getAppQualitySessionId(session_id_3))
        .isEqualTo(APP_QUALITY_SESSION_ID);

    assertThat(aqsSubscriber.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);

    assertThat(aqsSubscriber.getAppQualitySessionId(new_session_id_1))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
    assertThat(aqsSubscriber.getAppQualitySessionId(new_session_id_2))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
    assertThat(aqsSubscriber.getAppQualitySessionId(new_session_id_3))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);

    assertThat(aqsSubscriber.getAppQualitySessionId(NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  private static SessionDetails createSessionDetails(@NonNull String appQualitySessionId) {
    return new SessionDetails(appQualitySessionId);
  }
}
