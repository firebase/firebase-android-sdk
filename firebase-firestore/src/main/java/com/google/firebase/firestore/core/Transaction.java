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

import static com.google.firebase.firestore.util.Assert.fail;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.core.UserData.ParsedSetData;
import com.google.firebase.firestore.core.UserData.ParsedUpdateData;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.util.Executors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Internal transaction object responsible for accumulating the mutations to perform and the base
 * versions for any documents read.
 */
public class Transaction {
  private final Datastore datastore;
  private final HashMap<DocumentKey, SnapshotVersion> readVersions = new HashMap<>();
  private final ArrayList<Mutation> mutations = new ArrayList<>();
  private boolean committed;

  public Transaction(Datastore d) {
    datastore = d;
  }

  private void recordVersion(MaybeDocument doc) throws FirebaseFirestoreException {
    SnapshotVersion docVersion;
    if (doc instanceof Document) {
      docVersion = doc.getVersion();
    } else if (doc instanceof NoDocument) {
      // For nonexistent docs, we must use precondition with version 0 when we overwrite them.
      docVersion = SnapshotVersion.NONE;
    } else {
      throw fail("Unexpected document type in transaction: " + doc.getClass().getCanonicalName());
    }

    if (readVersions.containsKey(doc.getKey())) {
      SnapshotVersion existingVersion = readVersions.get(doc.getKey());
      if (!existingVersion.equals(doc.getVersion())) {
        // This transaction will fail no matter what.
        throw new FirebaseFirestoreException(
            "Document version changed between two reads.", Code.FAILED_PRECONDITION);
      }
    } else {
      readVersions.put(doc.getKey(), docVersion);
    }
  }

  /**
   * Takes a set of keys and asynchronously attempts to fetch all the documents from the backend,
   * ignoring any local changes.
   */
  public Task<List<MaybeDocument>> lookup(List<DocumentKey> keys) {
    if (committed) {
      return Tasks.forException(
          new FirebaseFirestoreException(
              "Transaction has already completed.", Code.FAILED_PRECONDITION));
    }
    if (mutations.size() != 0) {
      return Tasks.forException(
          new FirebaseFirestoreException(
              "Transactions lookups are invalid after writes.", Code.FAILED_PRECONDITION));
    }
    return datastore
        .lookup(keys)
        .continueWithTask(
            Executors.DIRECT_EXECUTOR,
            task -> {
              if (task.isSuccessful()) {
                for (MaybeDocument doc : task.getResult()) {
                  recordVersion(doc);
                }
              }
              return task;
            });
  }

  private void write(List<Mutation> mutations) {
    if (committed) {
      throw new IllegalStateException("Transaction has already completed.");
    }
    this.mutations.addAll(mutations);
  }

  /**
   * Returns version of this doc when it was read in this transaction as a precondition, or no
   * precondition if it was not read.
   */
  private Precondition precondition(DocumentKey key) {
    @Nullable SnapshotVersion version = readVersions.get(key);
    if (version != null) {
      return Precondition.updateTime(version);
    } else {
      return Precondition.NONE;
    }
  }

  /**
   * Returns the precondition for a document if the operation is an update, based on the provided
   * UpdateOptions.
   */
  private Precondition preconditionForUpdate(DocumentKey key) {
    @Nullable SnapshotVersion version = this.readVersions.get(key);
    if (version != null && version.equals(SnapshotVersion.NONE)) {
      // The document to update doesn't exist, so fail the transaction.
      throw new IllegalStateException("Can't update a document that doesn't exist.");
    } else if (version != null) {
      // Document exists, base precondition on document update time.
      return Precondition.updateTime(version);
    } else {
      // Document was not read, so we just use the preconditions for a blind write.
      return Precondition.exists(true);
    }
  }

  /** Stores a set mutation for the given key and value, to be committed when commit() is called. */
  public void set(DocumentKey key, ParsedSetData data) {
    write(data.toMutationList(key, precondition(key)));
  }

  /**
   * Stores an update mutation for the given key and values, to be committed when commit() is
   * called.
   */
  public void update(DocumentKey key, ParsedUpdateData data) {
    write(data.toMutationList(key, preconditionForUpdate(key)));
  }

  public void delete(DocumentKey key) {
    write(Collections.singletonList(new DeleteMutation(key, precondition(key))));
    // Since the delete will be applied before all following writes, we need to ensure that the
    // precondition for the next write will be exists: false.
    readVersions.put(key, SnapshotVersion.NONE);
  }

  public Task<Void> commit() {
    if (committed) {
      return Tasks.forException(
          new FirebaseFirestoreException(
              "Transaction has already completed.", Code.FAILED_PRECONDITION));
    }
    HashSet<DocumentKey> unwritten = new HashSet<>(readVersions.keySet());
    // For each mutation, note that the doc was written.
    for (Mutation mutation : mutations) {
      unwritten.remove(mutation.getKey());
    }
    if (unwritten.size() > 0) {
      return Tasks.forException(
          new FirebaseFirestoreException(
              "Every document read in a transaction must also be written.",
              Code.FAILED_PRECONDITION));
    }
    committed = true;
    return datastore
        .commit(mutations)
        .continueWithTask(
            Executors.DIRECT_EXECUTOR,
            task -> {
              if (task.isSuccessful()) {
                return Tasks.forResult(null);
              } else {
                return Tasks.forException(task.getException());
              }
            });
  }

  private static final Executor defaultExecutor = createDefaultExecutor();

  private static Executor createDefaultExecutor() {
    // Create a thread pool with a reasonable size.
    int corePoolSize = 5;
    // maxPoolSize only gets used when queue is full, and queue size is MAX_INT, so this is a no-op.
    int maxPoolSize = corePoolSize;
    int keepAliveSeconds = 1;
    LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS, queue);
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  public static Executor getDefaultExecutor() {
    return defaultExecutor;
  }
}
