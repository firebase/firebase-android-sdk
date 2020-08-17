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

import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessagingRegistrar.DevNullTransportFactory;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests the behavior of the FirebaseMessagingRegistrar for 3p apps} */
@RunWith(RobolectricTestRunner.class)
public final class FirebaseMessagingRegistrarRoboTest {
  @Test
  public void testDetermineFactory_nullFactory() {
    assertThat(FirebaseMessagingRegistrar.determineFactory(null))
        .isInstanceOf(DevNullTransportFactory.class);
  }

  @Test
  public void testDetermineFactory_nonNullFactory() {
    TransportFactory dummyTransportFactory =
        new TransportFactory() {
          @Override
          public <T> Transport<T> getTransport(
              String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
            return null;
          }

          @Override
          public <T> Transport<T> getTransport(
              String name,
              Class<T> payloadType,
              Encoding payloadEncoding,
              Transformer<T, byte[]> payloadTransformer) {
            return null;
          }
        };

    assertThat(FirebaseMessagingRegistrar.determineFactory(dummyTransportFactory))
        .isEqualTo(/* Returns differently in 1p usage */ dummyTransportFactory);
  }

  @Test
  public void testGetComponents() {
    FirebaseMessagingRegistrar registrar = new FirebaseMessagingRegistrar();
    List<Component<?>> components = registrar.getComponents();
    assertThat(components).isNotEmpty();
    Component<?> component = components.get(0);
    Set<Dependency> dependencies = component.getDependencies();

    assertThat(dependencies).contains(Dependency.required(FirebaseApp.class));
    assertThat(dependencies).contains(Dependency.required(FirebaseInstanceId.class));
    assertThat(dependencies).contains(Dependency.optional(TransportFactory.class));
  }
}
