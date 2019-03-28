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
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;
import javax.inject.Inject;

/**
 * Schedules the AlarmManager service based on the backendname. Used for Api levels 20 and below.
 */
public class AlarmManagerScheduler implements WorkScheduler {

  private final Context context;

  private final EventStore eventStore;

  private final Clock clock;

  @Inject
  public AlarmManagerScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  @Override
  public void schedule(String backendName, int attemptNumber) {}
}
