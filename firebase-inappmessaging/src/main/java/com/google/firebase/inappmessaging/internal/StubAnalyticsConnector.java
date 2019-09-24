// Copyright 2018 Google LLC
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

package com.google.firebase.inappmessaging.internal;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stub connector that may be used in the absence of an analytics connector. This allows us to take
 * an optional dependency on analytics connector, and use the stub as the replacement in its
 * absence.
 */
public class StubAnalyticsConnector implements AnalyticsConnector {

  public static final StubAnalyticsConnector instance = new StubAnalyticsConnector();

  private StubAnalyticsConnector() {}

  @Override
  public void logEvent(@NonNull String s, @NonNull String s1, Bundle bundle) {}

  @Override
  public void setUserProperty(@NonNull String s, @NonNull String s1, Object o) {}

  @Override
  public Map<String, Object> getUserProperties(boolean b) {
    return null;
  }

  @Override
  public AnalyticsConnectorHandle registerAnalyticsConnectorListener(
      String s, AnalyticsConnectorListener analyticsConnectorListener) {
    return AnalyticsConnectorHandle.instance;
  }

  @Override
  public void setConditionalUserProperty(
      @NonNull ConditionalUserProperty conditionalUserProperty) {}

  @Override
  public void clearConditionalUserProperty(
      @NonNull String s, @Nullable String s1, @Nullable Bundle bundle) {}

  @Override
  public List<ConditionalUserProperty> getConditionalUserProperties(
      @NonNull String s, @Nullable String s1) {
    return null;
  }

  @Override
  public int getMaxUserProperties(@NonNull String s) {
    return 0;
  }

  private static class AnalyticsConnectorHandle
      implements AnalyticsConnector.AnalyticsConnectorHandle {

    static final AnalyticsConnectorHandle instance = new AnalyticsConnectorHandle();

    private AnalyticsConnectorHandle() {}

    @Override
    public void unregister() {}

    @Override
    public void registerEventNames(Set<String> set) {}

    @Override
    public void unregisterEventNames() {}
  }
}
