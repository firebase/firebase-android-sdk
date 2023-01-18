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
