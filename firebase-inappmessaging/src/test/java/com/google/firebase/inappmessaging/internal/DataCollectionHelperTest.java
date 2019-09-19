// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.os.Bundle;
import com.google.firebase.DataCollectionDefaultChange;
import com.google.firebase.FirebaseApp;
import com.google.firebase.events.Event;
import com.google.firebase.events.EventHandler;
import com.google.firebase.events.Subscriber;
import com.google.firebase.iid.FirebaseInstanceId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class DataCollectionHelperTest {

  @Mock private Application application;
  @Mock private FirebaseApp firebaseApp;
  @Mock private FirebaseInstanceId firebaseInstanceId;
  @Mock private SharedPreferencesUtils sharedPreferencesUtils;

  @Mock private Subscriber subscriber;

  private DataCollectionHelper dataCollectionHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(firebaseInstanceId.getToken()).thenReturn("token");
    when(firebaseInstanceId.getId()).thenReturn("id");
  }

  @Test
  public void isAutomaticDataCollectionEnabled_defaultsToTrue() {
    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(false);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);
    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);

    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isTrue();
    verify(firebaseInstanceId, atLeastOnce()).getToken();
  }

  @Test
  public void setAutomaticDataCollectionEnabled_updatesSharedPreferences() {
    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(false);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);

    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);
    dataCollectionHelper.setAutomaticDataCollectionEnabled(false);
    verify(sharedPreferencesUtils, times(1))
        .setBooleanPreference(DataCollectionHelper.AUTO_INIT_PREFERENCES, false);
  }

  @Test
  public void isAutomaticDataCollectionEnabled_honorsManifestFlag() {
    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(false);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(true);
    when(sharedPreferencesUtils.getBooleanManifestValue(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED, true))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);

    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);
    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isFalse();
    verify(firebaseInstanceId, times(0)).getToken();
  }

  @Test
  public void isAutomaticDataCollectionEnabled_prefOverridesManifest() {
    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(true);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);

    // Manifest is false, preferences is true
    when(sharedPreferencesUtils.getBooleanPreference(
            DataCollectionHelper.AUTO_INIT_PREFERENCES, true))
        .thenReturn(true);

    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);

    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isTrue();
    verify(firebaseInstanceId, times(1)).getToken();
    verify(sharedPreferencesUtils, never())
        .isManifestSet(DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED);
    verify(sharedPreferencesUtils, never())
        .getBooleanManifestValue(DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED, true);
  }

  @Test
  public void isAutomaticDataCollectionEnabled_honorsGlobalFlag_productEnablePriority() {
    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(true);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(true);
    when(sharedPreferencesUtils.getBooleanPreference(
            DataCollectionHelper.AUTO_INIT_PREFERENCES, true))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);

    // These cases follow the order of precedence:
    // product flag > product manifest > global

    // Case 1:
    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);
    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isFalse();

    verify(sharedPreferencesUtils, never())
        .getBooleanManifestValue(DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED, true);
  }

  @Test
  public void isAutomaticDataCollectionEnabled_honorsGlobalFlag_productManifestPriority() {
    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(false);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(true);
    when(sharedPreferencesUtils.getBooleanManifestValue(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED, true))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(true);
    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);

    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isFalse();
    verify(sharedPreferencesUtils, never())
        .getBooleanPreference(DataCollectionHelper.AUTO_INIT_PREFERENCES, true);
  }

  @Test
  public void isAutomaticDataCollectionEnabled_honorsGlobalFlag_defaultsToGlobal()
      throws Exception {

    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(false);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(false);

    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, subscriber);

    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isFalse();
    verify(sharedPreferencesUtils, never())
        .getBooleanPreference(DataCollectionHelper.AUTO_INIT_PREFERENCES, true);
    verify(sharedPreferencesUtils, never())
        .getBooleanManifestValue(DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED, true);
  }

  @Test
  public void isAutomaticDataCollectionEnabled_updatesOnDataCollectionDefaultChanges()
      throws Exception {
    // There are no overrides set by us - the only one we're 'honoring' is the dataCollectionDefault
    // from the global flag

    when(sharedPreferencesUtils.isPreferenceSet(DataCollectionHelper.AUTO_INIT_PREFERENCES))
        .thenReturn(false);
    when(sharedPreferencesUtils.isManifestSet(
            DataCollectionHelper.MANIFEST_METADATA_AUTO_INIT_ENABLED))
        .thenReturn(false);
    when(firebaseApp.isDataCollectionDefaultEnabled()).thenReturn(false);

    TestFirebaseEventSubscriber testFirebaseEventSubscriber = new TestFirebaseEventSubscriber();

    dataCollectionHelper =
        new DataCollectionHelper(
            firebaseApp, sharedPreferencesUtils, firebaseInstanceId, testFirebaseEventSubscriber);
    verify(firebaseInstanceId, times(0)).getToken();

    // Now let's turn on the global flag:

    assertThat(testFirebaseEventSubscriber.dataCollectionHandlers.size()).isEqualTo(1);
    testFirebaseEventSubscriber.notifySubscribers(new DataCollectionDefaultChange(true));

    assertThat(dataCollectionHelper.isAutomaticDataCollectionEnabled()).isTrue();
  }

  private static Bundle createNewBundle() {
    return new Bundle();
  }

  private class TestFirebaseEventSubscriber implements Subscriber {
    private List<EventHandler<DataCollectionDefaultChange>> dataCollectionHandlers;

    TestFirebaseEventSubscriber() {
      dataCollectionHandlers = new ArrayList<>();
    }

    @Override
    public <T> void subscribe(Class<T> type, Executor executor, EventHandler<? super T> handler) {
      // Not implemented/needed
    }

    @Override
    public <T> void subscribe(Class<T> type, EventHandler<? super T> handler) {
      dataCollectionHandlers.add((EventHandler<DataCollectionDefaultChange>) handler);
    }

    @Override
    public <T> void unsubscribe(Class<T> type, EventHandler<? super T> handler) {
      dataCollectionHandlers.remove(handler);
    }

    public void notifySubscribers(DataCollectionDefaultChange change) {
      dataCollectionHandlers.forEach(
          subscriber -> subscriber.handle(new Event(DataCollectionDefaultChange.class, change)));
    }
  }
}
