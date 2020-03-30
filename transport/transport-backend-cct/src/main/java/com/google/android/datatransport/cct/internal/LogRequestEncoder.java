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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import java.io.IOException;

public final class LogRequestEncoder implements ObjectEncoder<AutoValue_LogRequest> {

  /**
   * Encodes the log source into the context.
   *
   * @throws EncodingException if neither LogSourceName or LogSource is set.
   */
  private void encodeLogSource(
      @Nullable AutoValue_LogRequest obj, @NonNull ObjectEncoderContext objectEncoderContext)
      throws IOException {
    if (obj.getLogSourceName() != null) {
      objectEncoderContext.add("logSourceName", obj.getLogSourceName());
    } else if (obj.getLogSource() != Integer.MIN_VALUE) {
      objectEncoderContext.add("logSource", obj.getLogSource());
    } else {
      throw new EncodingException("Log request must have either LogSourceName or LogSource");
    }
  }

  @Override
  public void encode(
      @Nullable AutoValue_LogRequest obj, @NonNull ObjectEncoderContext objectEncoderContext)
      throws IOException {
    objectEncoderContext
        .add("requestTimeMs", obj.getRequestTimeMs())
        .add("requestUptimeMs", obj.getRequestUptimeMs());
    if (obj.getClientInfo() != null) {
      objectEncoderContext.add("clientInfo", obj.getClientInfo());
    }
    encodeLogSource(obj, objectEncoderContext);
    if (!obj.getLogEvents().isEmpty()) {
      objectEncoderContext.add("logEvent", obj.getLogEvents());
    }
  }
}
