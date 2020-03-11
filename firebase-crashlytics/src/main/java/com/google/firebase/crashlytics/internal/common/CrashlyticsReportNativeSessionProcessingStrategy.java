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

import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.IOException;
import java.io.InputStream;

class CrashlyticsReportNativeSessionProcessingStrategy
    implements NativeSessionProcessingStrategy<CrashlyticsReport> {

  @Override
  public CrashlyticsReport processNativeSession(
      CrashlyticsNativeComponent nativeComponent,
      String sessionId,
      InputStream keysInput,
      InputStream logsInput,
      InputStream userInput)
      throws IOException {
    return null;
  }
}
