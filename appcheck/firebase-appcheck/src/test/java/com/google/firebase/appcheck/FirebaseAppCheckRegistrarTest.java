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

package com.google.firebase.appcheck;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseAppCheckRegistrar}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseAppCheckRegistrarTest {

  @Test
  public void testGetComponents() {
    FirebaseAppCheckRegistrar firebaseAppCheckRegistrar = new FirebaseAppCheckRegistrar();
    List<Component<?>> components = firebaseAppCheckRegistrar.getComponents();
    assertThat(components).isNotEmpty();
    assertThat(components).hasSize(2);
    Component<?> firebaseAppCheckComponent = components.get(0);
    assertThat(firebaseAppCheckComponent.getDependencies())
        .containsExactly(
            Dependency.required(FirebaseApp.class),
            Dependency.optionalProvider(UserAgentPublisher.class),
            Dependency.optionalProvider(HeartBeatInfo.class));
    assertThat(firebaseAppCheckComponent.isAlwaysEager()).isTrue();
  }
}
