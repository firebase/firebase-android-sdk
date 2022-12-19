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

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity opened through Deep Link when returning from web signIn flow. SignIn task is successful
 * when SignInResultActivity is created.
 */
public class SignInResultActivity extends AppCompatActivity {
  private static final String TAG = "SignInResultActivity";

  @Override
  public void onCreate(@NonNull Bundle savedInstanceBundle) {
    super.onCreate(savedInstanceBundle);
    LogWrapper.v(TAG, "The tester is signing in");
    // While this does not appear to be achieving much, handling the redirect in this way
    // ensures that we can remove the browser tab from the back stack. See the documentation
    // on AuthorizationManagementActivity for more details.
    finish();
  }
}
