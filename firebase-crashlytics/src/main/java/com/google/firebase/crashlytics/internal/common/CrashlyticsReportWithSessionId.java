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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;

@AutoValue
public abstract class CrashlyticsReportWithSessionId {

  public abstract CrashlyticsReport getReport();

  public abstract String getSessionId();

  @NonNull
  public static CrashlyticsReportWithSessionId create(CrashlyticsReport report, String sessionId) {
    return new AutoValue_CrashlyticsReportWithSessionId(report, sessionId);
  }
}
