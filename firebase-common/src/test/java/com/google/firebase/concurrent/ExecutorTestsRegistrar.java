package com.google.firebase.concurrent;

import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class ExecutorTestsRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    Qualified<ScheduledExecutorService> bgScheduledService =
        Qualified.qualified(Background.class, ScheduledExecutorService.class);
    Qualified<ExecutorService> bgService =
        Qualified.qualified(Background.class, ExecutorService.class);
    Qualified<Executor> bgExecutor = Qualified.qualified(Background.class, Executor.class);

    Qualified<ScheduledExecutorService> liteScheduledService =
        Qualified.qualified(Lightweight.class, ScheduledExecutorService.class);
    Qualified<ExecutorService> liteService =
        Qualified.qualified(Lightweight.class, ExecutorService.class);
    Qualified<Executor> liteExecutor = Qualified.qualified(Lightweight.class, Executor.class);

    Qualified<ScheduledExecutorService> blockingScheduledService =
        Qualified.qualified(Blocking.class, ScheduledExecutorService.class);
    Qualified<ExecutorService> blockingService =
        Qualified.qualified(Blocking.class, ExecutorService.class);
    Qualified<Executor> blockingExecutor = Qualified.qualified(Blocking.class, Executor.class);

    Qualified<Executor> uiExecutor = Qualified.qualified(UiThread.class, Executor.class);

    return Collections.singletonList(
        Component.builder(ExecutorComponent.class)
            .add(Dependency.required(bgScheduledService))
            .add(Dependency.required(bgService))
            .add(Dependency.required(bgExecutor))
            .add(Dependency.required(liteScheduledService))
            .add(Dependency.required(liteService))
            .add(Dependency.required(liteExecutor))
            .add(Dependency.required(blockingScheduledService))
            .add(Dependency.required(blockingService))
            .add(Dependency.required(blockingExecutor))
            .add(Dependency.required(uiExecutor))
            .factory(
                c ->
                    new ExecutorComponent(
                        c.get(bgScheduledService),
                        c.get(bgService),
                        c.get(bgExecutor),
                        c.get(liteScheduledService),
                        c.get(liteService),
                        c.get(liteExecutor),
                        c.get(blockingScheduledService),
                        c.get(blockingService),
                        c.get(blockingExecutor),
                        c.get(uiExecutor)))
            .build());
  }
}
