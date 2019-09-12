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

package com.google.firebase.provider;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;

/** Initializes Firebase APIs at app startup time. */
public class FirebaseInitProvider extends ContentProvider {

  private static final String TAG = "FirebaseInitProvider";

  /** Should match the {@link FirebaseInitProvider} authority if $androidId is empty. */
  @VisibleForTesting
  static final String EMPTY_APPLICATION_ID_PROVIDER_AUTHORITY =
      "com.google.firebase.firebaseinitprovider";

  @Override
  public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
    // super.attachInfo calls onCreate. Fail as early as possible.
    checkContentProviderAuthority(info);
    super.attachInfo(context, info);
  }

  /** Called before {@link Application#onCreate()}. */
  @Override
  public boolean onCreate() {
    if (FirebaseApp.initializeApp(getContext()) == null) {
      Log.i(TAG, "FirebaseApp initialization unsuccessful");
    } else {
      Log.i(TAG, "FirebaseApp initialization successful");
    }
    return false;
  }

  /**
   * Check that the content provider's authority does not use firebase-common's package name. If it
   * does, crash in order to alert the developer of the problem before they distribute the app.
   */
  private static void checkContentProviderAuthority(@NonNull ProviderInfo info) {
    Preconditions.checkNotNull(info, "FirebaseInitProvider ProviderInfo cannot be null.");
    if (EMPTY_APPLICATION_ID_PROVIDER_AUTHORITY.equals(info.authority)) {
      throw new IllegalStateException(
          "Incorrect provider authority in manifest. Most likely due to a missing "
              + "applicationId variable in application's build.gradle.");
    }
  }

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] projection,
      @Nullable String selection,
      @Nullable String[] selectionArgs,
      @Nullable String sortOrder) {
    return null;
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    return null;
  }

  @Override
  public int delete(
      @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] selectionArgs) {
    return 0;
  }
}
