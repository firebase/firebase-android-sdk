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

package com.google.firebase.heartbeatinfo;

import com.google.firebase.components.Component;

/** Factory to create a component that publishes the version of an SDK */
public class HeartBeatLogSourceComponent {
  private HeartBeatLogSourceComponent() {}

  /** Creates a component that publishes SDK versions */
  public static Component<?> create(String logSourceName) {
    return Component.intoSet(HeartBeatLogSource.create(logSourceName), HeartBeatLogSource.class);
  }
}
