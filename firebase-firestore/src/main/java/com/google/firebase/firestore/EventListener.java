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

package com.google.firebase.firestore;

import com.google.firebase.annotations.PublicApi;
import javax.annotation.Nullable;

/** An interface for event listeners. */
@PublicApi
public interface EventListener<T> {

  /**
   * onEvent will be called with the new value or the error if an error occurred. It's guaranteed
   * that exactly one of value or error will be non-null.
   *
   * @param value The value of the event. null if there was an error.
   * @param error The error if there was error. null otherwise.
   */
  @PublicApi
  void onEvent(@Nullable T value, @Nullable FirebaseFirestoreException error);
}
