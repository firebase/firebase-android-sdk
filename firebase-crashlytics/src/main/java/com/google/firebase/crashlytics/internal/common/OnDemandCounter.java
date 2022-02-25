// Copyright 2022 Google LLC
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

/** Simple, thread-safe, class to keep count of recorded and dropped on-demand events. */
public final class OnDemandCounter {
  private int recordedOnDemandExceptions;
  private int droppedOnDemandExceptions;

  public OnDemandCounter() {
    recordedOnDemandExceptions = 0;
    droppedOnDemandExceptions = 0;
  }

  public synchronized int getRecordedOnDemandExceptions() {
    return recordedOnDemandExceptions;
  }

  public synchronized void incrementRecordedOnDemandExceptions() {
    recordedOnDemandExceptions++;
  }

  public synchronized int getDroppedOnDemandExceptions() {
    return droppedOnDemandExceptions;
  }

  public synchronized void incrementDroppedOnDemandExceptions() {
    droppedOnDemandExceptions++;
  }

  public synchronized void resetDroppedOnDemandExceptions() {
    droppedOnDemandExceptions = 0;
  }
}
