package com.google.firebase.inappmessaging.internal.injection.modules;

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

  public ExecutorsModule(Executor backgroundExecutor, Executor blockingExecutor) {
    this.backgroundExecutor = backgroundExecutor;
    this.blockingExecutor = blockingExecutor;
  }

  @Provides
  @Singleton
  @Background
  public Executor providesBackgroundExecutor() {
    return backgroundExecutor;
  }

  @Provides
  @Singleton
  @Blocking
  public Executor providesBlockingExecutor() {
    return blockingExecutor;
  }
}
