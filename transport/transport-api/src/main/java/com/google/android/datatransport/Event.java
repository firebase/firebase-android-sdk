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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Event<T> {
  @Nullable
  public abstract Integer getCode();

  public abstract T getPayload();

  public abstract Priority getPriority();

  public static <T> Event<T> ofData(int code, T payload) {
    return new AutoValue_Event<>(code, payload, Priority.DEFAULT);
  }

  public static <T> Event<T> ofData(T payload) {
    return new AutoValue_Event<>(null, payload, Priority.DEFAULT);
  }

  public static <T> Event<T> ofTelemetry(int code, T value) {
    return new AutoValue_Event<>(code, value, Priority.VERY_LOW);
  }

  public static <T> Event<T> ofTelemetry(T value) {
    return new AutoValue_Event<>(null, value, Priority.VERY_LOW);
  }

  public static <T> Event<T> ofUrgent(int code, T value) {
    return new AutoValue_Event<>(code, value, Priority.HIGHEST);
  }

  public static <T> Event<T> ofUrgent(T value) {
    return new AutoValue_Event<>(null, value, Priority.HIGHEST);
  }
}
