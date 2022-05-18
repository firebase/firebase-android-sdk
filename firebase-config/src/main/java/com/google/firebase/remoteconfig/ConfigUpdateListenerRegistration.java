// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

/**
 * Represents a listener that can be removed by calling remove. This is returned when calling
 * addOnConfigUpdateListener and should be used when you no longer want to listen for new config
 * updates. If this is the last listener it will close the Realtime stream.
 *
 * @author Quan Pham
 */
public interface ConfigUpdateListenerRegistration {

  /**
   * Removes the listener being tracked by this 'ConfigUpdateListenerRegistration`. After the
   * initial call, subsequent calls have no effect.
   */
  public void remove();
}
