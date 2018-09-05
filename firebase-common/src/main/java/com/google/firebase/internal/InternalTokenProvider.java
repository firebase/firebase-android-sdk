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

import android.support.annotation.Nullable;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.GetTokenResult;

/**
 * Provides a way for {@link com.google.firebase.FirebaseApp} to get an access token if there exists
 * a logged in user.
 *
 * @deprecated Use {@link com.google.firebase.auth.internal.InternalAuthProvider} from
 *     firebase-auth-interop.
 * @hide
 */
@Deprecated
@KeepForSdk
public interface InternalTokenProvider {

  /**
   * Fetch a valid STS Token.
   *
   * @param forceRefresh force refreshes the token. Should only be set to <code>true</code> if the
   *     token is invalidated out of band.
   * @return a {@link Task}
   * @hide
   */
  @KeepForSdk
  Task<GetTokenResult> getAccessToken(boolean forceRefresh);

  /**
   * A synchronous way to get the current Firebase User's UID.
   *
   * @return the String representation of the UID. Returns null if FirebaseAuth is not linked, or if
   *     there is no currently signed-in user.
   * @hide
   */
  @Nullable
  @KeepForSdk
  String getUid();
}
