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

package com.google.firebase.inappmessaging.internal;

import com.google.auto.value.AutoValue;
import com.google.firebase.installations.InstallationTokenResult;

/**
 * Class to encapsulate the values pulled from the getId and getToken methods of the FIS API. Since
 * FIAM needs both of these results and FIS doesn't provide an API to fetch both of them this data
 * class facilitates the encapsulation of both values once they are fetched individually.
 */
@AutoValue
public abstract class InstallationIdResult {
  public static InstallationIdResult create(
      String installationId, InstallationTokenResult installationTokenResult) {
    return new AutoValue_InstallationIdResult(installationId, installationTokenResult);
  }

  abstract String installationId();

  abstract InstallationTokenResult installationTokenResult();
}
