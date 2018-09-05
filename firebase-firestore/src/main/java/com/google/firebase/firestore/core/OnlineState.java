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

package com.google.firebase.firestore.core;

/**
 * Describes the online state of the Firestore client. Note that this does not indicate whether or
 * not the remote store is trying to connect or not. This is primarily used by the View /
 * EventManager code to change their behavior while offline (e.g. get() calls shouldn't wait for
 * data from the server and snapshot events should set metadata.isFromCache=true).
 */
public enum OnlineState {
  /**
   * The Firestore client is in an unknown online state. This means the client is either not
   * actively trying to establish a connection or it is currently trying to establish a connection,
   * but it has not succeeded or failed yet. Higher-level components (e.g. Views and the
   * EventManager) should likely operate in online mode, waiting until they receive a definitive
   * OFFLINE notification before reverting to cache data, etc.
   */
  UNKNOWN,

  /**
   * The client is connected and the connections are healthy. This state is reached after a
   * successful connection and there has been at least one successful message received from the
   * backends.
   */
  ONLINE,

  /**
   * The client is either trying to establish a connection but failing, or it has been explicitly
   * marked offline via a call to disableNetwork(). Higher-level components (e.g. Views and the
   * EventManager) should operate in offline mode.
   */
  OFFLINE
}
