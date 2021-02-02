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

package com.google.android.datatransport.runtime;

import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import javax.inject.Provider;

public class TestWorkScheduler implements WorkScheduler {

  private final Provider<Uploader> uploader;

  TestWorkScheduler(Provider<Uploader> uploader) {
    this.uploader = uploader;
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    if (attemptNumber > 2) {
      return;
    }
    uploader.get().upload(transportContext, attemptNumber, () -> {});
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber, boolean force) {
    if (attemptNumber > 2) {
      return;
    }
    uploader.get().upload(transportContext, attemptNumber, () -> {});
  }
}
