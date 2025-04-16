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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.InstallIdProvider.InstallIds;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;

public class IdManagerTest extends CrashlyticsTestCase {

  private SharedPreferences prefs;
  private SharedPreferences legacyPrefs;

  private final CrashlyticsWorkers crashlyticsWorkers =
      new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());

  @Before
  public void setUp() throws Exception {
    prefs = CommonUtils.getSharedPrefs(getContext());
    legacyPrefs = CommonUtils.getLegacySharedPrefs(getContext());
    clearPrefs();
  }

  @After
  public void tearDown() throws Exception {
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

  public void testCreateUUID() throws Exception {
    final String fid = "test_fid";
    final IdManager idManager = createIdManager(fid, MOCK_ARBITER_ENABLED);
    final String installId = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotNull(installId);

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertNull(prefs.getString(IdManager.PREFKEY_ADVERTISING_ID, null));
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(installId, getInstallIds(idManager).getCrashlyticsInstallId());
  }

  public void testGetIdExceptionalCase_doesNotRotateInstallId() throws Exception {
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
    final String actualInstallId = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotNull(actualInstallId);
    assertEquals(expectedInstallId, actualInstallId);
  }

  public void testInstanceIdChanges_dataCollectionEnabled() throws Exception {
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

    String installId = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotNull(installId);
    assertFalse(installId.equals(oldUuid));

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertEquals(newFid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(installId, getInstallIds(idManager).getCrashlyticsInstallId());
  }

  void validateInstanceIdDoesntChange(boolean dataCollectionEnabled) throws Exception {
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

    String installId = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotNull(installId);

    // Test that the UUID didn't change.
    assertEquals(oldUuid, installId);

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(oldUuid, getInstallIds(idManager).getCrashlyticsInstallId());
  }

  public void testInstanceIdDoesntChange_dataCollectionEnabled() throws Exception {
    validateInstanceIdDoesntChange(/* dataCollectionEnabled= */ true);
  }

  public void testInstanceIdDoesntChange_dataCollectionDisabled() throws Exception {
    validateInstanceIdDoesntChange(/* dataCollectionEnabled= */ false);
  }

  public void testInstanceIdRotatesWithDataCollectionFlag() throws Exception {
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
    String firstUuid = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotNull(firstUuid);
    assertEquals(originalUuid, firstUuid);

    // subsequent calls should return the same id
    assertEquals(firstUuid, getInstallIds(idManager).getCrashlyticsInstallId());
    assertEquals(
        firstUuid,
        getInstallIds(createIdManager(originalFid, MOCK_ARBITER_ENABLED))
            .getCrashlyticsInstallId());

    // Disable data collection manager and confirm we get a different id
    idManager = createIdManager(originalFid, MOCK_ARBITER_DISABLED);
    String secondUuid = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotSame(secondUuid, firstUuid);
    assertEquals(secondUuid, getInstallIds(idManager).getCrashlyticsInstallId());
    assertEquals(
        secondUuid,
        getInstallIds(createIdManager(null, MOCK_ARBITER_DISABLED)).getCrashlyticsInstallId());
    // Check that we cached an synthetic FID
    final SharedPreferences prefs = CommonUtils.getSharedPrefs(getContext());
    String cachedFid = prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null);
    assertTrue(IdManager.isSyntheticFid(cachedFid));

    // re-enable data collection
    idManager = createIdManager(originalFid, MOCK_ARBITER_ENABLED);
    String thirdUuid = getInstallIds(idManager).getCrashlyticsInstallId();
    assertNotSame(thirdUuid, firstUuid);
    assertNotSame(thirdUuid, secondUuid);
    assertEquals(thirdUuid, getInstallIds(idManager).getCrashlyticsInstallId());
    assertEquals(
        thirdUuid,
        getInstallIds(createIdManager(originalFid, MOCK_ARBITER_ENABLED))
            .getCrashlyticsInstallId());
    // The cached ID should be back to the original
    cachedFid = prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null);
    assertEquals(cachedFid, originalFid);
  }

  /** Get the install ids on the common worker. */
  private InstallIds getInstallIds(IdManager idManager) throws Exception {
    return Tasks.await(crashlyticsWorkers.common.submit(idManager::getInstallIds));
  }
}
