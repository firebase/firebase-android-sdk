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

/** The result of calling a HttpsCallableReference function. */
public class HttpsCallableResult {
  // The actual result data, as generic types decoded from JSON.
  private final Object data;

  HttpsCallableResult(Object data) {
    this.data = data;
  }

  /**
   * Returns the data that was returned from the Callable HTTPS trigger.
   *
   * <p>The data is in the form of native Java objects. For example, if your trigger returned an
   * array, this object would be a List<Object>. If your trigger returned a JavaScript object with
   * keys and values, this object would be a Map<String, Object>.
   */
  @Nullable
  public Object getData() {
    return data;
  }
}
