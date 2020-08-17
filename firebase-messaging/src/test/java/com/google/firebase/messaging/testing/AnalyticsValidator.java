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

import android.os.Bundle;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.firebase.analytics.connector.AnalyticsConnector.ConditionalUserProperty;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** A validator for checking that the Analytics events and properties are set properly. */
public interface AnalyticsValidator {

  /** A representation of a Analytics Event for validation purposes. */
  @AutoValue
  public abstract static class LoggedEvent {
    public static LoggedEvent create(String origin, String name, Bundle params) {
      return new AutoValue_AnalyticsValidator_LoggedEvent(origin, name, params);
    }

    public abstract String getOrigin();

    public abstract String getName();

    @Nullable
    public abstract Bundle getParams();
  }

  /** Resets the saved data between test cases. */
  void reset();

  /** Returns all events that have been logged in the order that they were logged. */
  ImmutableList<LoggedEvent> getLoggedEvents();

  /** Returns all event names that have been logged in the order that they were logged. */
  ImmutableList<String> getLoggedEventNames();

  /** Returns all user properties that have been set without order. */
  Map<String, Object> getUserProperties(boolean includeInternal);

  /** Returns all conditional user properties that have been set in the order that they were set. */
  List<ConditionalUserProperty> getConditionalUserProperties();
}
