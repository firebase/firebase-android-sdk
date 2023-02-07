// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.impl;

/** Feedback trigger type */
enum FeedbackTrigger {
  NOTIFICATION("notification"),
  CUSTOM("custom"),
  UNKNOWN("unknown");

  private final String value;

  FeedbackTrigger(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }

  public static FeedbackTrigger fromString(String value) {
    if (value.equals(NOTIFICATION.value)) {
      return NOTIFICATION;
    } else if (value.equals(CUSTOM.value)) {
      return CUSTOM;
    } else {
      return UNKNOWN;
    }
  }
}
