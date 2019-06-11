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

package com.google.firebase.firestore.testutil.provider;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.firestore.R;

/**
 * Provides locations of production Firestore and Firebase Rules.
 *
 * <p>See the documentation for providers, providers.md.
 */
public final class FirestoreProvider {

  private final Context context;

  public FirestoreProvider() {
    this(ApplicationProvider.getApplicationContext());
  }

  public FirestoreProvider(Context context) {
    this.context = context;
  }

  public String firestoreHost() {
    return "firestore.googleapis.com";
  }

  public String projectId() {
    return context.getString(R.string.project_id);
  }
}
