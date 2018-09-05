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

package com.google.firebase.firestore;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.internal.InternalAuthProvider;
import java.util.HashMap;
import java.util.Map;

/** Multi-resource container for Firestore. */
class FirestoreMultiDbComponent {
  /**
   * A static map from instance key to FirebaseFirestore instances. Instance keys are database
   * names.
   */
  private final Map<String, FirebaseFirestore> instances = new HashMap<>();

  private final FirebaseApp app;
  private final Context context;
  private final InternalAuthProvider authProvider;

  FirestoreMultiDbComponent(
      @NonNull Context context,
      @NonNull FirebaseApp app,
      @Nullable InternalAuthProvider authProvider) {
    this.context = context;
    this.app = app;
    this.authProvider = authProvider;
  }

  /** Provides instances of Firestore for given database names. */
  @NonNull
  synchronized FirebaseFirestore get(@NonNull String databaseName) {
    FirebaseFirestore firestore = instances.get(databaseName);
    if (firestore == null) {
      firestore = FirebaseFirestore.newInstance(context, app, authProvider, databaseName);
      instances.put(databaseName, firestore);
    }
    return firestore;
  }
}
