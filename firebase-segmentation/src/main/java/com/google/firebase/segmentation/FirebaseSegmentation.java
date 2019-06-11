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

package com.google.firebase.segmentation;

import androidx.annotation.NonNull;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;

/** Entry point of Firebase Segmentation SDK. */
public class FirebaseSegmentation {

  private final FirebaseApp firebaseApp;
  private final FirebaseInstanceId firebaseInstanceId;

  FirebaseSegmentation(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstanceId = FirebaseInstanceId.getInstance(firebaseApp);
  }

  /**
   * Returns the {@link FirebaseSegmentation} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseSegmentation} instance
   */
  @NonNull
  public static FirebaseSegmentation getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseSegmentation} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseSegmentation} instance
   */
  @NonNull
  public static FirebaseSegmentation getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(FirebaseSegmentation.class);
  }

  Task<Void> setCustomInstallationId(String customInstallationId) {
    return Tasks.forResult(null);
  }
}
