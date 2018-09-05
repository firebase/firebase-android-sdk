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

package com.google.firebase.firestore.util;

import android.app.Activity;
import com.google.android.gms.common.api.internal.ActivityLifecycleObserver;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.core.QueryListener;
import com.google.firebase.firestore.core.ViewSnapshot;
import javax.annotation.Nullable;

/** Implements the ListenerRegistration interface by removing a query from the listener. */
public class ListenerRegistrationImpl implements ListenerRegistration {

  private final FirestoreClient client;

  /** The internal query listener object that is used to unlisten from the query. */
  private final QueryListener queryListener;

  /** The event listener for the query that raises events asynchronously. */
  private final ExecutorEventListener<ViewSnapshot> asyncEventListener;

  /** Creates a new ListenerRegistration. Is activity-scoped if and only if activity is non-null. */
  public ListenerRegistrationImpl(
      FirestoreClient client,
      QueryListener queryListener,
      @Nullable Activity activity,
      ExecutorEventListener<ViewSnapshot> asyncEventListener) {
    this.client = client;
    this.queryListener = queryListener;
    this.asyncEventListener = asyncEventListener;

    if (activity != null) {
      ActivityLifecycleObserver.of(activity).onStopCallOnce(this::remove);
    }
  }

  @Override
  public void remove() {
    asyncEventListener.mute();
    client.stopListening(queryListener);
  }
}
