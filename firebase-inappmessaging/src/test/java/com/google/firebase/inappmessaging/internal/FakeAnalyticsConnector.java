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

import static junit.framework.Assert.assertEquals;

import android.os.Bundle;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.analytics.connector.AnalyticsConnector.ConditionalUserProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Test implementation of AnalyticsConnector (TBReplaced by their test impl) */
public class FakeAnalyticsConnector implements AnalyticsConnector {

  FakeAnalyticsConnector() {}

  private static final List<LoggedEvent> logEventInternalCalls = new ArrayList<>();
  private static final List<LoggedUserProperty> setUserPropertyInternalCalls = new ArrayList<>();

  @Override
  public void logEvent(String origin, String name, Bundle params) {
    if (origin.equals("fiam")) {
      logEventInternalCalls.add(new LoggedEvent(origin, name, params));
    }
  }

  @Override
  public void setUserProperty(String origin, String name, Object value) {
    if (origin.equals("fiam")) {
      setUserPropertyInternalCalls.add(new LoggedUserProperty(origin, name, value));
    }
  }

  public static void resetState() {
    logEventInternalCalls.clear();
    setUserPropertyInternalCalls.clear();
  }

  public List<LoggedEvent> getLoggedEvent() {
    return logEventInternalCalls;
  }

  public List<LoggedUserProperty> getSetUserProperty() {
    return setUserPropertyInternalCalls;
  }

  public static void verifySetUserProperty(LoggedUserProperty... expectedUserProperties) {
    assertEquals(
        "Incorrect # of user-properties",
        expectedUserProperties.length,
        setUserPropertyInternalCalls.size());

    for (int i = 0; i < expectedUserProperties.length; i++) {
      LoggedUserProperty expected = expectedUserProperties[i];
      LoggedUserProperty actual = setUserPropertyInternalCalls.get(i);
      assertEquals("Incorrect user-property origin", expected.origin, actual.origin);
      assertEquals("Incorrect user-property name", expected.name, actual.name);
      assertEquals("Incorrect user-property value", expected.value, actual.value);
    }
  }

  public static class LoggedUserProperty {
    String origin;
    String name;
    Object value;

    public LoggedUserProperty(String origin, String name, Object value) {
      this.origin = origin;
      this.name = name;
      this.value = value;
    }
  }

  public static class LoggedEvent {
    String origin;
    String name;
    Bundle params;

    public LoggedEvent() {
      this("", "", new Bundle());
    }

    public LoggedEvent(String origin, String name, Bundle params) {
      this.origin = origin;
      this.name = name;
      this.params = params;
    }

    public LoggedEvent setOrigin(String origin) {
      this.origin = origin;
      return this;
    }

    public LoggedEvent setName(String name) {
      this.name = name;
      return this;
    }

    public LoggedEvent setParam(String key, String value) {
      params.putString(key, value);
      return this;
    }

    public LoggedEvent setParam(String key, int value) {
      params.putInt(key, value);
      return this;
    }
  }

  // Unused portion of the AnalyticsConnector interface:

  @Override
  public Map<String, Object> getUserProperties(boolean includeInternal) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AnalyticsConnectorHandle registerAnalyticsConnectorListener(
      String origin, AnalyticsConnectorListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setConditionalUserProperty(ConditionalUserProperty conditionalUserProperty) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearConditionalUserProperty(
      String userPropertyName, String clearEventName, Bundle clearEventParams) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ConditionalUserProperty> getConditionalUserProperties(
      String origin, String propertyNamePrefix) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMaxUserProperties(String origin) {
    throw new UnsupportedOperationException();
  }
}
