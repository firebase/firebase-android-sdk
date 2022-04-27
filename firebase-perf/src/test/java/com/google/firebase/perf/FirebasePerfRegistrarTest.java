// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebasePerfRegistrarTest {

  @Test
  public void testGetComponents() {
    FirebasePerfRegistrar firebasePerfRegistrar = new FirebasePerfRegistrar();
    List<Component<?>> components = firebasePerfRegistrar.getComponents();

    // Note: Although we have 3 deps but looks like size doesn't count deps towards interface like
    // FirebaseInstallationsApi
    assertThat(components).hasSize(2);

    Component<?> firebasePerfComponent = components.get(0);

    assertThat(firebasePerfComponent.getDependencies())
        .containsExactly(
            Dependency.required(FirebaseApp.class),
            Dependency.requiredProvider(RemoteConfigComponent.class),
            Dependency.required(FirebaseInstallationsApi.class),
            Dependency.requiredProvider(TransportFactory.class));

    assertThat(firebasePerfComponent.isLazy()).isTrue();
  }
}
