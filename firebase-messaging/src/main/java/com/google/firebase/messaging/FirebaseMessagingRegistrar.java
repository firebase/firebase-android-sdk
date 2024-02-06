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

import androidx.annotation.Keep;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.datatransport.TransportBackend;
import com.google.firebase.events.Subscriber;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.Arrays;
import java.util.List;

/**
 * {@link ComponentRegistrar} for FirebaseMessaging - see
 * go/firebase-components-android-integration-guide for more details
 *
 * @hide
 */
@KeepForSdk
@Keep
public class FirebaseMessagingRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-fcm";

  @Override
  @Keep
  public List<Component<?>> getComponents() {
    Qualified<TransportFactory> transportFactory =
        Qualified.qualified(TransportBackend.class, TransportFactory.class);
    return Arrays.asList(
        Component.builder(FirebaseMessaging.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.optional(FirebaseInstanceIdInternal.class))
            .add(Dependency.optionalProvider(UserAgentPublisher.class))
            .add(Dependency.optionalProvider(HeartBeatInfo.class))
            .add(Dependency.required(FirebaseInstallationsApi.class))
            .add(Dependency.optionalProvider(transportFactory))
            .add(Dependency.required(Subscriber.class))
            .factory(
                container ->
                    new FirebaseMessaging(
                        container.get(FirebaseApp.class),
                        container.get(FirebaseInstanceIdInternal.class),
                        container.getProvider(UserAgentPublisher.class),
                        container.getProvider(HeartBeatInfo.class),
                        container.get(FirebaseInstallationsApi.class),
                        container.getProvider(transportFactory),
                        container.get(Subscriber.class)))
            .alwaysEager()
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
