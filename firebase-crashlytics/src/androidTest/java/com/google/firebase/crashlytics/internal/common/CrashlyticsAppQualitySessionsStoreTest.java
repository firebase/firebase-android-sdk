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
import static com.google.firebase.crashlytics.internal.common.CrashlyticsAppQualitySessionsStore.readAqsSessionIdFile;
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
  private FileStore fileStore;

  @Override
  protected void setUp() throws Exception {
    // The files created by each test case will get cleaned up in super.tearDown().
    fileStore = spy(new FileStore(getContext()));
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

  public void testRotateAqsId_neverRotatedSessionId_doesNotPersist() {
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Does not create a file because there is no session to persist in.
    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isNull();
  }

  public void testRotateSessionId_neverRotatedAqsId_doesNotPersist() {
    aqsStore.rotateSessionId(SESSION_ID);

    // Does not create a file because there was no aqs id to persist.
    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isNull();
  }

  public void testRotateBothIds_persists() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testRotateBothIds_storesIds() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Delete all session files to verify the getter is reading the locally stored ids.
    fileStore.deleteSessionFiles(SESSION_ID);

    assertThat(aqsStore.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testRotateBothIds_thenRotateAqsId_persists() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testRotateBothIds_thenRotateSessionId_persistsInNewSession() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    aqsStore.rotateSessionId(NEW_SESSION_ID);

    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
    assertThat(readAqsSessionIdFile(fileStore, NEW_SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testRotateBothIds_thenSessionId_thenAqsId_persistsInNewSessionOnly() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    aqsStore.rotateSessionId(NEW_SESSION_ID);

    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Old session still contains the old aqs id.
    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);

    // New sessions contains the new aqs id.
    assertThat(readAqsSessionIdFile(fileStore, NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testRotateBothIds_thenAqsId_thenSessionId_persistsInBothSessions() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    aqsStore.rotateSessionId(NEW_SESSION_ID);

    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
    assertThat(readAqsSessionIdFile(fileStore, NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testRotateBothIds_thenReadInvalidSessionId_returnsNull() {
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    assertThat(readAqsSessionIdFile(fileStore, "sessionDoesNotExist")).isNull();
  }

  public void testRotateAqsIdWhileSessionClosed_persistsAqsIdInNewSessionOnly() {
    // Setup first session.
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Close the first session.
    aqsStore.rotateSessionId(CLOSED_SESSION);

    // Rotate the aqs session id while Crashlytics session is closed.
    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new Crashlytics session.
    aqsStore.rotateSessionId(NEW_SESSION_ID);

    // Verify the old session has the old aqs id
    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);

    // Verify the new session has the updated aqs id.
    assertThat(readAqsSessionIdFile(fileStore, NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testUpdateAqsIdWhileSessionFailedToClosed_persistsNewAqsIdInBothSessions() {
    // Setup first session.
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Simulate failing to close the first session by not closing it.

    // Update the aqs session id while Crashlytics session failed to closed.
    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new Crashlytics session.
    aqsStore.rotateSessionId(NEW_SESSION_ID);

    // Verify the old session has the new aqs id since it failed to close.
    assertThat(readAqsSessionIdFile(fileStore, SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);

    // Verify the new session has the updated aqs id.
    assertThat(readAqsSessionIdFile(fileStore, NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testGetAppQualitySessionId_manyAqsIdRotations_returnsLatestAqsIdPerSession() {
    // Open the first Crashlytics session.
    aqsStore.rotateSessionId(SESSION_ID);

    // Rotate the aqs id several times.
    aqsStore.rotateAppQualitySessionId("aqs id 1");
    aqsStore.rotateAppQualitySessionId("aqs id 2");
    aqsStore.rotateAppQualitySessionId("aqs id 3");
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Open a new Crashlytics session.
    aqsStore.rotateSessionId(NEW_SESSION_ID);

    // Rotate the aqs id several times.
    aqsStore.rotateAppQualitySessionId("new aqs id 1");
    aqsStore.rotateAppQualitySessionId("new aqs id 2");
    aqsStore.rotateAppQualitySessionId("new aqs id 3");
    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Verify the latest aqs id per session is returned.
    assertThat(aqsStore.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
    assertThat(aqsStore.getAppQualitySessionId(NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testGetAppQualitySessionId_manySessionIdRotations_returnsProperAqsIdForEachSession() {
    // Rotate the aqs id with no Crashlytics session.
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Rotate the Crashlytics id several times.
    aqsStore.rotateSessionId("session id 1");
    aqsStore.rotateSessionId("session id 2");
    aqsStore.rotateSessionId("session id 3");
    aqsStore.rotateSessionId(SESSION_ID);

    // Rotate the aqs session id.
    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Update the aqs id several times.
    aqsStore.rotateSessionId("new session id 1");
    aqsStore.rotateSessionId("new session id 2");
    aqsStore.rotateSessionId("new session id 3");
    aqsStore.rotateSessionId(NEW_SESSION_ID);

    // Verify the correct aqs id for each session is returned.
    assertThat(aqsStore.getAppQualitySessionId(SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
    assertThat(aqsStore.getAppQualitySessionId(NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testGetAppQualitySessionId_afterRelaunch_returnsPersistedAqsId() {
    // Setup first session.
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Rotate the aqs id during the Crashlytics session
    aqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Simulate a native crash and relaunch by making a new aqs store instance.
    CrashlyticsAppQualitySessionsStore newAqsStore =
        new CrashlyticsAppQualitySessionsStore(new FileStore(getContext()));

    assertThat(newAqsStore.getAppQualitySessionId(SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testGetAppQualitySessionId_afterRelaunch_afterRotate_returnsPersistedAqsId() {
    // Setup first session.
    aqsStore.rotateSessionId(SESSION_ID);
    aqsStore.rotateAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Simulate a native crash and relaunch by making a new aqs store instance.
    CrashlyticsAppQualitySessionsStore newAqsStore =
        new CrashlyticsAppQualitySessionsStore(new FileStore(getContext()));

    // Rotate the ids in the new launch.
    newAqsStore.rotateSessionId(NEW_SESSION_ID);
    newAqsStore.rotateAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Verify the old session still persisted the old aqs id.
    assertThat(newAqsStore.getAppQualitySessionId(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }
}
