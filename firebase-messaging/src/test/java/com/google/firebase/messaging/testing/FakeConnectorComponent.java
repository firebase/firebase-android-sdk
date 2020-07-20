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
package com.google.firebase.messaging.testing;

import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import java.util.Arrays;
import java.util.List;

/**
 * A component that injects a FakeAnalyticsConnector.
 *
 * <p>The FakeAnalyticsConnector should be used in open source testing to validate interactions with
 * the AnalyticsConnector. Use getAnalyticsValidator() for validating.
 */
public final class FakeConnectorComponent implements ComponentRegistrar {

  private static final FakeAnalyticsConnector analyticsConnector = new FakeAnalyticsConnector();

  @Override
  public List<Component<?>> getComponents() {
    Component<AnalyticsConnector> connector =
        Component.builder(AnalyticsConnector.class)
            .factory(container -> analyticsConnector)
            .build();
    return Arrays.asList(connector);
  }

  /**
   * Returns an AnalyticsValidator that can be used to validate interactions with the
   * FakeAnalyticsConnector.
   */
  public static AnalyticsValidator getAnalyticsValidator() {
    return analyticsConnector;
  }

  /** Provides access to an instance of AnalyticsConnector. */
  public static AnalyticsConnector getAnalyticsConnector() {
    return analyticsConnector;
  }
}
