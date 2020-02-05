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

import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;

public class ViewCache {

  private final CacheNode eventSnap;
  private final CacheNode serverSnap;

  public ViewCache(CacheNode eventSnap, CacheNode serverSnap) {
    this.eventSnap = eventSnap;
    this.serverSnap = serverSnap;
  }

  public ViewCache updateEventSnap(IndexedNode eventSnap, boolean complete, boolean filtered) {
    return new ViewCache(new CacheNode(eventSnap, complete, filtered), this.serverSnap);
  }

  public ViewCache updateServerSnap(IndexedNode serverSnap, boolean complete, boolean filtered) {
    return new ViewCache(this.eventSnap, new CacheNode(serverSnap, complete, filtered));
  }

  public CacheNode getEventCache() {
    return this.eventSnap;
  }

  public Node getCompleteEventSnap() {
    return this.eventSnap.isFullyInitialized() ? this.eventSnap.getNode() : null;
  }

  public CacheNode getServerCache() {
    return this.serverSnap;
  }

  public Node getCompleteServerSnap() {
    return this.serverSnap.isFullyInitialized() ? this.serverSnap.getNode() : null;
  }
}
