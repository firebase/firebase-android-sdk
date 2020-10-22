// Copyright 2018 Google LLC
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

package com.google.firebase.remoteconfig;

import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.abt.component.AbtComponent;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import java.util.Arrays;
import java.util.List;

/**
 * Mock {@link FirebaseABTesting} for testing purposes.
 *
 * @author Miraziz Yusupov
 */
public class MockFirebaseAbtRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    Component<AbtComponent> mockFirebaseAbt =
        Component.builder(AbtComponent.class).factory(container -> new FakeAbtComponent()).build();

    return Arrays.asList(mockFirebaseAbt);
  }

  private static class FakeAbtComponent extends AbtComponent {
    FakeAbtComponent() {
      super(/*appContext=*/ null, /*analyticsConnector=*/ null);
    }
  }
}
