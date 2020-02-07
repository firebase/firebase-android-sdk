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

package com.google.android.datatransport;

import androidx.annotation.Nullable;

/** An callback interface for the scheduling an Event. */
public interface TransportScheduleCallback {

  /**
   * Called when the process of scheduling an event has finished. If the process was finished
   * succesfully, the error parameter will be null, otherwise it will include the {@code Exception}
   * that caused the failure.
   *
   * @param error The error if there was error. {@code null} otherwise.
   */
  void onSchedule(@Nullable Exception error);
}
