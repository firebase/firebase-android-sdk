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

package com.google.firebase.inappmessaging.internal.injection.modules;

import androidx.annotation.NonNull;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executor;
import javax.inject.Singleton;

/** Provides executors for running tasks. */
@Module
public class ExecutorsModule {
  private final Executor backgroundExecutor;
  private final Executor blockingExecutor;

  public ExecutorsModule(
      @NonNull @Background Executor backgroundExecutor,
      @NonNull @Blocking Executor blockingExecutor) {
    this.backgroundExecutor = backgroundExecutor;
    this.blockingExecutor = blockingExecutor;
  }

  @Provides
  @Singleton
  @Background
  @NonNull
  public Executor providesBackgroundExecutor() {
    return backgroundExecutor;
  }

  @Provides
  @Singleton
  @Blocking
  @NonNull
  public Executor providesBlockingExecutor() {
    return blockingExecutor;
  }
}
