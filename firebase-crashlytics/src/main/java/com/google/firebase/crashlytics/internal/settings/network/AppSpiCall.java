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

import com.google.firebase.crashlytics.internal.settings.model.AppRequestData;

/** Internal interface representing the SPI REST call to report App information to the server. */
interface AppSpiCall {
  /**
   * @param requestData {@link AppRequestData} to send to the server.
   * @return <code>true</code> if the call should be considered complete, <code>false</code> if it
   *     should be considered to have failed, and needs repeating.
   */
  boolean invoke(AppRequestData requestData, boolean dataCollectionToken);
}
