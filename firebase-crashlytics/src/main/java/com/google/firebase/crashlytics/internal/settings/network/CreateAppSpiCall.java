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

package com.google.firebase.crashlytics.internal.settings.network;

import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;

/** Implementation of the {@link AppSpiCall} that performs an update on an existing App's data. */
public class CreateAppSpiCall extends AbstractAppSpiCall {
  public CreateAppSpiCall(
      String protocolAndHostOverride,
      String url,
      HttpRequestFactory requestFactory,
      String version) {
    super(protocolAndHostOverride, url, requestFactory, HttpMethod.POST, version);
  }
}
