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

package com.google.firebase.database.core;

/**
 * This interface defines the operations required for the Firebase Database library to fire
 * callbacks. Most users should not need this interface, it is only applicable if you are
 * customizing the way in which callbacks are triggered.
 */
public interface EventTarget {

  /**
   * This method will be called from the library's event loop whenever there is a new callback to be
   * triggered.
   *
   * @param r The callback to be run
   */
  void postEvent(Runnable r);

  void shutdown();

  void restart();
}
