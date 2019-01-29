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

package com.google.firebase.firestore.remote;

import com.google.firebase.firestore.util.Consumer;

/** Interface for monitoring changes in network connectivity/reachability. */
public interface ConnectivityMonitor {
  enum NetworkStatus {
    UNREACHABLE,
    REACHABLE,
    // TODO(rsgowman): REACHABLE_VIA_CELLULAR.
    // Leaving this off for now, since (a) we don't need it, and (b) it's somewhat messy to
    // determine, and (c) we need two parallel implementations (for N+ and pre-N).
  };

  // TODO(rsgowman): Skipping isNetworkReachable() until we need it.
  // boolean isNetworkReachable();

  void addCallback(Consumer<NetworkStatus> callback);

  /**
   * Stops monitoring connectivity. After this call completes, no further callbacks will be
   * triggered. After shutdown() is called, no further calls are allowed on this instance.
   */
  void shutdown();
}
