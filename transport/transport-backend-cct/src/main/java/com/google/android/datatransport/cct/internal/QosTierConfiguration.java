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
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.io.IOException;

@AutoValue
abstract class QosTierConfiguration {
  public enum QosTier {
    DEFAULT(0),
    UNMETERED_ONLY(1),
    UNMETERED_OR_DAILY(2),
    FAST_IF_RADIO_AWAKE(3),
    NEVER(4);

    private final int value;

    QosTier(int value) {
      this.value = value;
    }
  }

  /** Log source identifier as a String. [Preferred] */
  @Nullable
  abstract String getLogSourceName();

  /** Log source identifier as a number. */
  abstract int getLogSource();

  abstract QosTier getQosTier();

  static Builder builder() {
    return new AutoValue_QosTierConfiguration.Builder()
        .setQosTier(QosTier.DEFAULT)
        .setLogSource(-1);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setLogSourceName(String value);

    abstract Builder setLogSource(int value);

    abstract Builder setQosTier(QosTier value);

    abstract QosTierConfiguration build();
  }

  @Nullable
  static QosTierConfiguration fromJsonReader(JsonReader jsonReader) throws IOException {
    Builder builder = builder();
    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      if (name.equals("log_source_name")) {
        builder.setLogSourceName(jsonReader.nextString());
      } else if (name.equals("log_source")) {
        builder.setLogSource(jsonReader.nextInt());
      } else if (name.equals("qos_tier")) {
        // TODO: double check if this is true.
        builder.setQosTier(QosTier.valueOf(jsonReader.nextString()));
      }
    }
    jsonReader.endObject();
    return builder.build();
  }
}
