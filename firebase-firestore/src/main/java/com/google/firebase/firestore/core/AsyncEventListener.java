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

package com.google.firebase.firestore.core;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.util.concurrent.Executor;

/**
 * A wrapper event listener that uses an Executor to dispatch events. Exposes a mute() call to
 * immediately silence the event listener when events are dispatched on different threads.
 */
public class AsyncEventListener<T> implements EventListener<T> {
  private final Executor executor;
  private final EventListener<T> eventListener;

  private volatile boolean muted = false;

  public AsyncEventListener(Executor executor, EventListener<T> eventListener) {
    this.executor = executor;
    this.eventListener = eventListener;
  }

  @Override
  public void onEvent(@Nullable T value, @Nullable FirebaseFirestoreException error) {
    executor.execute(
        () -> {
          if (!muted) {
            eventListener.onEvent(value, error);
          }
        });
  }

  public void mute() {
    muted = true;
  }
}
