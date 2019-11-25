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

package com.google.android.datatransport.cct.internal.encoders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.datatransport.cct.internal.LogEvent;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import java.io.IOException;

public final class LogEventEncoder implements ObjectEncoder<LogEvent> {

  @Override
  public void encode(@Nullable LogEvent obj, @NonNull ObjectEncoderContext objectEncoderContext)
      throws EncodingException, IOException {
    objectEncoderContext
        .add("EventTimeMs", obj.getEventTimeMs())
        .add("EventUptimeMs", obj.getEventUptimeMs())
        .add("TimezoneOffsetSeconds", obj.getTimezoneOffsetSeconds());
    if (obj.getSourceExtension() != null) {
      objectEncoderContext.add("SourceExtension", obj.getSourceExtension());
    }
    if (obj.getSourceExtensionJsonProto3Bytes() != null) {
      objectEncoderContext.add(
          "SourceExtensionJsonProto3Bytes", obj.getSourceExtensionJsonProto3Bytes());
    }
    if (obj.getEventCode() != Integer.MIN_VALUE) {
      objectEncoderContext.add("EventCode", obj.getEventCode());
    }
    if (obj.getNetworkConnectionInfo() != null) {
      objectEncoderContext.add("NetworkConnectionInfo", obj.getNetworkConnectionInfo());
    }
  }
}
