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

package com.google.firebase.inappmessaging.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.inappmessaging.internal.InAppMessageStreamManager.ON_FOREGROUND;

import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ForegroundNotifierTest {

  private ForegroundNotifier foregroundNotifier;
  private TestSubscriber<String> subscriber;

  @Before
  public void setup() {
    foregroundNotifier = new ForegroundNotifier();
    ConnectableFlowable<String> foregroundFlowable = foregroundNotifier.foregroundFlowable();
    foregroundFlowable.connect();
    subscriber = foregroundFlowable.test();
  }

  @Test
  public void notifier_onActivityResumedForFirstTime_notifiesListener() {
    foregroundNotifier.onActivityResumed(null);
    assertThat(subscriber.getEvents().get(0)).contains(ON_FOREGROUND);
  }

  @Test
  public void notifier_onActivityResumedBeforeRunnable_doesNotNotifyListener() {
    foregroundNotifier.onActivityResumed(null); // called once
    assertThat(subscriber.getEvents().get(0)).hasSize(1);
    foregroundNotifier.onActivityPaused(null);
    foregroundNotifier.onActivityResumed(null); // should not be called
    assertThat(subscriber.getEvents().get(0)).hasSize(1);
  }

  @Test
  public void notifier_onActivityResumedAfterRunnableExecution_notifiesListener() {
    foregroundNotifier.onActivityResumed(null); // 1
    assertThat(subscriber.getEvents().get(0)).hasSize(1);
    foregroundNotifier.onActivityPaused(null);
    Robolectric.flushForegroundThreadScheduler();
    foregroundNotifier.onActivityResumed(null); // 2
    assertThat(subscriber.getEvents().get(0)).hasSize(2);
  }
}
