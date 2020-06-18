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

package com.google.firebase.inappmessaging;

import static com.google.firebase.inappmessaging.internal.InAppMessageStreamManager.ON_FOREGROUND;

import android.app.Activity;
import android.os.Bundle;
import com.google.firebase.inappmessaging.internal.ForegroundNotifier;
import io.reactivex.BackpressureStrategy;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.subjects.BehaviorSubject;

public class TestForegroundNotifier extends ForegroundNotifier {

  private final BehaviorSubject<String> foregroundSubject = BehaviorSubject.create();

  @Override
  public ConnectableFlowable<String> foregroundFlowable() {
    return foregroundSubject.toFlowable(BackpressureStrategy.BUFFER).publish();
  }

  public void notifyForeground() {
    foregroundSubject.onNext(ON_FOREGROUND);
  }

  @Override
  public void onActivityResumed(Activity activity) {}

  @Override
  public void onActivityPaused(Activity activity) {}

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  @Override
  public void onActivityStarted(Activity activity) {}

  @Override
  public void onActivityStopped(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}
}
