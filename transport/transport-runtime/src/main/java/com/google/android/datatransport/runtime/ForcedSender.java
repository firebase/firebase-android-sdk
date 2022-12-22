// Copyright 2022 Google LLC
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

package com.google.android.datatransport.runtime;

import android.annotation.SuppressLint;
import androidx.annotation.Discouraged;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.Transport;

@Discouraged(
    message =
        "TransportRuntime is not a realtime delivery system, don't use unless you absolutely must.")
public final class ForcedSender {
  @WorkerThread
  public static void sendBlocking(Transport<?> transport, Priority priority) {
    @SuppressLint("DiscouragedApi")
    TransportContext context = getTransportContextOrThrow(transport).withPriority(priority);
    TransportRuntime.getInstance().getUploader().logAndUpdateState(context, 1);
  }

  private static TransportContext getTransportContextOrThrow(Transport<?> transport) {
    if (transport instanceof TransportImpl) {
      return ((TransportImpl<?>) transport).getTransportContext();
    }
    throw new IllegalArgumentException("Expected instance of TransportImpl.");
  }

  private ForcedSender() {}
}
