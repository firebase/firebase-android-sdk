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

package com.google.firebase.functions;

import androidx.annotation.Nullable;

/** The metadata about the client that should automatically be included in function calls. */
class HttpsCallableContext {
  @Nullable private final String authToken;
  @Nullable private final String instanceIdToken;

  HttpsCallableContext(@Nullable String authToken, @Nullable String instanceIdToken) {
    this.authToken = authToken;
    this.instanceIdToken = instanceIdToken;
  }

  @Nullable
  public String getAuthToken() {
    return authToken;
  }

  @Nullable
  public String getInstanceIdToken() {
    return instanceIdToken;
  }
}
