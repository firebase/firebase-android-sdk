// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.debug;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseAppCheckDebugRegistrar}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseAppCheckDebugRegistrarTest {
  @Test
  public void testGetComponents() {
    FirebaseAppCheckDebugRegistrar registrar = new FirebaseAppCheckDebugRegistrar();
    List<Component<?>> components = registrar.getComponents();
    assertThat(components).isNotEmpty();
    assertThat(components).hasSize(2);
    Component<?> appCheckDebugComponent = components.get(0);
    assertThat(appCheckDebugComponent.getDependencies())
        .containsExactly(
            Dependency.required(FirebaseApp.class),
            Dependency.optionalProvider(InternalDebugSecretProvider.class),
            Dependency.required(Qualified.qualified(Lightweight.class, Executor.class)),
            Dependency.required(Qualified.qualified(Background.class, Executor.class)),
            Dependency.required(Qualified.qualified(Blocking.class, Executor.class)));
    assertThat(appCheckDebugComponent.isLazy()).isTrue();
  }
}
