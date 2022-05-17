// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.impl;

import android.content.Context;
import android.content.SharedPreferences;

/** Class that handles storage for App Distribution SignIn persistence. */
class SignInStorage {

  private static final String SIGNIN_PREFERENCES_NAME = "FirebaseAppDistributionSignInStorage";
  private static final String SIGNIN_TAG = "firebase_app_distribution_signin";

  private final SharedPreferences signInSharedPreferences;

  SignInStorage(Context applicationContext) {
    this.signInSharedPreferences =
        applicationContext.getSharedPreferences(SIGNIN_PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  void setSignInStatus(boolean testerSignedIn) {
    this.signInSharedPreferences.edit().putBoolean(SIGNIN_TAG, testerSignedIn).apply();
  }

  boolean getSignInStatus() {
    return signInSharedPreferences.getBoolean(SIGNIN_TAG, false);
  }
}
