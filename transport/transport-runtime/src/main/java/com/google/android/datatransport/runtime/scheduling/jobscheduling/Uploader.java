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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.BackendResponse;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.inject.Inject;

public class Uploader {

  private final Context context;
  private final BackendRegistry backendRegistry;
  private final EventStore eventStore;
  private final WorkScheduler workScheduler;
  private final Executor executor;
  private final SynchronizationGuard guard;

  @Inject
  public Uploader(
      Context context,
      BackendRegistry backendRegistry,
      EventStore eventStore,
      WorkScheduler workScheduler,
      Executor executor,
      SynchronizationGuard guard) {
    this.context = context;
    this.backendRegistry = backendRegistry;
    this.eventStore = eventStore;
    this.workScheduler = workScheduler;
    this.executor = executor;
    this.guard = guard;
  }


  void upload(String backendName, int attemptNumber, Runnable callback) {
  }

}
