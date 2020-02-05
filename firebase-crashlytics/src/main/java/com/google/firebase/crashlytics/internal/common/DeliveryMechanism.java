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

package com.google.firebase.crashlytics.internal.common;

/** Must match the DeliveryMechanism enum from crashlytics.proto */
public enum DeliveryMechanism {
  DEVELOPER(1),
  USER_SIDELOAD(2),
  TEST_DISTRIBUTION(3),
  APP_STORE(4);

  private final int id;

  DeliveryMechanism(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }

  @Override
  public String toString() {
    return Integer.toString(id);
  }

  /**
   * Currently never returns USER_SIDELOAD since sideloading can't be distinguished from DEVELOPER
   * installation
   */
  public static DeliveryMechanism determineFrom(String installerPackageName) {
    return (installerPackageName != null) ? APP_STORE : DEVELOPER;
  }
}
