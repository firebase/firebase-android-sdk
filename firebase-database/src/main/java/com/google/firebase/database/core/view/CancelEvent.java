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

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;

public class CancelEvent implements Event {

  private final Path path;
  private final EventRegistration eventRegistration;
  private final DatabaseError error;

  public CancelEvent(EventRegistration eventRegistration, DatabaseError error, Path path) {
    this.eventRegistration = eventRegistration;
    this.path = path;
    this.error = error;
  }

  @Override
  public Path getPath() {
    return this.path;
  }

  @Override
  public void fire() {
    this.eventRegistration.fireCancelEvent(this.error);
  }

  @Override
  public String toString() {
    return this.getPath() + ":" + "CANCEL";
  }
}
