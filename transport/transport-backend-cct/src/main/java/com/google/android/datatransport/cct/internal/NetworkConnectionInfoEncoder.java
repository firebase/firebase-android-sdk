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
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import java.io.IOException;

public final class NetworkConnectionInfoEncoder
    implements ObjectEncoder<AutoValue_NetworkConnectionInfo> {
  @Override
  public void encode(
      @Nullable AutoValue_NetworkConnectionInfo obj,
      @NonNull ObjectEncoderContext objectEncoderContext)
      throws IOException {
    if (obj.getMobileSubtype() != null) {
      objectEncoderContext.add("mobileSubtype", obj.getMobileSubtype().name());
    }
    if (obj.getNetworkType() != null) {
      objectEncoderContext.add("networkType", obj.getNetworkType().name());
    }
  }
}
