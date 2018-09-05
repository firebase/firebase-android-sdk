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

package com.google.firebase.database.core.persistence;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import android.util.Log;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class NoopPersistenceManager implements PersistenceManager {

  private static final String TAG = "NoopPersistenceManager";

  private boolean insideTransaction = false;

  @Override
  public void saveUserOverwrite(Path path, Node node, long writeId) {
    verifyInsideTransaction();
  }

  @Override
  public void saveUserMerge(Path path, CompoundWrite children, long writeId) {
    verifyInsideTransaction();
  }

  @Override
  public void removeUserWrite(long writeId) {
    verifyInsideTransaction();
  }

  @Override
  public void removeAllUserWrites() {
    verifyInsideTransaction();
  }

  @Override
  public void applyUserWriteToServerCache(Path path, Node node) {
    verifyInsideTransaction();
  }

  @Override
  public void applyUserWriteToServerCache(Path path, CompoundWrite merge) {
    verifyInsideTransaction();
  }

  @Override
  public List<UserWriteRecord> loadUserWrites() {
    return Collections.emptyList();
  }

  @Override
  public CacheNode serverCache(QuerySpec query) {
    return new CacheNode(
        IndexedNode.from(EmptyNode.Empty(), query.getIndex()),
        /* fullyInitialized= */ false,
        /*filtered=*/ false);
  }

  @Override
  public void updateServerCache(QuerySpec query, Node node) {
    verifyInsideTransaction();
  }

  @Override
  public void updateServerCache(Path path, CompoundWrite children) {
    verifyInsideTransaction();
  }

  @Override
  public void setQueryActive(QuerySpec query) {
    verifyInsideTransaction();
  }

  @Override
  public void setQueryInactive(QuerySpec query) {
    verifyInsideTransaction();
  }

  @Override
  public void setQueryComplete(QuerySpec query) {
    verifyInsideTransaction();
  }

  @Override
  public void setTrackedQueryKeys(QuerySpec query, Set<ChildKey> keys) {
    verifyInsideTransaction();
  }

  @Override
  public void updateTrackedQueryKeys(QuerySpec query, Set<ChildKey> added, Set<ChildKey> removed) {
    verifyInsideTransaction();
  }

  @Override
  public <T> T runInTransaction(Callable<T> callable) {
    // We still track insideTransaction, so we can catch bugs.
    hardAssert(
        !insideTransaction,
        "runInTransaction called when an existing transaction is already in progress.");
    insideTransaction = true;
    try {
      return callable.call();
    } catch (Throwable e) {
      // NOTE: Normally we'd use LogWrapper, but that requires a context, which we lack here. So
      // we'll just log directly to Logcat.
      Log.e(TAG, "Caught Throwable.", e);
      throw new RuntimeException(e);
    } finally {
      insideTransaction = false;
    }
  }

  private void verifyInsideTransaction() {
    hardAssert(this.insideTransaction, "Transaction expected to already be in progress.");
  }
}
