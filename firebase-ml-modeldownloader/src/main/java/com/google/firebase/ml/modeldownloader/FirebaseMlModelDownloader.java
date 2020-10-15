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
package com.google.firebase.ml.modeldownloader;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class FirebaseMlModelDownloader {

  private final FirebaseOptions firebaseOptions;

  FirebaseMlModelDownloader(FirebaseOptions firebaseOptions) {
    this.firebaseOptions = firebaseOptions;
  }

  /**
   * Returns the {@link FirebaseMlModelDownloader} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseMlModelDownloader} instance
   */
  @NonNull
  public static FirebaseMlModelDownloader getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseMlModelDownloader} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseMlModelDownloader} instance
   */
  @NonNull
  public static FirebaseMlModelDownloader getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(FirebaseMlModelDownloader.class);
  }

  /** Returns the nick name of the {@link FirebaseApp} of this {@link FirebaseMlModelDownloader} */
  @VisibleForTesting
  String getApplicationId() {
    return firebaseOptions.getApplicationId();
  }
}
