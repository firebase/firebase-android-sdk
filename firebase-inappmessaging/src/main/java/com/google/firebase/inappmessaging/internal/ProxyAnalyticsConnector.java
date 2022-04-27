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
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.inject.Deferred;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proxy connector that delegates to analytics when it's available.
 *
 * <p>For a subset of functionality it also caches calls and propagates them to analytics once it
 * loads.
 */
public class ProxyAnalyticsConnector implements AnalyticsConnector {
  private volatile Object instance;

  public ProxyAnalyticsConnector(Deferred<AnalyticsConnector> analyticsConnector) {
    instance = analyticsConnector;
    analyticsConnector.whenAvailable(connectorProvider -> instance = connectorProvider.get());
  }

  private AnalyticsConnector safeGet() {
    Object result = instance;
    if (result instanceof AnalyticsConnector) {
      return (AnalyticsConnector) result;
    }
    return null;
  }

  @Override
  public void logEvent(@NonNull String s, @NonNull String s1, @NonNull Bundle bundle) {
    AnalyticsConnector connector = safeGet();
    if (connector != null) {
      connector.logEvent(s, s1, bundle);
    }
  }

  @Override
  public void setUserProperty(@NonNull String s, @NonNull String s1, @NonNull Object o) {
    AnalyticsConnector connector = safeGet();
    if (connector != null) {
      connector.setUserProperty(s, s1, o);
    }
  }

  // Not implemented since it's not used by fiam
  @NonNull
  @Override
  public Map<String, Object> getUserProperties(boolean b) {
    return Collections.emptyMap();
  }

  @NonNull
  @Override
  public AnalyticsConnectorHandle registerAnalyticsConnectorListener(
      @NonNull String s, @NonNull AnalyticsConnectorListener analyticsConnectorListener) {
    Object result = instance;
    if (result instanceof AnalyticsConnector) {
      return ((AnalyticsConnector) result)
          .registerAnalyticsConnectorListener(s, analyticsConnectorListener);
    }
    @SuppressWarnings("unchecked")
    Deferred<AnalyticsConnector> deferred = (Deferred<AnalyticsConnector>) result;
    return new ProxyAnalyticsConnectorHandle(s, analyticsConnectorListener, deferred);
  }

  // Not implemented since it's not used by fiam.
  @Override
  public void setConditionalUserProperty(
      @NonNull ConditionalUserProperty conditionalUserProperty) {}

  // Not implemented since it's not used by fiam.
  @Override
  public void clearConditionalUserProperty(
      @NonNull String s, @Nullable String s1, @Nullable Bundle bundle) {}

  // Not implemented since it's not used by fiam.
  @NonNull
  @Override
  public List<ConditionalUserProperty> getConditionalUserProperties(
      @NonNull String s, @Nullable String s1) {
    return Collections.emptyList();
  }

  // Not implemented since it's not used by fiam.
  @Override
  public int getMaxUserProperties(@NonNull String s) {
    return 0;
  }

  private static class ProxyAnalyticsConnectorHandle implements AnalyticsConnectorHandle {
    private static final Object UNREGISTERED = new Object();

    @GuardedBy("this")
    private Set<String> eventNames = new HashSet<>();

    private volatile Object instance;

    private ProxyAnalyticsConnectorHandle(
        String s,
        AnalyticsConnectorListener listener,
        Deferred<AnalyticsConnector> analyticsConnector) {
      analyticsConnector.whenAvailable(
          connectorProvider -> {
            Object result = instance;
            if (result == UNREGISTERED) {
              return;
            }
            AnalyticsConnector connector = connectorProvider.get();
            // Now that analytics is available:

            // register the listener with analytics.
            AnalyticsConnectorHandle handle =
                connector.registerAnalyticsConnectorListener(s, listener);
            instance = handle;

            // propagate registered event names to analytics.
            synchronized (ProxyAnalyticsConnectorHandle.this) {
              if (!eventNames.isEmpty()) {
                handle.registerEventNames(eventNames);
                eventNames = new HashSet<>();
              }
            }
          });
    }

    @Override
    public void unregister() {
      Object result = instance;
      if (result == UNREGISTERED) {
        return;
      }

      if (result != null) {
        AnalyticsConnectorHandle handle = (AnalyticsConnectorHandle) result;
        handle.unregister();
      }
      instance = UNREGISTERED;
      synchronized (this) {
        eventNames.clear();
      }
    }

    @Override
    public void registerEventNames(@NonNull Set<String> set) {
      Object result = instance;
      if (result == UNREGISTERED) {
        return;
      }

      if (result != null) {
        AnalyticsConnectorHandle handle = (AnalyticsConnectorHandle) result;
        handle.registerEventNames(set);
        return;
      }
      synchronized (this) {
        eventNames.addAll(set);
      }
    }

    @Override
    public void unregisterEventNames() {
      Object result = instance;
      if (result == UNREGISTERED) {
        return;
      }
      if (result != null) {
        AnalyticsConnectorHandle handle = (AnalyticsConnectorHandle) result;
        handle.unregisterEventNames();
        return;
      }
      synchronized (this) {
        eventNames.clear();
      }
    }
  }
}
