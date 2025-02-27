// Copyright 2023 Google LLC
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

package com.google.firebase.crashlytics.internal;

import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.inject.Deferred;
import com.google.firebase.remoteconfig.interop.FirebaseRemoteConfigInterop;

public class RemoteConfigDeferredProxy {
  private final Deferred<FirebaseRemoteConfigInterop> remoteConfigInteropDeferred;

  public RemoteConfigDeferredProxy(
      Deferred<FirebaseRemoteConfigInterop> remoteConfigInteropDeferred) {
    this.remoteConfigInteropDeferred = remoteConfigInteropDeferred;
  }

  public void setupListener(UserMetadata metadata) {
    if (metadata == null) {
      Logger.getLogger().w("Didn't successfully register with UserMetadata for rollouts listener");
      return;
    }
    CrashlyticsRemoteConfigListener listener = new CrashlyticsRemoteConfigListener(metadata);
    remoteConfigInteropDeferred.whenAvailable(
        remoteConfigInteropProvider -> {
          FirebaseRemoteConfigInterop interop = remoteConfigInteropProvider.get();
          interop.registerRolloutsStateSubscriber("firebase", listener);
          Logger.getLogger().d("Registering RemoteConfig Rollouts subscriber");
        });
  }
}
