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

package com.google.firebase.appcheck;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Class to hold the result emitted by a Firebase App Check service application verification
 * attempt. The result will always contain a token, which will either be a valid raw JWT attesting
 * application identity, or a dummy value. The result may optionally contain an {@link Exception} if
 * application verification does not succeed.
 */
public abstract class AppCheckTokenResult {

  /**
   * Returns the raw JWT attesting to this application’s identity. May be a dummy value, if
   * application verification does not succeed.
   */
  @NonNull
  public abstract String getToken();

  /**
   * Returns the {@link Exception} if the {@link
   * com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider} failed to obtain a token.
   */
  @Nullable
  public abstract Exception getError();
}
