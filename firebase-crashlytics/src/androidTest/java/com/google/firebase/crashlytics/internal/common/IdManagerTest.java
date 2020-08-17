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

  private IdManager createIdManager(String instanceId) {
    FirebaseInstallationsApi iid = mock(FirebaseInstallationsApi.class);
    when(iid.getId()).thenReturn(Tasks.forResult(instanceId));
    return new IdManager(getContext(), getContext().getPackageName(), iid);
  }

  public void testCreateUUID() {
    final String fid = "test_fid";
    final IdManager idManager = createIdManager(fid);
    final String installId = idManager.getCrashlyticsInstallId();
    assertNotNull(installId);

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertNull(prefs.getString(IdManager.PREFKEY_ADVERTISING_ID, null));
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(installId, idManager.getCrashlyticsInstallId());
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

    final IdManager idManager = new IdManager(getContext(), getContext().getPackageName(), fis);
    final String actualInstallId = idManager.getCrashlyticsInstallId();
    assertNotNull(actualInstallId);
    assertEquals(expectedInstallId, actualInstallId);
  }

  public void testInstanceIdChanges() {
    // Set up the initial state with a valid iid and uuid.
    final String oldUuid = "old_uuid";
    final String newFid = "new_test_fid";
    prefs
        .edit()
        .putString(IdManager.PREFKEY_INSTALLATION_UUID, oldUuid)
        .putString(IdManager.PREFKEY_FIREBASE_IID, "old_test_fid")
        .apply();

    // Initialize the manager with a different FID.
    IdManager idManager = createIdManager(newFid);

    String installId = idManager.getCrashlyticsInstallId();
    assertNotNull(installId);
    assertFalse(installId.equals(oldUuid));

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertEquals(newFid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(installId, idManager.getCrashlyticsInstallId());
  }

  public void testInstanceIdDoesntChange() {
    final String oldUuid = "test_uuid";
    final String fid = "test_fid";
    // Set up the initial state with a valid iid and uuid.
    prefs
        .edit()
        .putString(IdManager.PREFKEY_INSTALLATION_UUID, oldUuid)
        .putString(IdManager.PREFKEY_FIREBASE_IID, fid)
        .apply();

    // Initialize the manager with the same IID.
    IdManager idManager = createIdManager(fid);

    String installId = idManager.getCrashlyticsInstallId();
    assertNotNull(installId);

    // Test that the UUID didn't change.
    assertEquals(oldUuid, installId);

    assertEquals(installId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));

    // subsequent calls should return the same id
    assertEquals(oldUuid, idManager.getCrashlyticsInstallId());
  }

  public void testLegacyInstanceIdMigration() {
    // Pre-Firebase versions of Crashlytics stored the installation ID in a different preference
    // store. This test verifies that the legacy ID, if it exists, is migrated to the new
    // preference store and returned by the IdManager. It also verifies that the legacy ID is
    // then removed from the legacy preference store.
    final String legacyId = "legacy_uuid";
    final String fid = "new_fid";
    legacyPrefs
        .edit()
        .putString(IdManager.PREFKEY_LEGACY_INSTALLATION_UUID, legacyId)
        .putString(IdManager.PREFKEY_ADVERTISING_ID, "test_adid")
        .apply();

    final IdManager idManager = createIdManager(fid);
    assertEquals(legacyId, idManager.getCrashlyticsInstallId());
    assertEquals(fid, prefs.getString(IdManager.PREFKEY_FIREBASE_IID, null));
    assertEquals(legacyId, prefs.getString(IdManager.PREFKEY_INSTALLATION_UUID, null));
    assertFalse(legacyPrefs.contains(IdManager.PREFKEY_LEGACY_INSTALLATION_UUID));

    // The legacy SDK would cache the ad id, so that the installation ID could be rotated when the
    // ad id changed. Crashlytics now rotates the installation ID based on the Firebase Install Id,
    // so we verify that the ad id is removed from the legacy preference store:
    assertFalse(legacyPrefs.contains(IdManager.PREFKEY_ADVERTISING_ID));

    // subsequent calls should return the same id
    assertEquals(legacyId, idManager.getCrashlyticsInstallId());
  }
}
