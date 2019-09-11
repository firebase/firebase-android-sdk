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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.inappmessaging.internal.ForegroundNotifier.Listener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ForegroundNotifierTest {
  @Mock Listener listener;
  private ForegroundNotifier foregroundNotifier;

  @Before
  public void setup() {
    initMocks(this);
    foregroundNotifier = new ForegroundNotifier();
  }

  @Test
  public void notifier_onActivityResumedForFirstTime_notifiesListener() {
    ForegroundNotifier foregroundNotifier = new ForegroundNotifier();
    foregroundNotifier.setListener(listener);

    foregroundNotifier.onActivityResumed(null);

    verify(listener).onForeground();
  }

  @Test
  public void notifier_onActivityResumedBeforeRunnable_doesNotNotifyListener() {
    foregroundNotifier.setListener(listener);

    foregroundNotifier.onActivityResumed(null); // called once
    verify(listener, times(1)).onForeground();
    foregroundNotifier.onActivityPaused(null);
    foregroundNotifier.onActivityResumed(null); // should not be called

    verify(listener, times(1)).onForeground();
  }

  @Test
  public void notifier_onActivityResumedAfterRunnableExecution_notifiesListener() {
    foregroundNotifier.setListener(listener);

    foregroundNotifier.onActivityResumed(null); // 1
    verify(listener, times(1)).onForeground();
    foregroundNotifier.onActivityPaused(null);
    Robolectric.flushForegroundThreadScheduler();
    foregroundNotifier.onActivityResumed(null); // 2

    verify(listener, times(2)).onForeground();
  }
}
