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

package com.google.firebase.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.database.android.AndroidAuthTokenProvider;
import com.google.firebase.database.core.AuthTokenProvider;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoInfo;
import java.util.HashMap;
import java.util.Map;

class FirebaseDatabaseComponent {
  /**
   * A map of RepoInfo to FirebaseDatabase instance.
   *
   * <p>TODO: This serves a duplicate purpose as RepoManager. We should clean up. TODO: We should
   * maybe be conscious of leaks and make this a weak map or similar but we have a lot of work to do
   * to allow FirebaseDatabase/Repo etc. to be GC'd.
   */
  private final Map<RepoInfo, FirebaseDatabase> instances = new HashMap<>();

  private final FirebaseApp app;
  private final AuthTokenProvider authProvider;

  FirebaseDatabaseComponent(@NonNull FirebaseApp app, @Nullable InternalAuthProvider authProvider) {
    this.app = app;

    if (authProvider != null) {
      this.authProvider = AndroidAuthTokenProvider.forAuthenticatedAccess(authProvider);
    } else {
      this.authProvider = AndroidAuthTokenProvider.forUnauthenticatedAccess();
    }
  }

  /** Provides instances of Firebase Database for the given RepoInfo */
  @NonNull
  synchronized FirebaseDatabase get(RepoInfo repo) {
    FirebaseDatabase database = instances.get(repo);
    if (database == null) {
      DatabaseConfig config = new DatabaseConfig();
      // If this is the default app, don't set the session persistence key so that we use our
      // default ("default") instead of the FirebaseApp default ("[DEFAULT]") so that we
      // preserve the default location used by the legacy Firebase SDK.
      if (!app.isDefaultApp()) {
        config.setSessionPersistenceKey(app.getName());
      }
      config.setFirebaseApp(app);
      config.setAuthTokenProvider(authProvider);

      database = new FirebaseDatabase(app, repo, config);
      instances.put(repo, database);
    }
    return database;
  }
}
