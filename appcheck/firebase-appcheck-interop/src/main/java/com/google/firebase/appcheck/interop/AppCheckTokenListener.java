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

package com.google.firebase.appcheck.interop;

import androidx.annotation.NonNull;
import com.google.firebase.appcheck.AppCheckTokenResult;

/**
 * Listener that is triggered when the {@link AppCheckTokenResult} changes. To be used when you need
 * the {@link AppCheckTokenResult} to remain valid at all times.
 */
public interface AppCheckTokenListener {

  /**
   * This method gets invoked on the UI thread on changes to the token state. Does not trigger on
   * token expiry.
   */
  void onAppCheckTokenChanged(@NonNull AppCheckTokenResult appCheckTokenResult);
}
