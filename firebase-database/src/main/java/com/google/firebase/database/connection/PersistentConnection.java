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

package com.google.firebase.database.connection;

import com.google.android.gms.tasks.Task;
import java.util.List;
import java.util.Map;

public interface PersistentConnection {

  interface Delegate {
    void onDataUpdate(List<String> path, Object message, boolean isMerge, Long optTag);

    void onRangeMergeUpdate(List<String> path, List<RangeMerge> merges, Long optTag);

    void onConnect();

    void onDisconnect();

    void onAuthStatus(boolean authOk);

    void onServerInfoUpdate(Map<String, Object> updates);
  }

  // Lifecycle

  void initialize();

  void shutdown();

  // Auth

  void refreshAuthToken();

  void refreshAuthToken(String token);

  // Listens

  void listen(
      List<String> path,
      Map<String, Object> queryParams,
      ListenHashProvider currentHashFn,
      Long tag,
      RequestResultCallback onComplete);

  void unlisten(List<String> path, Map<String, Object> queryParams);

  // Get

  Task<Object> get(List<String> path, Map<String, Object> queryParams);

  // Writes

  void purgeOutstandingWrites();

  void put(List<String> path, Object data, RequestResultCallback onComplete);

  void compareAndPut(List<String> path, Object data, String hash, RequestResultCallback onComplete);

  void merge(List<String> path, Map<String, Object> data, RequestResultCallback onComplete);

  // Disconnects

  void onDisconnectPut(List<String> path, Object data, RequestResultCallback onComplete);

  void onDisconnectMerge(
      List<String> path, Map<String, Object> updates, RequestResultCallback onComplete);

  void onDisconnectCancel(List<String> path, RequestResultCallback onComplete);

  // Connection management

  void interrupt(String reason);

  void resume(String reason);

  boolean isInterrupted(String reason);
}
