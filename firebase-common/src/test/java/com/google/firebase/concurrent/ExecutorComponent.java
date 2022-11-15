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

package com.google.firebase.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

class ExecutorComponent {
  final ScheduledExecutorService bgScheduledService;
  final ExecutorService bgService;
  final Executor bgExecutor;

  final ScheduledExecutorService liteScheduledService;
  final ExecutorService liteService;
  final Executor liteExecutor;

  final ScheduledExecutorService blockingScheduledService;
  final ExecutorService blockingService;
  final Executor blockingExecutor;

  final Executor uiExecutor;

  public ExecutorComponent(
      ScheduledExecutorService bgScheduledService,
      ExecutorService bgService,
      Executor bgExecutor,
      ScheduledExecutorService liteScheduledService,
      ExecutorService liteService,
      Executor liteExecutor,
      ScheduledExecutorService blockingScheduledService,
      ExecutorService blockingService,
      Executor blockingExecutor,
      Executor uiExecutor) {
    this.bgScheduledService = bgScheduledService;
    this.bgService = bgService;
    this.bgExecutor = bgExecutor;
    this.liteScheduledService = liteScheduledService;
    this.liteService = liteService;
    this.liteExecutor = liteExecutor;
    this.blockingScheduledService = blockingScheduledService;
    this.blockingService = blockingService;
    this.blockingExecutor = blockingExecutor;
    this.uiExecutor = uiExecutor;
  }
}
