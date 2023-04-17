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

import android.annotation.SuppressLint;
import androidx.concurrent.futures.AbstractResolvableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// While direct use of AbstractResolvableFuture is not encouraged, it's stable for use and is not
// going to be removed. In this case it's required since we need to implement a ScheduledFuture so
// we can't use CallbackToFutureAdapter.
@SuppressLint("RestrictedApi")
@SuppressWarnings("RestrictTo")
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
