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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseAppLifecycleListener;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Multi-resource container for Cloud Firestore. */
class FirestoreMultiDbComponent
    implements FirebaseAppLifecycleListener, FirebaseFirestore.InstanceRegistry {

  /**
   * A static map from instance key to FirebaseFirestore instances. Instance keys are database
   * names.
   */
  private final Map<String, FirebaseFirestore> instances = new HashMap<>();

  private final FirebaseApp app;
  private final Context context;
  private final InternalAuthProvider authProvider;
  private final GrpcMetadataProvider metadataProvider;

  FirestoreMultiDbComponent(
      @NonNull Context context,
      @NonNull FirebaseApp app,
      @Nullable InternalAuthProvider authProvider,
      @Nullable GrpcMetadataProvider metadataProvider) {
    this.context = context;
    this.app = app;
    this.authProvider = authProvider;
    this.metadataProvider = metadataProvider;
    this.app.addLifecycleEventListener(this);
  }

  /** Provides instances of Cloud Firestore for given database IDs. */
  @NonNull
  synchronized FirebaseFirestore get(@NonNull String databaseId) {
    FirebaseFirestore firestore = instances.get(databaseId);
    if (firestore == null) {
      firestore =
          FirebaseFirestore.newInstance(
              context, app, authProvider, databaseId, this, metadataProvider);
      instances.put(databaseId, firestore);
    }
    return firestore;
  }

  /**
   * Remove the instance of a given database ID from this component, such that if {@link
   * FirestoreMultiDbComponent#get(String)} is called again with the same name, a new instance of
   * {@link FirebaseFirestore} is created.
   *
   * <p>It is a no-op if there is no instance associated with the given database name.
   */
  @Override
  public synchronized void remove(@NonNull String databaseId) {
    instances.remove(databaseId);
  }

  @Override
  public synchronized void onDeleted(String firebaseAppName, FirebaseOptions options) {
    // Shut down all database instances and remove them from the registry map. To avoid
    // ConcurrentModificationException, make a copy of the entries instead of using an iterator from
    // the `instances` map directly.
    for (Map.Entry<String, FirebaseFirestore> entry : new ArrayList<>(instances.entrySet())) {
      entry.getValue().terminate();
      hardAssert(
          !instances.containsKey(entry.getKey()),
          "terminate() should have removed its entry from `instances` for key: %s",
          entry.getKey());
    }
  }
}
