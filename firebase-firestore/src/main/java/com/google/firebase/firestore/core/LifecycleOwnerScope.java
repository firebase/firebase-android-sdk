// Copyright 2021 Google LLC
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

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import com.google.firebase.firestore.ListenerRegistration;

/** Scopes the lifetime of a ListenerRegistration to a LifecycleOwner. */
public class LifecycleOwnerScope {

  /** Binds the given registration to the lifetime of the lifecycleOwner. */
  public static ListenerRegistration bind(
      @NonNull LifecycleOwner lifecycleOwner, @NonNull ListenerRegistration registration) {

    lifecycleOwner
        .getLifecycle()
        .addObserver(
            new LifecycleObserver() {

              @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
              public void onStop() {
                registration.remove();
              }
            });
    return registration;
  }
}
