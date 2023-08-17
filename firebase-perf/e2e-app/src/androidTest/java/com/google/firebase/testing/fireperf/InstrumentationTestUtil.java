// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.testing.fireperf;

import com.google.android.datatransport.Priority;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntimeTesting;

public class InstrumentationTestUtil {
  /**
   * Blocks calling thread until all of Fireperf's persisted events are sent by Firelog. Must be
   * called after events have persisted.
   */
  static void flgForceUploadSync() {
    TransportRuntimeTesting.forceUpload(CCTDestination.LEGACY_INSTANCE, Priority.DEFAULT);
  }
}
