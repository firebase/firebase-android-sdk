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

package com.google.firebase.appcheck.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;
import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.util.Logger;
import com.google.firebase.components.Lazy;

/**
 * Internal class used to persist {@link com.google.firebase.appcheck.AppCheckToken}s. Uses {@link
 * android.content.SharedPreferences} in {@code Context.MODE_PRIVATE} to store the tokens. Does not
 * keep a version code since we're ok with being unable to read a token state (since tokens only
 * have an hour TTL, we can just require a new token on app upgrade).
 */
public class StorageHelper {

  private static final Logger logger = new Logger(StorageHelper.class.getSimpleName());

  @VisibleForTesting static final String PREFS_TEMPLATE = "com.google.firebase.appcheck.store.%s";

  @VisibleForTesting static final String TOKEN_KEY = "com.google.firebase.appcheck.APP_CHECK_TOKEN";

  @VisibleForTesting static final String TOKEN_TYPE_KEY = "com.google.firebase.appcheck.TOKEN_TYPE";

  @VisibleForTesting
  enum TokenType {
    DEFAULT_APP_CHECK_TOKEN,
    UNKNOWN_APP_CHECK_TOKEN
  }

  private Lazy<SharedPreferences> sharedPreferences;

  public StorageHelper(@NonNull Context context, @NonNull String persistenceKey) {
    checkNotNull(context);
    checkNotEmpty(persistenceKey);
    String prefsName = String.format(PREFS_TEMPLATE, persistenceKey);
    this.sharedPreferences =
        new Lazy(() -> context.getSharedPreferences(prefsName, Context.MODE_PRIVATE));
  }

  public void saveAppCheckToken(@NonNull AppCheckToken appCheckToken) {
    if (appCheckToken instanceof DefaultAppCheckToken) {
      sharedPreferences
          .get()
          .edit()
          .putString(TOKEN_KEY, ((DefaultAppCheckToken) appCheckToken).serializeTokenToString())
          .putString(TOKEN_TYPE_KEY, TokenType.DEFAULT_APP_CHECK_TOKEN.name())
          .apply();
    } else {
      sharedPreferences
          .get()
          .edit()
          .putString(TOKEN_KEY, appCheckToken.getToken())
          .putString(TOKEN_TYPE_KEY, TokenType.UNKNOWN_APP_CHECK_TOKEN.name())
          .apply();
    }
  }

  @Nullable
  public AppCheckToken retrieveAppCheckToken() {
    String tokenType = sharedPreferences.get().getString(TOKEN_TYPE_KEY, null);
    String serializedToken = sharedPreferences.get().getString(TOKEN_KEY, null);
    if (tokenType == null || serializedToken == null) {
      return null;
    }
    try {
      switch (TokenType.valueOf(tokenType)) {
        case DEFAULT_APP_CHECK_TOKEN:
          return DefaultAppCheckToken.deserializeTokenFromJsonString(serializedToken);
        case UNKNOWN_APP_CHECK_TOKEN:
          return DefaultAppCheckToken.constructFromRawToken(serializedToken);
      }
    } catch (IllegalArgumentException e) {
      logger.e(
          "Failed to parse TokenType of stored token  with type ["
              + tokenType
              + "] with exception: "
              + e.getMessage());
      clearSharedPrefs();
      return null;
    }
    // Should be unreachable, but needed by the compiler:
    logger.e("Reached unreachable section in #retrieveAppCheckToken()");
    return null;
  }

  void clearSharedPrefs() {
    sharedPreferences.get().edit().remove(TOKEN_KEY).remove(TOKEN_TYPE_KEY).apply();
  }
}
