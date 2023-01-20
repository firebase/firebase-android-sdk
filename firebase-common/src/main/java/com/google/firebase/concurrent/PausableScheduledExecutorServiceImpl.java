// Copyright 2023 Google LLC
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

package com.google.firebase.concurrent;

import java.util.concurrent.ScheduledExecutorService;

final class PausableScheduledExecutorServiceImpl extends DelegatingScheduledExecutorService
    implements PausableScheduledExecutorService {
  private final PausableExecutorService delegate;

  PausableScheduledExecutorServiceImpl(
      PausableExecutorService delegate, ScheduledExecutorService scheduler) {
    super(delegate, scheduler);
    this.delegate = delegate;
  }

  @Override
  public void pause() {
    delegate.pause();
  }

  @Override
  public void resume() {
    delegate.resume();
  }

  @Override
  public boolean isPaused() {
    return delegate.isPaused();
  }
}
