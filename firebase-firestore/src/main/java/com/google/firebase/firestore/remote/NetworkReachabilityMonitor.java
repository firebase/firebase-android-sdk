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

/** Interface for monitoring changes in network connectivity/reachability. */
public interface NetworkReachabilityMonitor {
  enum Reachability {
    UNREACHABLE,
    REACHABLE,
  };

  interface NetworkReachabilityCallback {
    void onChange(Reachability networkStatus);
  }

  // TODO(rsgowman): Skipping isNetworkReachable() until we need it.
  // boolean isNetworkReachable();

  void onNetworkReachabilityChange(NetworkReachabilityCallback callback);
}
