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

import android.content.Context;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;

public class TestWorkScheduler implements WorkScheduler {

  private final Context context;

  public TestWorkScheduler(Context applicationContext) {
    this.context = applicationContext;
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    if (attemptNumber > 2) {
      return;
    }
    TransportRuntime.initialize(context);
    TransportRuntime.getInstance().getUploader().upload(transportContext, attemptNumber, () -> {});
  }
}
