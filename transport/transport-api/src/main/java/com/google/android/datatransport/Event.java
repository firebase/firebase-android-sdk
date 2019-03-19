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

package com.google.android.datatransport;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Event<T> {
  public abstract T getPayload();

  public abstract Priority getPriority();

  public static <T> Event<T> ofData(T payload, Priority priority) {
    return new AutoValue_Event<>(payload, priority);
  }

  public static <T> Event<T> ofTelemetry(T value) {
    return new AutoValue_Event<>(value, Priority.DEFAULT);
  }
}
