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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.persistence.FileStore;

public final class CrashlyticsAppQualitySessionsStoreTest extends CrashlyticsTestCase {
  private static final String CLOSED_SESSION = null;

  private static final String SESSION_ID = "64e61da7023800012303a14eecd3f58d";
  private static final String APP_QUALITY_SESSION_ID = "79fd5d2e08ef4ea9a4f378879e53af2e";
  private static final String NEW_SESSION_ID = "64e61da0007500012284a14eecd3f58d";
  private static final String NEW_APP_QUALITY_SESSION_ID = "f7196b60000a44a092b5cc9e624f551c";

  private CrashlyticsAppQualitySessionsStore aqsStore;

  @Override
  protected void setUp() throws Exception {
    // The files created by each test case will get cleaned up in super.tearDown().
    FileStore fileStore = spy(new FileStore(getContext()));
    aqsStore = new CrashlyticsAppQualitySessionsStore(fileStore);

    when(fileStore.getSessionFile(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              // The AQS file store relies on file timestamps. This sleep ensures each new file
              // has a unique timestamp, so the most recent file can be found deterministically.
              Thread.sleep(1000L);
              return invocation.callRealMethod();
            });
  }

  public void testSetBothIds_createsFile() {
    aqsStore.setSessionId(SESSION_ID);
    aqsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    assertThat(aqsStore.readAqsSessionIdFile(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testSetBothIds_updatedAqsId_createsFile() {
    aqsStore.setSessionId(SESSION_ID);
    aqsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    aqsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    assertThat(aqsStore.readAqsSessionIdFile(SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testUpdateAqsIdWhileSessionClosed_persistsAqsIdInNewSession() {
    // Setup first session.
    aqsStore.setSessionId(SESSION_ID);
    aqsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Close the first session.
    aqsStore.setSessionId(CLOSED_SESSION);

    // Update the aqs session id while crashlytics session is closed.
    aqsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new crashlytics session.
    aqsStore.setSessionId(NEW_SESSION_ID);

    // Verify the old session has the old aqs id
    assertThat(aqsStore.readAqsSessionIdFile(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);

    // Verify the new session has the updated aqs id.
    assertThat(aqsStore.readAqsSessionIdFile(NEW_SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testUpdateAqsIdWhileSessionFailedToClosed_persistsNewAqsIdInBothSessions() {
    // Setup first session.
    aqsStore.setSessionId(SESSION_ID);
    aqsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Simulate failing to close the first session by not closing it.

    // Update the aqs session id while crashlytics session is closed.
    aqsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new crashlytics session.
    aqsStore.setSessionId(NEW_SESSION_ID);

    // Verify the old session has the new aqs id since it failed to close.
    assertThat(aqsStore.readAqsSessionIdFile(SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);

    // Verify the new session has the updated aqs id.
    assertThat(aqsStore.readAqsSessionIdFile(NEW_SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testGetAppQualitySessionId_returnsLatestAqsIdPerSession() {
    // Open the first crashlytics session.
    aqsStore.setSessionId(SESSION_ID);

    // Update the aqs id several times.
    aqsStore.setAppQualitySessionId("aqs id 1");
    aqsStore.setAppQualitySessionId("aqs id 2");
    aqsStore.setAppQualitySessionId("aqs id 3");
    aqsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Open a new crashlytics session.
    aqsStore.setSessionId(NEW_SESSION_ID);

    // Update the aqs id several times.
    aqsStore.setAppQualitySessionId("new aqs id 1");
    aqsStore.setAppQualitySessionId("new aqs id 2");
    aqsStore.setAppQualitySessionId("new aqs id 3");
    aqsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Verify the latest aqs id per session is returned.
    assertThat(aqsStore.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
    assertThat(aqsStore.getAppQualitySessionId(NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }
}
