// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.auth;

import androidx.annotation.NonNull;
import com.google.firebase.firestore.util.Listener;

public abstract class AppCheckTokenProvider {
  /**
   * Returns the latest AppCheck token. This can be null (if the task of retrieving the token has
   * not completed yet), and can be an empty string (if AppCheck is not available).
   */
  public abstract String getCurrentAppCheckToken();

  /** Registers a listener that will be notified when AppCheck token changes. */
  public abstract void setChangeListener(@NonNull Listener<String> changeListener);
}
