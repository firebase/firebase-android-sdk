// Copyright 2020 Google LLC
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

import com.google.firebase.firestore.local.GarbageCollectionScheduler;
import com.google.firebase.firestore.local.LocalSerializer;
import com.google.firebase.firestore.local.LruDelegate;
import com.google.firebase.firestore.local.LruGarbageCollector;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.SQLitePersistence;
import com.google.firebase.firestore.remote.RemoteSerializer;

/** Provides all components needed for Firestore with SQLite persistence. */
public class SQLiteComponentProvider extends MemoryComponentProvider {

  @Override
  protected GarbageCollectionScheduler createGarbageCollectionScheduler(
      Configuration configuration) {
    LruDelegate lruDelegate = ((SQLitePersistence) getPersistence()).getReferenceDelegate();
    LruGarbageCollector gc = lruDelegate.getGarbageCollector();
    return gc.newScheduler(configuration.getAsyncQueue(), getLocalStore());
  }

  @Override
  protected Persistence createPersistence(Configuration configuration) {
    LocalSerializer serializer =
        new LocalSerializer(new RemoteSerializer(configuration.getDatabaseInfo().getDatabaseId()));
    LruGarbageCollector.Params params =
        LruGarbageCollector.Params.WithCacheSizeBytes(
            configuration.getSettings().getCacheSizeBytes());
    return new SQLitePersistence(
        configuration.getContext(),
        configuration.getDatabaseInfo().getPersistenceKey(),
        configuration.getDatabaseInfo().getDatabaseId(),
        serializer,
        params);
  }
}
