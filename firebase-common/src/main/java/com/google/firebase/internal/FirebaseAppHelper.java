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

package com.google.firebase.internal;

import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseApp.IdTokenListener;
import com.google.firebase.auth.GetTokenResult;

/**
 * A helper to expose obfuscated methods on FirebaseApp in an obfuscated manner for libraries in
 * different library groups.
 *
 * @deprecated Use {@link com.google.firebase.auth.internal.InternalAuthProvider} in
 *     firebase-auth-interop.
 * @hide
 */
@Deprecated
@KeepForSdk
public class FirebaseAppHelper {
  /**
   * Exposes getToken on FirebaseApp in an unobfuscated manner.
   *
   * @hide
   */
  @KeepForSdk
  public static Task<GetTokenResult> getToken(FirebaseApp app, boolean forceRefresh) {
    return app.getToken(forceRefresh);
  }

  /**
   * Exposes addIdTokenListener on FirebaseApp in an unobfuscated manner.
   *
   * @hide
   */
  @KeepForSdk
  public static void addIdTokenListener(FirebaseApp app, IdTokenListener listener) {
    app.addIdTokenListener(listener);
  }
  /**
   * Exposes removeIdTokenListener on FirebaseApp in an unobfuscated manner.
   *
   * @hide
   */
  @KeepForSdk
  public static void removeIdTokenListener(FirebaseApp app, IdTokenListener listener) {
    app.removeIdTokenListener(listener);
  }

  /**
   * Exposes getUid on FirebaseApp in an unobfuscated manner.
   *
   * @hide
   */
  @KeepForSdk
  public static String getUid(FirebaseApp app) throws FirebaseApiNotAvailableException {
    return app.getUid();
  }
}
