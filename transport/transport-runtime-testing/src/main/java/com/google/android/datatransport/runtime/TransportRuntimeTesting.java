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

import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.UploadTestSupport;

/** Test support for {@link TransportRuntime}. */
public final class TransportRuntimeTesting {
  private TransportRuntimeTesting() {}

  /** Synchronously force upload all scheduled events for a given destination and priority. */
  public static BackendResponse forceUpload(Destination destination, Priority priority) {
    return UploadTestSupport.forceUpload(
        TransportContext.builder()
            .setBackendName(destination.getName())
            .setExtras(destination.getExtras())
            .setPriority(priority)
            .build());
  }
}
