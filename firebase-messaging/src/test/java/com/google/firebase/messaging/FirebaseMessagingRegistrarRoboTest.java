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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.datatransport.TransportBackend;
import com.google.firebase.events.Subscriber;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests the behavior of the FirebaseMessagingRegistrar for 3p apps} */
@RunWith(RobolectricTestRunner.class)
public final class FirebaseMessagingRegistrarRoboTest {

  @Test
  public void testGetComponents() {
    FirebaseMessagingRegistrar registrar = new FirebaseMessagingRegistrar();
    List<Component<?>> components = registrar.getComponents();
    assertThat(components).isNotEmpty();
    Component<?> component = components.get(0);
    Set<Dependency> dependencies = component.getDependencies();

    assertThat(dependencies)
        .containsAtLeast(
            Dependency.required(FirebaseApp.class),
            Dependency.optional(FirebaseInstanceIdInternal.class),
            Dependency.optionalProvider(UserAgentPublisher.class),
            Dependency.optionalProvider(HeartBeatInfo.class),
            Dependency.required(FirebaseInstallationsApi.class),
            Dependency.optionalProvider(
                Qualified.qualified(TransportBackend.class, TransportFactory.class)),
            Dependency.required(Subscriber.class));
  }
}
