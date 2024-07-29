// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.metadata;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.CrashlyticsWorker;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@SuppressWarnings("ResultOfMethodCallIgnored") // Convenient use of files.
public class MetaDataStoreTest extends CrashlyticsTestCase {

  private static final String SESSION_ID_1 = "session1";
  private static final String SESSION_ID_2 = "session2";

  private static final String USER_ID = "testUserId";

  private static final String KEY_1 = "testKey1";
  private static final String KEY_2 = "testKey2";
  private static final String KEY_3 = "testKey3";
  private static final String VALUE_1 = "testValue1";
  private static final String VALUE_2 = "testValue2";
  private static final String VALUE_3 = "testValue3";

  private static final String UNICODE = "あいうえおかきくけ";

  private static final String ESCAPED = "\ttest\nvalue";

  private static final List<RolloutAssignment> ROLLOUTS_STATE = new ArrayList<>();

  static {
    RolloutAssignment assignment =
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1);
    ROLLOUTS_STATE.add(assignment);
  }

  private FileStore fileStore;

  private CrashlyticsWorker diskWriteWorker;
  private MetaDataStore storeUnderTest;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    fileStore = new FileStore(getContext());
    storeUnderTest = new MetaDataStore(fileStore);
    diskWriteWorker = new CrashlyticsWorker(TestOnlyExecutors.background());
  }

  @Override
  public void tearDown() throws Exception {
    fileStore.deleteAllCrashlyticsFiles();
  }

  private UserMetadata metadataWithUserId(String sessionId) {
    return metadataWithUserId(sessionId, USER_ID);
  }

  private UserMetadata metadataWithUserId(String sessionId, String userId) {
    UserMetadata metadata = new UserMetadata(sessionId, fileStore, diskWriteWorker);
    metadata.setUserId(userId);
    return metadata;
  }

  @Test
  public void testWriteUserData_allFields() throws Exception {
    diskWriteWorker.submit(
        () -> {
          storeUnderTest.writeUserData(SESSION_ID_1, metadataWithUserId(SESSION_ID_1).getUserId());
        });
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertEquals(USER_ID, userData.getUserId());
  }

  @Test
  public void testWriteUserData_noFields() throws Exception {
    diskWriteWorker.submit(
        () -> {
          storeUnderTest.writeUserData(
              SESSION_ID_1, new UserMetadata(SESSION_ID_1, fileStore, null).getUserId());
        });
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertNull(userData.getUserId());
  }

  @Test
  public void testWriteUserData_singleField() throws Exception {
    diskWriteWorker.submit(
        () -> {
          storeUnderTest.writeUserData(SESSION_ID_1, metadataWithUserId(SESSION_ID_1).getUserId());
        });
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertEquals(USER_ID, userData.getUserId());
  }

  @Test
  public void testWriteUserData_null() throws Exception {
    diskWriteWorker.submit(
        () -> {
          storeUnderTest.writeUserData(
              SESSION_ID_1, metadataWithUserId(SESSION_ID_1, null).getUserId());
        });
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertNull(userData.getUserId());
  }

  @Test
  public void testWriteUserData_emptyString() throws Exception {
    diskWriteWorker.submit(
        () -> {
          storeUnderTest.writeUserData(
              SESSION_ID_1, metadataWithUserId(SESSION_ID_1, "").getUserId());
        });
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertEquals("", userData.getUserId());
  }

  @Test
  public void testWriteUserData_unicode() throws Exception {
    storeUnderTest.writeUserData(
        SESSION_ID_1, metadataWithUserId(SESSION_ID_1, UNICODE).getUserId());
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertEquals(UNICODE, userData.getUserId());
  }

  @Test
  public void testWriteUserData_escaped() throws Exception {
    diskWriteWorker.submit(
        () -> {
          storeUnderTest.writeUserData(
              SESSION_ID_1, metadataWithUserId(SESSION_ID_1, ESCAPED).getUserId());
        });
    diskWriteWorker.await();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertEquals(ESCAPED.trim(), userData.getUserId());
  }

  @Test
  public void testWriteUserData_readDifferentSession() {
    storeUnderTest.writeUserData(SESSION_ID_1, metadataWithUserId(SESSION_ID_1).getUserId());
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_2, fileStore, diskWriteWorker);
    assertNull(userData.getUserId());
  }

  @Test
  public void testReadUserData_corruptData() throws IOException {
    File file = storeUnderTest.getUserDataFileForSession(SESSION_ID_1);
    try (PrintWriter printWriter = new PrintWriter(file)) {
      printWriter.println("Matt says hi!");
    }
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertNull(userData.getUserId());
    assertFalse(file.exists());
  }

  @Test
  public void testReadUserData_emptyData() throws IOException {
    File file = storeUnderTest.getUserDataFileForSession(SESSION_ID_1);
    file.createNewFile();
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertNull(userData.getUserId());
    assertFalse(file.exists());
  }

  @Test
  public void testReadUserData_noStoredData() {
    UserMetadata userData =
        UserMetadata.loadFromExistingSession(SESSION_ID_1, fileStore, diskWriteWorker);
    assertNull(userData.getUserId());
  }

  @Test
  public void testUpdateSessionId_notPersistUserIdToNewSessionIfNoUserIdSet() throws Exception {
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    userMetadata.setNewSession(SESSION_ID_2);
    diskWriteWorker.submit(
        () -> {
          assertThat(
                  fileStore.getSessionFile(SESSION_ID_2, UserMetadata.USERDATA_FILENAME).exists())
              .isFalse();
        });
    diskWriteWorker.await();
  }

  @Test
  public void testUpdateSessionId_notPersistCustomKeysToNewSessionIfNoCustomKeysSet()
      throws Exception {
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    userMetadata.setNewSession(SESSION_ID_2);
    diskWriteWorker.submit(
        () -> {
          assertThat(fileStore.getSessionFile(SESSION_ID_2, UserMetadata.KEYDATA_FILENAME).exists())
              .isFalse();
        });
    diskWriteWorker.await();
  }

  @Test
  public void testUpdateSessionId_notPersistRolloutsToNewSessionIfNoRolloutsSet() throws Exception {
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    userMetadata.setNewSession(SESSION_ID_2);

    diskWriteWorker.submit(
        () -> {
          assertThat(
                  fileStore
                      .getSessionFile(SESSION_ID_2, UserMetadata.ROLLOUTS_STATE_FILENAME)
                      .exists())
              .isFalse();
        });
    diskWriteWorker.await();
  }

  @Test
  public void testUpdateSessionId_persistCustomKeysToNewSessionIfCustomKeysSet() throws Exception {
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, VALUE_1);
            put(KEY_2, VALUE_2);
            put(KEY_3, VALUE_3);
          }
        };
    userMetadata.setCustomKeys(keys);
    userMetadata.setNewSession(SESSION_ID_2);
    diskWriteWorker.submit(
        () -> {
          assertThat(fileStore.getSessionFile(SESSION_ID_2, UserMetadata.KEYDATA_FILENAME).exists())
              .isTrue();
        });
    diskWriteWorker.await();

    MetaDataStore metaDataStore = new MetaDataStore(fileStore);
    assertThat(metaDataStore.readKeyData(SESSION_ID_2)).isEqualTo(keys);
  }

  @Test
  public void testSetSameKeysRaceCondition_preserveLastEntryValue() throws Exception {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, "10000");
            put(KEY_2, "20000");
          }
        };
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    for (int index = 0; index <= 10000; index++) {
      userMetadata.setCustomKey(KEY_1, String.valueOf(index));
      userMetadata.setCustomKey(KEY_2, String.valueOf(index * 2));
    }
    diskWriteWorker.submit(
        () -> {
          final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
          assertThat(readKeys.get(KEY_1)).isEqualTo("10000");
          assertThat(readKeys.get(KEY_2)).isEqualTo("20000");
          assertEqualMaps(keys, readKeys);
        });
    diskWriteWorker.await();
  }

  @Test
  public void testUpdateSessionId_persistUserIdToNewSessionIfUserIdSet() throws Exception {
    String userId = "ThemisWang";
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    userMetadata.setUserId(userId);
    userMetadata.setNewSession(SESSION_ID_2);

    diskWriteWorker.submit(
        () -> {
          assertThat(
                  fileStore.getSessionFile(SESSION_ID_2, UserMetadata.USERDATA_FILENAME).exists())
              .isTrue();
        });
    diskWriteWorker.await();

    MetaDataStore metaDataStore = new MetaDataStore(fileStore);
    assertThat(metaDataStore.readUserId(SESSION_ID_2)).isEqualTo(userId);
  }

  @Test
  public void testUpdateSessionId_persistRolloutsToNewSessionIfRolloutsSet() throws Exception {
    UserMetadata userMetadata = new UserMetadata(SESSION_ID_1, fileStore, diskWriteWorker);
    userMetadata.updateRolloutsState(ROLLOUTS_STATE);
    userMetadata.setNewSession(SESSION_ID_2);
    diskWriteWorker.submit(
        () -> {
          assertThat(
                  fileStore
                      .getSessionFile(SESSION_ID_2, UserMetadata.ROLLOUTS_STATE_FILENAME)
                      .exists())
              .isTrue();
        });
    diskWriteWorker.await();

    MetaDataStore metaDataStore = new MetaDataStore(fileStore);
    assertThat(metaDataStore.readRolloutsState(SESSION_ID_2)).isEqualTo(ROLLOUTS_STATE);
  }

  // Keys

  public void testWriteKeys() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, VALUE_1);
            put(KEY_2, VALUE_2);
            put(KEY_3, VALUE_3);
          }
        };
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEqualMaps(keys, readKeys);
  }

  public void testWriteKeys_noValues() {
    final Map<String, String> keys = Collections.emptyMap();
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEqualMaps(keys, readKeys);
  }

  public void testWriteKeys_nullValues() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, null);
            put(KEY_2, VALUE_2);
            put(KEY_3, null);
          }
        };
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEqualMaps(keys, readKeys);
  }

  public void testWriteKeys_emptyStrings() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, "");
            put(KEY_2, VALUE_2);
            put(KEY_3, "");
          }
        };
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEqualMaps(keys, readKeys);
  }

  public void testWriteKeys_unicode() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, UNICODE);
            put(KEY_2, VALUE_2);
            put(KEY_3, UNICODE);
          }
        };
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEqualMaps(keys, readKeys);
  }

  public void testWriteKeys_escaped() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, ESCAPED);
            put(KEY_2, VALUE_2);
            put(KEY_3, ESCAPED);
          }
        };
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEqualMaps(keys, readKeys);
  }

  public void testWriteKeys_readDifferentSession() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, VALUE_2);
            put(KEY_2, VALUE_2);
            put(KEY_3, VALUE_3);
          }
        };
    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_2);
    assertEquals(0, readKeys.size());
  }

  // Ensures the Internal Keys and User Custom Keys are stored separately
  public void testWriteKeys_readSeparateFromUser() {
    final Map<String, String> keys =
        new HashMap<String, String>() {
          {
            put(KEY_1, VALUE_1);
          }
        };

    final Map<String, String> internalKeys =
        new HashMap<String, String>() {
          {
            put(KEY_2, VALUE_2);
            put(KEY_3, VALUE_3);
          }
        };

    storeUnderTest.writeKeyData(SESSION_ID_1, keys);
    storeUnderTest.writeKeyData(SESSION_ID_1, internalKeys, /* isInternal= */ true);

    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    final Map<String, String> readInternalKeys = storeUnderTest.readKeyData(SESSION_ID_1, true);

    assertEqualMaps(keys, readKeys);
    assertEqualMaps(internalKeys, readInternalKeys);
  }

  public void testReadKeys_corruptData() throws IOException {
    File file = storeUnderTest.getKeysFileForSession(SESSION_ID_1);
    try (PrintWriter printWriter = new PrintWriter(file)) {
      printWriter.println("This is not json.");
    }
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEquals(0, readKeys.size());
    assertFalse(file.exists());
  }

  public void testReadKeys_emptyStoredData() throws IOException {
    File file = storeUnderTest.getKeysFileForSession(SESSION_ID_1);
    file.createNewFile();
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEquals(0, readKeys.size());
    assertFalse(file.exists());
  }

  public void testReadKeys_noStoredData() {
    final Map<String, String> readKeys = storeUnderTest.readKeyData(SESSION_ID_1);
    assertEquals(0, readKeys.size());
  }

  @Test
  public void testWriteReadRolloutState() throws Exception {
    storeUnderTest.writeRolloutState(SESSION_ID_1, ROLLOUTS_STATE);
    List<RolloutAssignment> readRolloutsState = storeUnderTest.readRolloutsState(SESSION_ID_1);

    assertThat(readRolloutsState).isEqualTo(ROLLOUTS_STATE);
  }

  @Test
  public void testWriteReadRolloutState_writeValidThenEmpty() throws Exception {
    storeUnderTest.writeRolloutState(SESSION_ID_1, ROLLOUTS_STATE);
    List<RolloutAssignment> emptyState = new ArrayList<>();
    storeUnderTest.writeRolloutState(SESSION_ID_1, emptyState);

    assertThat(
            fileStore.getSessionFile(SESSION_ID_1, UserMetadata.ROLLOUTS_STATE_FILENAME).exists())
        .isFalse();
  }

  public static void assertEqualMaps(Map<String, String> expected, Map<String, String> actual) {
    assertEquals(expected.size(), actual.size());
    for (String key : expected.keySet()) {
      assertTrue(actual.containsKey(key));
      assertEquals(expected.get(key), actual.get(key));
    }
  }
}
