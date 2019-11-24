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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AutoValue
public abstract class QosTiersOverride {
  /**
   * Quality of Service tiers enforced by server for overriding client qos_tier setting in event
   * logging. This usually happens when server is burdened with fast qos tiers.
   */
  @NonNull
  public abstract List<QosTierConfiguration> getQosTierConfigurations();

  /** The fingerprint of the qos_tier_configuration field. */
  public abstract long getQosTierFingerprint();

  static Builder builder() {
    return new AutoValue_QosTiersOverride.Builder()
        .setQosTierConfigurations(Collections.emptyList());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setQosTierFingerprint(long value);

    abstract Builder setQosTierConfigurations(@NonNull List<QosTierConfiguration> value);

    abstract QosTiersOverride build();
  }

  @Nullable
  static QosTiersOverride fromJsonReader(JsonReader jsonReader) throws IOException {
    Builder builder = builder();
    List<QosTierConfiguration> configurations = new ArrayList<>();
    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      if (name.equals("qos_tier_fingerprint")) {
        builder.setQosTierFingerprint(jsonReader.nextLong());
      } else if (name.equals("qos_tier_configuration")) {
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {
          QosTierConfiguration configuration = QosTierConfiguration.fromJsonReader(jsonReader);
          if (configuration != null) {
            configurations.add(configuration);
          }
        }
        builder.setQosTierConfigurations(configurations);
        jsonReader.endArray();
      }
    }
    jsonReader.endObject();
    return builder.build();
  }
}
