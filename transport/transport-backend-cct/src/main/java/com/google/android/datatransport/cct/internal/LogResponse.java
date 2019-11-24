// Copyright 2019 Google LLC
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

package com.google.android.datatransport.cct.internal;

import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.StringReader;

@AutoValue
public abstract class LogResponse {
  /** Client should wait for next_request_wait_millis before sending the next log request. */
  public abstract long getNextRequestAwaitMillis();

  /**
   * Quality of Service tiers enforced by server for overriding client qos_tier setting in event
   * logging.
   */
  @Nullable
  public abstract QosTiersOverride getQosTiersOverride();

  static Builder builder() {
    return new AutoValue_LogResponse.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setNextRequestAwaitMillis(long value);

    abstract Builder setQosTiersOverride(QosTiersOverride value);

    abstract LogResponse build();
  }

  @Nullable
  public static LogResponse fromJson(@Nullable String input) {
    if (input == null) {
      return null;
    }

    JsonReader jsonReader = new JsonReader(new StringReader(input));
    Builder builder = builder();
    try {
      jsonReader.beginObject();
      while (jsonReader.hasNext()) {
        String name = jsonReader.nextName();
        if (name.equals("next_request_wait_millis")) {
          if (jsonReader.peek() == JsonToken.STRING) {
            builder.setNextRequestAwaitMillis(Long.parseLong(jsonReader.nextString()));
          } else {
            builder.setNextRequestAwaitMillis(jsonReader.nextLong());
          }
        } else if (name.equals("qos_tier")) {
          // TODO: fix
          builder.setQosTiersOverride(QosTiersOverride.fromJsonReader(jsonReader));
        }
      }
      return builder.build();
    } catch (IOException e) {
      // This should never happen(TM)
      return null;
    }
  }
}
