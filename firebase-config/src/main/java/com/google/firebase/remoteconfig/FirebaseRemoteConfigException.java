// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseException;

/** Base class for {@link FirebaseRemoteConfig} exceptions. */
public class FirebaseRemoteConfigException extends FirebaseException {
  /** Creates a Firebase Remote Config exception with the given message. */
  public FirebaseRemoteConfigException(@NonNull String detailMessage) {
    super(detailMessage);
  }

  /** Creates a Firebase Remote Config exception with the given message and cause. */
  public FirebaseRemoteConfigException(@NonNull String detailMessage, @Nullable Throwable cause) {
    super(detailMessage, cause);
  }
}
