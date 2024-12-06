// Copyright 2024 Google LLC
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

package com.google.firebase.firestore.local;

import androidx.annotation.NonNull;
import com.google.protobuf.ByteString;

/**
 * General purpose cache for global values.
 *
 * <p>Global state that cuts across components should be saved here. Following are contained herein:
 *
 * <p>`sessionToken` tracks server interaction across Listen and Write streams. This facilitates cache
 * synchronization and invalidation.
 */
interface GlobalsCache {

  @NonNull
  ByteString getSessionsToken();

  void setSessionToken(@NonNull ByteString value);
}
