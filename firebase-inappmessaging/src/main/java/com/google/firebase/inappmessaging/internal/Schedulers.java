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

package com.google.firebase.inappmessaging.internal;

import io.reactivex.Scheduler;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Schedulers container for rx actions
 *
 * @hide
 */
@Singleton
public class Schedulers {
  private final Scheduler ioScheduler;
  private final Scheduler computeScheduler;
  private final Scheduler mainThreadScheduler;

  @Inject
  Schedulers(
      @Named("io") Scheduler ioScheduler,
      @Named("compute") Scheduler computeScheduler,
      @Named("main") Scheduler mainThreadScheduler) {
    this.ioScheduler = ioScheduler;
    this.computeScheduler = computeScheduler;
    this.mainThreadScheduler = mainThreadScheduler;
  }

  public Scheduler io() {
    return ioScheduler;
  }

  public Scheduler mainThread() {
    return mainThreadScheduler;
  }

  public Scheduler computation() {
    return computeScheduler;
  }
}
