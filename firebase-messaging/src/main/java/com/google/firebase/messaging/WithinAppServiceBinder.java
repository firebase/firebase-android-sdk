// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.FirebaseMessaging.TAG;

import android.content.Intent;
import android.os.Binder;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.WithinAppServiceConnection.BindRequest;

/**
 * Binder for another service (currently always FirebaseMessagingService) within the same app.
 *
 * <p>This allows sending an intent to the service via binding.
 */
class WithinAppServiceBinder extends Binder {

  interface IntentHandler {
    Task<Void> handle(Intent intent);
  }

  private final IntentHandler intentHandler;

  WithinAppServiceBinder(IntentHandler intentHandler) {
    this.intentHandler = intentHandler;
  }

  /**
   * Process the intent in the service. The intent can be processed synchronously / async based on
   * the service logic.
   */
  void send(final BindRequest bindRequest) {
    if (Binder.getCallingUid() != android.os.Process.myUid()) {
      throw new SecurityException("Binding only allowed within app");
    }
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "service received new intent via bind strategy");
    }
    // finish is quick and thread safe, just do it on the same thread as executed the task
    intentHandler
        .handle(bindRequest.intent)
        .addOnCompleteListener(Runnable::run, task -> bindRequest.finish());
  }
}
