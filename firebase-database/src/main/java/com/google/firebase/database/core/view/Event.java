// Copyright 2018 Google LLC
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

package com.google.firebase.database.core.view;

import com.google.firebase.database.core.Path;

public interface Event {
  /** */
  public static enum EventType {
    // The order is important here and reflects the order events should be raised in
    CHILD_REMOVED,
    CHILD_ADDED,
    CHILD_MOVED,
    CHILD_CHANGED,
    VALUE
  }

  public Path getPath();

  public void fire();

  @Override
  public String toString();
}
