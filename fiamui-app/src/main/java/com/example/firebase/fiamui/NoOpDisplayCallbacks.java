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

package com.example.firebase.fiamui;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;

public class NoOpDisplayCallbacks implements FirebaseInAppMessagingDisplayCallbacks {
  @Override
  public Task<Void> impressionDetected() {
    return new TaskCompletionSource<Void>().getTask();
  }

  @Override
  public Task<Void> messageDismissed(InAppMessagingDismissType dismissType) {
    return new TaskCompletionSource<Void>().getTask();
  }

  @Override
  public Task<Void> messageClicked() {
    return new TaskCompletionSource<Void>().getTask();
  }

  @Override
  public Task<Void> displayErrorEncountered(InAppMessagingErrorReason InAppMessagingErrorReason) {
    return new TaskCompletionSource<Void>().getTask();
  }
}
