package com.google.firebase.concurrent;

import androidx.concurrent.futures.AbstractResolvableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class DelegatingScheduledFuture<V> extends AbstractResolvableFuture<V>
    implements ScheduledFuture<V> {

  interface Completer<T> {
    void set(T value);

    void setException(Throwable ex);
  }

  interface Resolver<T> {
    ScheduledFuture<?> addCompleter(Completer<T> completer);
  }

  DelegatingScheduledFuture(Resolver<V> resolver) {
    upstreamFuture =
        resolver.addCompleter(
            new Completer<V>() {
              @Override
              public void set(V value) {
                DelegatingScheduledFuture.this.set(value);
              }

              @Override
              public void setException(Throwable ex) {
                DelegatingScheduledFuture.this.setException(ex);
              }
            });
  }

  private final ScheduledFuture<?> upstreamFuture;

  @Override
  protected void afterDone() {
    upstreamFuture.cancel(wasInterrupted());
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return upstreamFuture.getDelay(unit);
  }

  @Override
  public int compareTo(Delayed o) {
    return upstreamFuture.compareTo(o);
  }
}
