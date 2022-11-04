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
