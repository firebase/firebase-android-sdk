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
import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.Reader;

@AutoValue
public abstract class LogResponse {
  private static final String LOG_TAG = "LogResponseInternal";

  /** Client should wait for next_request_wait_millis before sending the next log request. */
  public abstract long getNextRequestWaitMillis();

  static LogResponse create(long nextRequestWaitMillis) {
    return new AutoValue_LogResponse(nextRequestWaitMillis);
  }

  @NonNull
  public static LogResponse fromJson(@NonNull Reader reader) throws IOException {
    JsonReader jsonReader = new JsonReader(reader);
    try {
      jsonReader.beginObject();
      while (jsonReader.hasNext()) {
        String name = jsonReader.nextName();
        if (name.equals("nextRequestWaitMillis")) {
          if (jsonReader.peek() == JsonToken.STRING) {
            return LogResponse.create(Long.parseLong(jsonReader.nextString()));
          } else {
            return LogResponse.create(jsonReader.nextLong());
          }
        }
        jsonReader.skipValue();
      }
      throw new IOException("Response is missing nextRequestWaitMillis field.");
    } finally {
      jsonReader.close();
    }
  }
}
