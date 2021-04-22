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

package com.google.firebase.appcheck.debug.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;
import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Internal class used to persist debug secrets. Uses {@link android.content.SharedPreferences} in
 * {@code Context.MODE_PRIVATE} to store the secrets.
 */
public class StorageHelper {

  @VisibleForTesting
  static final String PREFS_TEMPLATE = "com.google.firebase.appcheck.debug.store.%s";

  @VisibleForTesting
  static final String DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET";

  private final SharedPreferences sharedPreferences;

  public StorageHelper(@NonNull Context context, @NonNull String persistenceKey) {
    checkNotNull(context);
    checkNotEmpty(persistenceKey);
    String prefsName = String.format(PREFS_TEMPLATE, persistenceKey);
    this.sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
  }

  public void saveDebugSecret(@NonNull String debugSecret) {
    sharedPreferences.edit().putString(DEBUG_SECRET_KEY, debugSecret).apply();
  }

  @Nullable
  public String retrieveDebugSecret() {
    return sharedPreferences.getString(DEBUG_SECRET_KEY, null);
  }
}
