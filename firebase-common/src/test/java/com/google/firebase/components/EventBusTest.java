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

package com.google.firebase.components;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.annotation.NonNull;
import com.google.firebase.events.Event;
import com.google.firebase.events.EventHandler;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventBusTest {
  private static final class RecordingExecutor implements Executor {
    private int executedRunnables;

    @Override
    public void execute(@NonNull Runnable command) {
      executedRunnables++;
      command.run();
    }
  }

  private static final Event<String> EVENT = new Event<>(String.class, "hello");

  @SuppressWarnings("unchecked")
  private final EventHandler<String> stringHandler1 = mock(EventHandler.class);

  @SuppressWarnings("unchecked")
  private final EventHandler<String> stringHandler2 = mock(EventHandler.class);

  @SuppressWarnings("unchecked")
  private final EventHandler<Boolean> boolHandler = mock(EventHandler.class);

  private final RecordingExecutor defaultExecutor = new RecordingExecutor();
  private final RecordingExecutor executor2 = new RecordingExecutor();

  private final EventBus enabledBus = new EventBus(defaultExecutor);

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    enabledBus.enablePublishingAndFlushPending();
  }

  @Test
  public void publish_shouldNotifySubscribedHandlers() {

    enabledBus.subscribe(String.class, stringHandler1);
    enabledBus.subscribe(String.class, stringHandler2);
    enabledBus.subscribe(Boolean.class, boolHandler);

    enabledBus.publish(EVENT);

    assertThat(defaultExecutor.executedRunnables).isEqualTo(2);

    verify(stringHandler1, times(1)).handle(EVENT);
    verify(stringHandler2, times(1)).handle(EVENT);
    verify(boolHandler, never()).handle(any());
  }

  @Test
  public void publish_shouldNotifyOnSubscribersExecutor() {
    enabledBus.subscribe(String.class, executor2, stringHandler1);
    enabledBus.subscribe(String.class, stringHandler2);

    enabledBus.publish(EVENT);

    assertThat(defaultExecutor.executedRunnables).isEqualTo(1);
    assertThat(defaultExecutor.executedRunnables).isEqualTo(1);
    verify(stringHandler1, times(1)).handle(EVENT);
    verify(stringHandler2, times(1)).handle(EVENT);
  }

  @Test
  public void unsubscribe_shouldRemoveHandlersFromSubscribers() {
    enabledBus.subscribe(String.class, stringHandler1);
    enabledBus.subscribe(String.class, stringHandler2);

    enabledBus.unsubscribe(String.class, stringHandler1);

    enabledBus.publish(EVENT);

    verify(stringHandler1, never()).handle(any());
    verify(stringHandler2, times(1)).handle(EVENT);
  }

  @Test
  public void publish_whenCalledWithNull_shouldThrow() {
    thrown.expect(NullPointerException.class);
    enabledBus.publish(null);
  }

  @Test
  public void subscribe_whenCalledWithNullType_shouldThrow() {
    thrown.expect(NullPointerException.class);
    enabledBus.subscribe(null, e -> {});
  }

  @Test
  public void subscribe_whenCalledWithNullHandler_shouldThrow() {
    thrown.expect(NullPointerException.class);
    enabledBus.subscribe(String.class, null);
  }

  @Test
  public void unsubscribe_whenCalledWithNullType_shouldThrow() {
    thrown.expect(NullPointerException.class);
    enabledBus.unsubscribe(null, e -> {});
  }

  @Test
  public void unsubscribe_whenCalledWithNullHandler_shouldThrow() {
    thrown.expect(NullPointerException.class);
    enabledBus.unsubscribe(String.class, null);
  }

  @Test
  public void publish_shouldOnlyPublishOnceTheEventBusIsPublishEnabled() {
    EventBus disabledBus = new EventBus(Runnable::run);

    disabledBus.subscribe(String.class, stringHandler1);
    disabledBus.subscribe(String.class, stringHandler2);

    disabledBus.publish(EVENT);

    verify(stringHandler1, never()).handle(any());
    verify(stringHandler2, never()).handle(any());

    disabledBus.enablePublishingAndFlushPending();

    verify(stringHandler1, times(1)).handle(EVENT);
    verify(stringHandler2, times(1)).handle(EVENT);
  }
}
