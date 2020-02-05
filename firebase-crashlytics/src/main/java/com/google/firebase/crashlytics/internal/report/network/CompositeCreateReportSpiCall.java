// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.report.network;

import com.google.firebase.crashlytics.internal.report.model.CreateReportRequest;

public class CompositeCreateReportSpiCall implements CreateReportSpiCall {

  private final DefaultCreateReportSpiCall javaReportSpiCall;
  private final NativeCreateReportSpiCall nativeReportSpiCall;

  public CompositeCreateReportSpiCall(
      DefaultCreateReportSpiCall javaReportSpiCall, NativeCreateReportSpiCall nativeReportSpiCall) {
    this.javaReportSpiCall = javaReportSpiCall;
    this.nativeReportSpiCall = nativeReportSpiCall;
  }

  @Override
  public boolean invoke(CreateReportRequest requestData, boolean dataCollectionToken) {
    switch (requestData.report.getType()) {
      case JAVA:
        javaReportSpiCall.invoke(requestData, dataCollectionToken);
        return true;
      case NATIVE:
        nativeReportSpiCall.invoke(requestData, dataCollectionToken);
        return true;
      default:
        return false;
    }
  }
}
