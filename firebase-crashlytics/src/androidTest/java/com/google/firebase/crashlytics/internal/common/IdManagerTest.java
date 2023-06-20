// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import static com.google.firebase.crashlytics.internal.common.DataCollectionArbiterTest.MOCK_ARBITER_DISABLED;
import static com.google.firebase.crashlytics.internal.common.DataCollectionArbiterTest.MOCK_ARBITER_ENABLED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.TimeoutException;

public class IdManagerTest extends CrashlyticsTestCase {

  SharedPreferences prefs;
  SharedPreferences legacyPrefs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    prefs = CommonUtils.getSharedPrefs(getContext());
    legacyPrefs = CommonUtils.getLegacySharedPrefs(getContext());
    clearPrefs();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    clearPrefs();
  }

  private void clearPrefs() {
    prefs
        .edit()
        .remove(IdManager.PREFKEY_ADVERTISING_ID)
        .remove(IdManager.PREFKEY_FIREBASE_IID)
        .remove(IdManager.PREFKEY_INSTALLATION_UUID)
        .apply();

    legacyPrefs
        .edit()
        .remove(IdManager.PREFKEY_LEGACY_INSTALLATION_UUID)
        .remove(IdManager.PREFKEY_ADVERTISING_ID)
        .apply();
  }

  private IdManager createIdManager(String instanceId, DataCollectionArbiter arbiter) {
    FirebaseInstallationsApi iid = mock(FirebaseInstallationsApi.class);
    when(iid.getId()).thenReturn(Tasks.forResult(instanceId));

    return new IdManager(getContext(), getContext().getPackageName(), iid, arbiter);
  }

  public void testCreateUUID() {
    final String fid = "test_fid";
    final IdManager idManager = createIdManager(fid, MOCK_ARBITER_ENABLED);
    final String installId = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotNull(installId);

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertNull(prefs.getString(IdManager.PREFKEY_ADVERTISING_ID, null));
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(installId, idManager.getInstallIds().getCrashlyticsInstallId());
  }

  public void testGetIdExceptionalCase_doesNotRotateInstallId() {
    FirebaseInstallationsApi fis = mock(FirebaseInstallationsApi.class);
    final String expectedInstallId = "expectedInstallId";
    when(fis.getId())
        .thenReturn(Tasks.forException(new TimeoutException("Fetching id timed out.")));
    prefs
        .edit()
        .putString(IdManager.PREFKEY_INSTALLATION_UUID, expectedInstallId)
        .putString(IdManager.PREFKEY_FIREBASE_IID, "firebase-iid")
        .apply();

    final IdManager idManager =
        new IdManager(getContext(), getContext().getPackageName(), fis, MOCK_ARBITER_ENABLED);
    final String actualInstallId = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotNull(actualInstallId);
    assertEquals(expectedInstallId, actualInstallId);
  }

  public void testInstanceIdChanges_dataCollectionEnabled() {
    // Set up the initial state with a valid iid and uuid.
    final String oldUuid = "old_uuid";
    final String newFid = "new_test_fid";
    prefs
        .edit()
        .putString(IdManager.PREFKEY_INSTALLATION_UUID, oldUuid)
        .putString(IdManager.PREFKEY_FIREBASE_IID, "old_test_fid")
        .apply();

    // Initialize the manager with a different FID.
    IdManager idManager = createIdManager(newFid, MOCK_ARBITER_ENABLED);

    String installId = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotNull(installId);
    assertFalse(installId.equals(oldUuid));

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertEquals(newFid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(installId, idManager.getInstallIds().getCrashlyticsInstallId());
  }

  void validateInstanceIdDoesntChange(boolean dataCollectionEnabled) {
    final String oldUuid = "test_uuid";
    final String fid = dataCollectionEnabled ? "test_fid" : IdManager.createSyntheticFid();
    // Set up the initial state with a valid iid and uuid.
    prefs
        .edit()
        .putString(IdManager.PREFKEY_INSTALLATION_UUID, oldUuid)
        .putString(IdManager.PREFKEY_FIREBASE_IID, fid)
        .apply();

    // Initialize the manager with the same IID.
    IdManager idManager =
        createIdManager(fid, dataCollectionEnabled ? MOCK_ARBITER_ENABLED : MOCK_ARBITER_DISABLED);

    String installId = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotNull(installId);

    // Test that the UUID didn't change.
    assertEquals(oldUuid, installId);

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(oldUuid, idManager.getInstallIds().getCrashlyticsInstallId());
  }

  public void testInstanceIdDoesntChange_dataCollectionEnabled() {
    validateInstanceIdDoesntChange(/* dataCollectionEnabled= */ true);
  }

  public void testInstanceIdDoesntChange_dataCollectionDisabled() {
    validateInstanceIdDoesntChange(/* dataCollectionEnabled= */ false);
  }

  public void testInstanceIdRotatesWithDataCollectionFlag() {
    final String originalUuid = "test_uuid";
    final String originalFid = "test_fid";
    // Set up the initial state with a valid iid and uuid.
    prefs
        .edit()
        .putString(IdManager.PREFKEY_INSTALLATION_UUID, originalUuid)
        .putString(IdManager.PREFKEY_FIREBASE_IID, originalFid)
        .apply();

    // Initialize the manager with the same FID.
    IdManager idManager = createIdManager(originalFid, MOCK_ARBITER_ENABLED);
    String firstUuid = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotNull(firstUuid);
    assertEquals(originalUuid, firstUuid);

    // subsequent calls should return the same id
    assertEquals(firstUuid, idManager.getInstallIds().getCrashlyticsInstallId());
    assertEquals(
        firstUuid,
        createIdManager(originalFid, MOCK_ARBITER_ENABLED)
            .getInstallIds()
            .getCrashlyticsInstallId());

    // Disable data collection manager and confirm we get a different id
    idManager = createIdManager(originalFid, MOCK_ARBITER_DISABLED);
    String secondUuid = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotSame(secondUuid, firstUuid);
    assertEquals(secondUuid, idManager.getInstallIds().getCrashlyticsInstallId());
    assertEquals(
        secondUuid,
        createIdManager(null, MOCK_ARBITER_DISABLED).getInstallIds().getCrashlyticsInstallId());
    // Check that we cached an synthetic FID
    final SharedPreferences prefs = CommonUtils.getSharedPrefs(getContext());
    String cachedFid = prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null);
    assertTrue(IdManager.isSyntheticFid(cachedFid));

    // re-enable data collection
    idManager = createIdManager(originalFid, MOCK_ARBITER_ENABLED);
    String thirdUuid = idManager.getInstallIds().getCrashlyticsInstallId();
    assertNotSame(thirdUuid, firstUuid);
    assertNotSame(thirdUuid, secondUuid);
    assertEquals(thirdUuid, idManager.getInstallIds().getCrashlyticsInstallId());
    assertEquals(
        thirdUuid,
        createIdManager(originalFid, MOCK_ARBITER_ENABLED)
            .getInstallIds()
            .getCrashlyticsInstallId());
    // The cached ID should be back to the original
    cachedFid = prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null);
    assertEquals(cachedFid, originalFid);
  }
}
