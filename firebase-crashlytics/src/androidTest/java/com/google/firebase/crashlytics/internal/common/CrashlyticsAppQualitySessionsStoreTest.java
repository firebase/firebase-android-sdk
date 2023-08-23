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
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.io.IOException;

public final class CrashlyticsAppQualitySessionsStoreTest extends CrashlyticsTestCase {
  private static final boolean WITH_NDK = true;
  private static final boolean WITHOUT_NDK = false;
  private static final String CLOSED_SESSION = null;

  private static final String SESSION_ID = "64e61da7023800012303a14eecd3f58d";
  private static final String APP_QUALITY_SESSION_ID = "79fd5d2e08ef4ea9a4f378879e53af2e";
  private static final String NEW_SESSION_ID = "64e61da0007500012284a14eecd3f58d";
  private static final String NEW_APP_QUALITY_SESSION_ID = "f7196b60000a44a092b5cc9e624f551c";

  private CrashlyticsAppQualitySessionsStore appQualitySessionsStore;

  public void testSetBothIdsWithNdk_createsFile() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);

    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    assertAqsFileExists(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testSetBothIdsWithNdk_updatedAqsId_createsFile() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    assertAqsFileExists(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testBothIdsWithoutNdk_doesNotCreateFileOnNewAqsId() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITHOUT_NDK);

    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    assertAqsFileDoesNotExist(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isNull();
  }

  public void testBothIdsWithoutNdk_updatedAqsId_doesNotCreateFileOnNewAqsId() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITHOUT_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    assertAqsFileDoesNotExist(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isNull();
  }

  public void testBothIdsWithoutNdk_createsFileOnEvent() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITHOUT_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    appQualitySessionsStore.persistOnEvent();

    assertAqsFileExists(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);
  }

  public void testBothIdsWithoutNdk_updatedAqsId_createsFileOnEvent() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITHOUT_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);
    appQualitySessionsStore.persistOnEvent();

    assertAqsFileExists(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testUpdateAqsIdWhileSessionClosed_withNdk_persistsAqsIdInNewSession() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);

    // Setup first session.
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Close the first session.
    appQualitySessionsStore.setSessionId(CLOSED_SESSION);

    // Update the aqs session id while crashlytics session is closed.
    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new crashlytics session.
    appQualitySessionsStore.setSessionId(NEW_SESSION_ID);

    // Verify the old session has the old aqs id
    assertThat(readAqsFile(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);

    // Verify the new session has the updated aqs id.
    assertThat(readAqsFile(NEW_SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testUpdateAqsIdWhileSessionClosed_withoutNdk_persistsAqsIdInNewSessionOnEvent() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITHOUT_NDK);

    // Setup first session.
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Simulate an event.
    appQualitySessionsStore.persistOnEvent();

    // Close the first session.
    appQualitySessionsStore.setSessionId(CLOSED_SESSION);

    // Update the aqs session id while crashlytics session is closed.
    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new crashlytics session.
    appQualitySessionsStore.setSessionId(NEW_SESSION_ID);

    // Simulate an event in the new session.
    appQualitySessionsStore.persistOnEvent();

    // Verify the old session has the old aqs id
    assertAqsFileExists(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isEqualTo(APP_QUALITY_SESSION_ID);

    // Verify the new session has the updated aqs id.
    assertAqsFileExists(NEW_SESSION_ID);
    assertThat(readAqsFile(NEW_SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testUpdateAqsIdWhileSessionClosed_withoutNdk_doesNotPersistsAqsIdWithoutEvent() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITHOUT_NDK);

    // Setup first session.
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // No event, so does not persist anything.

    // Close the first session.
    appQualitySessionsStore.setSessionId(CLOSED_SESSION);

    // Update the aqs session id while crashlytics session is closed.
    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Start a new crashlytics session.
    appQualitySessionsStore.setSessionId(NEW_SESSION_ID);

    // No event, so does not persist anything.

    // Verify the old session has no aqs file persisted.
    assertAqsFileDoesNotExist(SESSION_ID);

    // Verify the new session has no aqs file persisted.
    assertAqsFileDoesNotExist(NEW_SESSION_ID);

    // Now simulate an event.
    appQualitySessionsStore.persistOnEvent();

    // Verify the new session has the updated aqs id.
    assertThat(readAqsFile(NEW_SESSION_ID)).isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testCorruptFile_withNdk_getsDeletedAndReturnsNull() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Verify the aqs file exists before testing it got deleted.
    assertAqsFileExists(SESSION_ID);
    corruptAqsFile(SESSION_ID);

    // Attempt to read the aqs id to trigger reading the corrupt aqs file.
    appQualitySessionsStore.setSessionId(NEW_SESSION_ID); // So it does not read from local value.
    appQualitySessionsStore.getAppQualitySessionId(SESSION_ID);

    assertAqsFileDoesNotExist(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isNull();
  }

  public void testCorruptFile_withoutNdk_getsDeletedAndReturnsNullOnEvent() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Verify the aqs file exists before testing it got deleted.
    appQualitySessionsStore.persistOnEvent();
    assertAqsFileExists(SESSION_ID);
    corruptAqsFile(SESSION_ID);

    // Attempt to read the aqs id to trigger reading the corrupt aqs file.
    appQualitySessionsStore.setSessionId(NEW_SESSION_ID); // So it does not read from local value.
    appQualitySessionsStore.getAppQualitySessionId(SESSION_ID);

    assertAqsFileDoesNotExist(SESSION_ID);
    assertThat(readAqsFile(SESSION_ID)).isNull();
  }

  public void testGetAppQualitySessionId_returnsLatestAqsIdPerSession() {
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);

    // Open the first crashlytics session.
    appQualitySessionsStore.setSessionId(SESSION_ID);

    // Update the aqs id several times.
    appQualitySessionsStore.setAppQualitySessionId("aqs id 1");
    appQualitySessionsStore.setAppQualitySessionId("aqs id 2");
    appQualitySessionsStore.setAppQualitySessionId("aqs id 3");
    appQualitySessionsStore.setAppQualitySessionId(APP_QUALITY_SESSION_ID);

    // Open a new crashlytics session.
    appQualitySessionsStore.setSessionId(NEW_SESSION_ID);

    // Update the aqs id several times.
    appQualitySessionsStore.setAppQualitySessionId("new aqs id 1");
    appQualitySessionsStore.setAppQualitySessionId("new aqs id 2");
    appQualitySessionsStore.setAppQualitySessionId("new aqs id 3");
    appQualitySessionsStore.setAppQualitySessionId(NEW_APP_QUALITY_SESSION_ID);

    // Verify the latest aqs id per session is returned.
    assertThat(appQualitySessionsStore.getAppQualitySessionId(SESSION_ID))
        .isEqualTo(APP_QUALITY_SESSION_ID);
    assertThat(appQualitySessionsStore.getAppQualitySessionId(NEW_SESSION_ID))
        .isEqualTo(NEW_APP_QUALITY_SESSION_ID);
  }

  public void testPersistedFileWithAqsIdTooLong() {
    String tooLongAqsId = "thisAqsIdIsWayTooLooooooooooooonnnnnoooooonnnnnggggggggg";
    appQualitySessionsStore = createAppQualitySessionsStore(WITH_NDK);
    appQualitySessionsStore.setSessionId(SESSION_ID);
    appQualitySessionsStore.setAppQualitySessionId(tooLongAqsId);

    // Close the session to simulate the next launch of the app after a native crash.
    appQualitySessionsStore.setSessionId(CLOSED_SESSION);

    // The persisted file should only read the first 32 characters.
    assertThat(appQualitySessionsStore.getAppQualitySessionId(SESSION_ID))
        .isEqualTo(tooLongAqsId.substring(0, 32));
  }

  /**
   * Create an instance of the CrashlyticsAppQualitySessionsStore with the given hasNdk flag.
   *
   * <p>The files created by each test case will get cleaned up in super.tearDown().
   */
  private CrashlyticsAppQualitySessionsStore createAppQualitySessionsStore(boolean hasNdk) {
    return new CrashlyticsAppQualitySessionsStore(new FileStore(getContext()), hasNdk);
  }

  private String readAqsFile(@NonNull String sessionId) {
    File aqsFile = appQualitySessionsStore.getAqsSessionIdFile(sessionId);
    return appQualitySessionsStore.readAAqsSessionIdFile(aqsFile);
  }

  @SuppressWarnings({"ResultOfMethodCallIgnored", "SameParameterValue"})
  private void corruptAqsFile(@NonNull String sessionId) {
    File aqsFile = appQualitySessionsStore.getAqsSessionIdFile(sessionId);
    try {
      aqsFile.delete();
      aqsFile.createNewFile();
    } catch (IOException ex) {
      throw new RuntimeException("Failed to corrupt aqs file.", ex);
    }
  }

  private void assertAqsFileDoesNotExist(@NonNull String sessionId) {
    File aqsFile = appQualitySessionsStore.getAqsSessionIdFile(sessionId);
    assertWithMessage("expected aqs file not to exist, but does").that(aqsFile.exists()).isFalse();
  }

  private void assertAqsFileExists(@NonNull String sessionId) {
    File aqsFile = appQualitySessionsStore.getAqsSessionIdFile(sessionId);
    assertWithMessage("expected aqs file to exist, but does not").that(aqsFile.exists()).isTrue();
  }
}
