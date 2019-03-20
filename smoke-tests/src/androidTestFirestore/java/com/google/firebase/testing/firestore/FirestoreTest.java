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

package com.google.firebase.testing.firestore;

import android.util.Log;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.testing.common.SmokeTestBase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FirestoreTest extends SmokeTestBase {

  private static final String TAG = "FirestoreTest";

  @Rule
  public final ActivityTestRule<FirestoreActivity> atr =
      new ActivityTestRule<>(FirestoreActivity.class);

  @Test
  public void runSmokeTests() throws Exception {
    Log.d(TAG, "Initializing activity.");
    FirestoreActivity instance = atr.getActivity();

    // Delegate to base class.
    runSmokeTests(instance);
  }
}
