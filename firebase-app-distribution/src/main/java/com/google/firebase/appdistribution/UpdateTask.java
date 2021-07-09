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

package com.google.firebase.appdistribution;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;

public abstract class UpdateTask extends Task<UpdateState> {

  /**
   * Adds a listener that is called periodically while the UpdateTask executes.
   *
   * @return this Task
   */
  @NonNull
  public abstract UpdateTask addOnProgressListener(@NonNull OnProgressListener listener);
}
