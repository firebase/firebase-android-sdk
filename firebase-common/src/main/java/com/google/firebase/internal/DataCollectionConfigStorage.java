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

package com.google.firebase.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import com.google.firebase.DataCollectionDefaultChange;
import com.google.firebase.events.Event;
import com.google.firebase.events.Publisher;
import java.util.concurrent.atomic.AtomicBoolean;

/** Encapsulates data collection configuration. */
public class DataCollectionConfigStorage {
  private static final String FIREBASE_APP_PREFS = "com.google.firebase.common.prefs:";

  @VisibleForTesting
  public static final String DATA_COLLECTION_DEFAULT_ENABLED =
      "firebase_data_collection_default_enabled";

  private final Context applicationContext;
  private final SharedPreferences sharedPreferences;
  private final Publisher publisher;
  private final AtomicBoolean dataCollectionDefaultEnabled;

  public DataCollectionConfigStorage(
      Context applicationContext, String persistenceKey, Publisher publisher) {
    this.applicationContext = directBootSafe(applicationContext);
    this.sharedPreferences =
        applicationContext.getSharedPreferences(
            FIREBASE_APP_PREFS + persistenceKey, Context.MODE_PRIVATE);
    this.publisher = publisher;
    this.dataCollectionDefaultEnabled = new AtomicBoolean(readAutoDataCollectionEnabled());
  }

  private static Context directBootSafe(Context applicationContext) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
        || ContextCompat.isDeviceProtectedStorage(applicationContext)) {
      return applicationContext;
    }
    return ContextCompat.createDeviceProtectedStorageContext(applicationContext);
  }

  public boolean isEnabled() {
    return dataCollectionDefaultEnabled.get();
  }

  public void setEnabled(boolean enabled) {
    if (dataCollectionDefaultEnabled.compareAndSet(!enabled, enabled)) {
      sharedPreferences.edit().putBoolean(DATA_COLLECTION_DEFAULT_ENABLED, enabled).apply();

      publisher.publish(
          new Event<>(DataCollectionDefaultChange.class, new DataCollectionDefaultChange(enabled)));
    }
  }

  private boolean readAutoDataCollectionEnabled() {
    if (sharedPreferences.contains(DATA_COLLECTION_DEFAULT_ENABLED)) {
      return sharedPreferences.getBoolean(DATA_COLLECTION_DEFAULT_ENABLED, true);
    }
    try {
      PackageManager packageManager = applicationContext.getPackageManager();
      if (packageManager != null) {
        ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                applicationContext.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(DATA_COLLECTION_DEFAULT_ENABLED)) {
          return applicationInfo.metaData.getBoolean(DATA_COLLECTION_DEFAULT_ENABLED);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // This shouldn't happen since it's this app's package, but fall through to default if so.
    }
    return true;
  }
}
