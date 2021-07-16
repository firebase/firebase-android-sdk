// Copyright 2020 Google LLC
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
package com.google.testing.timing;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** Fake executor service that simply executes the runnable in the same thread. */
public class FakeDirectExecutorService extends AbstractExecutorService {

  private boolean isShutdown = false;

  public void execute(Runnable command) {
    command.run();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return true;
  }

  @Override
  public boolean isShutdown() {
    return isShutdown;
  }

  @Override
  public void shutdown() {
    isShutdown = true;
  }

  @Override
  public List<Runnable> shutdownNow() {
    isShutdown = true;
    return Arrays.asList();
  }

  @Override
  public boolean isTerminated() {
    return isShutdown;
  }
}
