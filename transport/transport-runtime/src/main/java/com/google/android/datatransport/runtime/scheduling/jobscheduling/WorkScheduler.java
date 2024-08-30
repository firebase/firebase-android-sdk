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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.zip.Adler32;

/** Schedules the services to be able to eventually log events to their respective backends. */
public interface WorkScheduler {
  void schedule(TransportContext transportContext, int attemptNumber);

  void schedule(TransportContext transportContext, int attemptNumber, boolean force);

  @VisibleForTesting
  static int getJobId(Context context, TransportContext transportContext) {
    Adler32 checksum = new Adler32();
    checksum.update(context.getPackageName().getBytes(Charset.forName("UTF-8")));
    checksum.update(transportContext.getBackendName().getBytes(Charset.forName("UTF-8")));
    checksum.update(
        ByteBuffer.allocate(4)
            .putInt(PriorityMapping.toInt(transportContext.getPriority()))
            .array());
    if (transportContext.getExtras() != null) {
      checksum.update(transportContext.getExtras());
    }
    return (int) checksum.getValue();
  }
}
