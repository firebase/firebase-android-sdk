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

/** Interface to subscribe to status updates on the release update process. Called by updateApp. */
public interface UpdateProgressListener {
  /**
   * Method invoked during updating to track progress by modifying UpdateProgress object
   *
   * @param UpdateProgress object to be modified
   */
  public void onProgressUpdate(@NonNull UpdateProgress updateProgress);
}
