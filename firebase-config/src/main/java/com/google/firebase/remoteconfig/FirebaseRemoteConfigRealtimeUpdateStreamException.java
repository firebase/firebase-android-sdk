// Copyright 2022 Google LLC
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

/**
 * A Firebase Remote Config issue that is caused by the stream component of Realtime config updates.
 *
 * @author Quan Pham
 */
public class FirebaseRemoteConfigRealtimeUpdateStreamException
    extends FirebaseRemoteConfigException {
  /** Creates a Firebase Remote Config Realtime stream exception with the given message. */
  public FirebaseRemoteConfigRealtimeUpdateStreamException(@NonNull String detailMessage) {
    super(detailMessage);
  }

  /**
   * Creates a Firebase Remote Config Realtime stream exception with the given message and cause.
   */
  public FirebaseRemoteConfigRealtimeUpdateStreamException(
      @NonNull String detailMessage, @Nullable Throwable cause) {
    super(detailMessage, cause);
  }
}
