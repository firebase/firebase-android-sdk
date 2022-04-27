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

package com.google.firebase.messaging.testing;

import android.os.Bundle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A fast, deterministic, fake implementation of AnalyticsConnector to be used for unit testing.
 *
 * <p>It provides support for logging events, setting/getting user properties, and setting/getting
 * conditional user properties.
 */
public class FakeAnalyticsConnector implements AnalyticsConnector, AnalyticsValidator {

  private static final String CAMPAIGN_INFO_SOURCE = "_cis";
  private static final String FCM_CONNECTOR_CAMPAIGN = "fcm_integration";
  private final List<AnalyticsValidator.LoggedEvent> events = new ArrayList<>();
  private final Map<String, Object> userProperties = new HashMap<>();
  private final List<ConditionalUserProperty> conditionalUserProperties = new ArrayList<>();

  @Override
  public AnalyticsConnectorHandle registerAnalyticsConnectorListener(
      String origin, AnalyticsConnectorListener listener) {
    return null;
  }

  @Override
  public void logEvent(String origin, String name, Bundle params) {
    if (origin.equals("fcm")) {
      params.putString(CAMPAIGN_INFO_SOURCE, FCM_CONNECTOR_CAMPAIGN);
    }
    events.add(AnalyticsValidator.LoggedEvent.create(origin, name, params));
  }

  @Override
  public void setUserProperty(String origin, String name, Object value) {
    userProperties.put(name, value);
  }

  @Override
  public Map<String, Object> getUserProperties(boolean includeInternal) {
    ImmutableMap.Builder<String, Object> filteredProperties = ImmutableMap.builder();
    for (Entry<String, Object> property : userProperties.entrySet()) {
      if (includeInternal || !property.getKey().startsWith("_")) {
        filteredProperties.put(property);
      }
    }
    return filteredProperties.build();
  }

  @Override
  public void setConditionalUserProperty(ConditionalUserProperty conditionalUserProperty) {
    conditionalUserProperties.add(conditionalUserProperty);
  }

  @Override
  public void clearConditionalUserProperty(
      String userPropertyName, String clearEventName, Bundle clearEventParams) {
    for (Iterator<ConditionalUserProperty> iterator = conditionalUserProperties.iterator();
        iterator.hasNext(); ) {
      ConditionalUserProperty conditionalUserProperty = iterator.next();
      if (conditionalUserProperty.name.equals(userPropertyName)) {
        iterator.remove();
        logEvent(conditionalUserProperty.origin, clearEventName, clearEventParams);
      }
    }
  }

  @Override
  public int getMaxUserProperties(String origin) {
    return 25;
  }

  @Override
  public List<ConditionalUserProperty> getConditionalUserProperties(
      String origin, String propertyNamePrefix) {
    ImmutableList.Builder<ConditionalUserProperty> filteredProperties = ImmutableList.builder();
    for (ConditionalUserProperty property : conditionalUserProperties) {
      if (property.origin.equals(origin) && property.name.startsWith(propertyNamePrefix)) {
        filteredProperties.add(property);
      }
    }
    return filteredProperties.build();
  }

  @Override
  public List<ConditionalUserProperty> getConditionalUserProperties() {
    return conditionalUserProperties;
  }

  @Override
  public ImmutableList<AnalyticsValidator.LoggedEvent> getLoggedEvents() {
    return ImmutableList.copyOf(events);
  }

  @Override
  public ImmutableList<String> getLoggedEventNames() {
    ImmutableList.Builder<String> names = ImmutableList.builder();
    for (AnalyticsValidator.LoggedEvent event : events) {
      names.add(event.getName());
    }
    return names.build();
  }

  @Override
  public void reset() {
    events.clear();
    userProperties.clear();
    conditionalUserProperties.clear();
  }
}
