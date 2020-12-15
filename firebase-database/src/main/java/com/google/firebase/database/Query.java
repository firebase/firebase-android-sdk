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

package com.google.firebase.database;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.core.ChildEventRegistration;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.ValueEventRegistration;
import com.google.firebase.database.core.ZombieEventManager;
import com.google.firebase.database.core.utilities.Validation;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.BooleanNode;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.DoubleNode;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.KeyIndex;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PathIndex;
import com.google.firebase.database.snapshot.PriorityIndex;
import com.google.firebase.database.snapshot.PriorityUtilities;
import com.google.firebase.database.snapshot.StringNode;
import com.google.firebase.database.snapshot.ValueIndex;

/**
 * The Query class (and its subclass, {@link DatabaseReference}) are used for reading data.
 * Listeners are attached, and they will be triggered when the corresponding data changes. <br>
 * <br>
 * Instances of Query are obtained by calling startAt(), endAt(), or limit() on a DatabaseReference.
 */
public class Query {

  /** @hide */
  protected final Repo repo;
  /** @hide */
  protected final Path path;
  /** @hide */
  protected final QueryParams params;
  // we can't use params index, because the default query params have priority index set as default,
  // but we don't want to allow multiple orderByPriority calls, so track them here
  private final boolean orderByCalled;

  Query(Repo repo, Path path, QueryParams params, boolean orderByCalled) throws DatabaseException {
    this.repo = repo;
    this.path = path;
    this.params = params;
    this.orderByCalled = orderByCalled;
    hardAssert(params.isValid(), "Validation of queries failed.");
  }

  Query(Repo repo, Path path) {
    this.repo = repo;
    this.path = path;
    this.params = QueryParams.DEFAULT_PARAMS;
    this.orderByCalled = false;
  }

  /**
   * This method validates that key index has been called with the correct combination of parameters
   */
  private void validateQueryEndpoints(QueryParams params) {
    if (params.getIndex().equals(KeyIndex.getInstance())) {
      String message =
          "You must use startAt(String value), endAt(String value) or "
              + "equalTo(String value) in combination with orderByKey(). Other type of values or "
              + "using the version with 2 parameters is not supported";
      if (params.hasStart()) {
        Node startNode = params.getIndexStartValue();
        ChildKey startName = params.getIndexStartName();
        if (!Objects.equal(startName, ChildKey.getMinName())
            || !(startNode instanceof StringNode)) {
          throw new IllegalArgumentException(message);
        }
      }
      if (params.hasEnd()) {
        Node endNode = params.getIndexEndValue();
        ChildKey endName = params.getIndexEndName();
        if (!endName.equals(ChildKey.getMaxName()) || !(endNode instanceof StringNode)) {
          throw new IllegalArgumentException(message);
        }
      }
    } else if (params.getIndex().equals(PriorityIndex.getInstance())) {
      if ((params.hasStart() && !PriorityUtilities.isValidPriority(params.getIndexStartValue()))
          || (params.hasEnd() && !PriorityUtilities.isValidPriority(params.getIndexEndValue()))) {
        throw new IllegalArgumentException(
            "When using orderByPriority(), values provided to startAt(), "
                + "endAt(), or equalTo() must be valid priorities.");
      }
    }
  }

  /** This method validates that limit has been called with the correct combination or parameters */
  private void validateLimit(QueryParams params) {
    if (params.hasStart() && params.hasEnd() && params.hasLimit() && !params.hasAnchoredLimit()) {
      throw new IllegalArgumentException(
          "Can't combine startAt(), endAt() and limit(). "
              + "Use limitToFirst() or limitToLast() instead");
    }
  }

  /** This method validates that the equalTo call can be made */
  private void validateEqualToCall() {
    if (params.hasStart()) {
      throw new IllegalArgumentException("Can't call equalTo() and startAt() combined");
    }
    if (params.hasEnd()) {
      throw new IllegalArgumentException("Can't call equalTo() and endAt() combined");
    }
  }

  /** This method validates that only one order by call has been made */
  private void validateNoOrderByCall() {
    if (this.orderByCalled) {
      throw new IllegalArgumentException("You can't combine multiple orderBy calls!");
    }
  }

  /**
   * Add a listener for changes in the data at this location. Each time time the data changes, your
   * listener will be called with an immutable snapshot of the data.
   *
   * @param listener The listener to be called with changes
   * @return A reference to the listener provided. Save this to remove the listener later.
   */
  @NonNull
  public ValueEventListener addValueEventListener(@NonNull ValueEventListener listener) {
    addEventRegistration(new ValueEventRegistration(repo, listener, getSpec()));
    return listener;
  }

  /**
   * Add a listener for child events occurring at this location. When child locations are added,
   * removed, changed, or moved, the listener will be triggered for the appropriate event
   *
   * @param listener The listener to be called with changes
   * @return A reference to the listener provided. Save this to remove the listener later.
   */
  @NonNull
  public ChildEventListener addChildEventListener(@NonNull ChildEventListener listener) {
    addEventRegistration(new ChildEventRegistration(repo, listener, getSpec()));
    return listener;
  }

  /**
   * Gets the server values for this query. Updates the cache and raises events if successful. If
   * not connected, falls back to a locally-cached value.
   */
  @NonNull
  public Task<DataSnapshot> get() {
    return repo.getValue(this);
  }

  /**
   * Add a listener for a single change in the data at this location. This listener will be
   * triggered once with the value of the data at the location.
   *
   * @param listener The listener to be called with the data
   */
  public void addListenerForSingleValueEvent(@NonNull final ValueEventListener listener) {
    addEventRegistration(
        new ValueEventRegistration(
            repo,
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                // Removing the event listener will also prevent any further calls into onDataChange
                removeEventListener(this);
                listener.onDataChange(snapshot);
              }

              @Override
              public void onCancelled(DatabaseError error) {
                listener.onCancelled(error);
              }
            },
            getSpec()));
  }

  /**
   * Remove the specified listener from this location.
   *
   * @param listener The listener to remove
   */
  public void removeEventListener(@NonNull final ValueEventListener listener) {
    if (listener == null) {
      throw new NullPointerException("listener must not be null");
    }
    removeEventRegistration(new ValueEventRegistration(repo, listener, getSpec()));
  }

  /**
   * Remove the specified listener from this location.
   *
   * @param listener The listener to remove
   */
  public void removeEventListener(@NonNull final ChildEventListener listener) {
    if (listener == null) {
      throw new NullPointerException("listener must not be null");
    }
    removeEventRegistration(new ChildEventRegistration(repo, listener, getSpec()));
  }

  private void removeEventRegistration(final EventRegistration registration) {
    ZombieEventManager.getInstance().zombifyForRemove(registration);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.removeEventCallback(registration);
          }
        });
  }

  private void addEventRegistration(final EventRegistration listener) {
    ZombieEventManager.getInstance().recordEventRegistration(listener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.addEventCallback(listener);
          }
        });
  }

  /**
   * By calling `keepSynced(true)` on a location, the data for that location will automatically be
   * downloaded and kept in sync, even when no listeners are attached for that location.
   * Additionally, while a location is kept synced, it will not be evicted from the persistent disk
   * cache.
   *
   * @since 2.3
   * @param keepSynced Pass `true` to keep this location synchronized, pass `false` to stop
   *     synchronization.
   */
  public void keepSynced(final boolean keepSynced) {
    if (!this.path.isEmpty() && this.path.getFront().equals(ChildKey.getInfoKey())) {
      throw new DatabaseException("Can't call keepSynced() on .info paths.");
    }

    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.keepSynced(getSpec(), keepSynced);
          }
        });
  }

  /** Removes all of the event listeners at this location */
  /*public void removeAllEventListeners() {
      Query.scheduleNow(new Runnable() {

          @Override
          public void run() {
              repo.removeEventCallback(Query.this, null);
          }
      });
  }*/

  /**
   * Create a query constrained to only return child nodes with a value greater than or equal to the
   * given value, using the given orderBy directive or priority as default.
   *
   * @param value The value to start at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query startAt(@Nullable String value) {
    return startAt(value, null);
  }

  /**
   * Create a query constrained to only return child nodes with a value greater than or equal to the
   * given value, using the given orderBy directive or priority as default.
   *
   * @param value The value to start at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query startAt(double value) {
    return startAt(value, null);
  }

  /**
   * Create a query constrained to only return child nodes with a value greater than or equal to the
   * given value, using the given orderBy directive or priority as default.
   *
   * @param value The value to start at, inclusive
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query startAt(boolean value) {
    return startAt(value, null);
  }

  /**
   * Create a query constrained to only return child nodes with a value greater than or equal to the
   * given value, using the given orderBy directive or priority as default, and additionally only
   * child nodes with a key greater than or equal to the given key.
   *
   * @param value The priority to start at, inclusive
   * @param key The key to start at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query startAt(@Nullable String value, @Nullable String key) {
    Node node =
        value != null ? new StringNode(value, PriorityUtilities.NullPriority()) : EmptyNode.Empty();
    return startAt(node, key);
  }

  /**
   * Create a query constrained to only return child nodes with a value greater than or equal to the
   * given value, using the given orderBy directive or priority as default, and additionally only
   * child nodes with a key greater than or equal to the given key.
   *
   * @param value The priority to start at, inclusive
   * @param key The key name to start at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query startAt(double value, @Nullable String key) {
    return startAt(new DoubleNode(value, PriorityUtilities.NullPriority()), key);
  }

  /**
   * Create a query constrained to only return child nodes with a value greater than or equal to the
   * given value, using the given orderBy directive or priority as default, and additionally only
   * child nodes with a key greater than or equal to the given key.
   *
   * @param value The priority to start at, inclusive
   * @param key The key to start at, inclusive
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query startAt(boolean value, @Nullable String key) {
    return startAt(new BooleanNode(value, PriorityUtilities.NullPriority()), key);
  }

  private Query startAt(Node node, String key) {
    Validation.validateNullableKey(key);
    if (!(node.isLeafNode() || node.isEmpty())) {
      throw new IllegalArgumentException("Can only use simple values for startAt()");
    }
    if (params.hasStart()) {
      throw new IllegalArgumentException("Can't call startAt() or equalTo() multiple times");
    }
    ChildKey childKey = key != null ? ChildKey.fromString(key) : null;
    QueryParams newParams = params.startAt(node, childKey);
    validateLimit(newParams);
    validateQueryEndpoints(newParams);
    hardAssert(newParams.isValid());
    return new Query(repo, path, newParams, orderByCalled);
  }

  /**
   * Create a query constrained to only return child nodes with a value less than or equal to the
   * given value, using the given orderBy directive or priority as default.
   *
   * @param value The value to end at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query endAt(@Nullable String value) {
    return endAt(value, null);
  }

  /**
   * Create a query constrained to only return child nodes with a value less than or equal to the
   * given value, using the given orderBy directive or priority as default.
   *
   * @param value The value to end at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query endAt(double value) {
    return endAt(value, null);
  }

  /**
   * Create a query constrained to only return child nodes with a value less than or equal to the
   * given value, using the given orderBy directive or priority as default.
   *
   * @param value The value to end at, inclusive
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query endAt(boolean value) {
    return endAt(value, null);
  }

  /**
   * Create a query constrained to only return child nodes with a value less than or equal to the
   * given value, using the given orderBy directive or priority as default, and additionally only
   * child nodes with a key key less than or equal to the given key.
   *
   * @param value The value to end at, inclusive
   * @param key The key to end at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query endAt(@Nullable String value, @Nullable String key) {
    Node node =
        value != null ? new StringNode(value, PriorityUtilities.NullPriority()) : EmptyNode.Empty();
    return endAt(node, key);
  }

  /**
   * Create a query constrained to only return child nodes with a value less than or equal to the
   * given value, using the given orderBy directive or priority as default, and additionally only
   * child nodes with a key less than or equal to the given key.
   *
   * @param value The value to end at, inclusive
   * @param key The key to end at, inclusive
   * @return A Query with the new constraint
   */
  @NonNull
  public Query endAt(double value, @Nullable String key) {
    return endAt(new DoubleNode(value, PriorityUtilities.NullPriority()), key);
  }

  /**
   * Create a query constrained to only return child nodes with a value less than or equal to the
   * given value, using the given orderBy directive or priority as default, and additionally only
   * child nodes with a key less than or equal to the given key.
   *
   * @param value The value to end at, inclusive
   * @param key The key to end at, inclusive
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query endAt(boolean value, @Nullable String key) {
    return endAt(new BooleanNode(value, PriorityUtilities.NullPriority()), key);
  }

  private Query endAt(Node node, String key) {
    Validation.validateNullableKey(key);
    if (!(node.isLeafNode() || node.isEmpty())) {
      throw new IllegalArgumentException("Can only use simple values for endAt()");
    }
    ChildKey childKey = key != null ? ChildKey.fromString(key) : null;
    if (params.hasEnd()) {
      throw new IllegalArgumentException("Can't call endAt() or equalTo() multiple times");
    }
    QueryParams newParams = params.endAt(node, childKey);
    validateLimit(newParams);
    validateQueryEndpoints(newParams);
    hardAssert(newParams.isValid());
    return new Query(repo, path, newParams, orderByCalled);
  }

  /**
   * Create a query constrained to only return child nodes with the given value
   *
   * @param value The value to query for
   * @return A query with the new constraint
   */
  @NonNull
  public Query equalTo(@Nullable String value) {
    validateEqualToCall();
    return this.startAt(value).endAt(value);
  }

  /**
   * Create a query constrained to only return child nodes with the given value
   *
   * @param value The value to query for
   * @return A query with the new constraint
   */
  @NonNull
  public Query equalTo(double value) {
    validateEqualToCall();
    return this.startAt(value).endAt(value);
  }

  /**
   * Create a query constrained to only return child nodes with the given value.
   *
   * @param value The value to query for
   * @return A query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query equalTo(boolean value) {
    validateEqualToCall();
    return this.startAt(value).endAt(value);
  }

  /**
   * Create a query constrained to only return the child node with the given key and value. Note
   * that there is at most one such child as names are unique.
   *
   * @param value The value to query for
   * @param key The key of the child
   * @return A query with the new constraint
   */
  @NonNull
  public Query equalTo(@Nullable String value, @Nullable String key) {
    validateEqualToCall();
    return this.startAt(value, key).endAt(value, key);
  }

  /**
   * Create a query constrained to only return the child node with the given key and value. Note
   * that there is at most one such child as keys are unique.
   *
   * @param value The value to query for
   * @param key The key of the child
   * @return A query with the new constraint
   */
  @NonNull
  public Query equalTo(double value, @Nullable String key) {
    validateEqualToCall();
    return this.startAt(value, key).endAt(value, key);
  }

  /**
   * Create a query constrained to only return the child node with the given key and value. Note
   * that there is at most one such child as keys are unique.
   *
   * @param value The value to query for
   * @param key The name of the child
   * @return A query with the new constraint
   */
  @NonNull
  public Query equalTo(boolean value, @Nullable String key) {
    validateEqualToCall();
    return this.startAt(value, key).endAt(value, key);
  }

  /**
   * Create a query with limit and anchor it to the start of the window
   *
   * @param limit The maximum number of child nodes to return
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query limitToFirst(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be a positive integer!");
    }
    if (params.hasLimit()) {
      throw new IllegalArgumentException(
          "Can't call limitToLast on query with previously set limit!");
    }
    return new Query(repo, path, params.limitToFirst(limit), orderByCalled);
  }

  /**
   * Create a query with limit and anchor it to the end of the window
   *
   * @param limit The maximum number of child nodes to return
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query limitToLast(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be a positive integer!");
    }
    if (params.hasLimit()) {
      throw new IllegalArgumentException(
          "Can't call limitToLast on query with previously set limit!");
    }
    return new Query(repo, path, params.limitToLast(limit), orderByCalled);
  }

  /**
   * Create a query in which child nodes are ordered by the values of the specified path.
   *
   * @param path The path to the child node to use for sorting
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query orderByChild(@NonNull String path) {
    if (path == null) {
      throw new NullPointerException("Key can't be null");
    }
    if (path.equals("$key") || path.equals(".key")) {
      throw new IllegalArgumentException(
          "Can't use '" + path + "' as path, please use orderByKey() instead!");
    }
    if (path.equals("$priority") || path.equals(".priority")) {
      throw new IllegalArgumentException(
          "Can't use '" + path + "' as path, please use orderByPriority() instead!");
    }
    if (path.equals("$value") || path.equals(".value")) {
      throw new IllegalArgumentException(
          "Can't use '" + path + "' as path, please use orderByValue() instead!");
    }
    Validation.validatePathString(path);
    validateNoOrderByCall();
    Path indexPath = new Path(path);
    if (indexPath.size() == 0) {
      throw new IllegalArgumentException("Can't use empty path, use orderByValue() instead!");
    }
    Index index = new PathIndex(indexPath);
    return new Query(repo, this.path, params.orderBy(index), true);
  }

  /**
   * Create a query in which child nodes are ordered by their priorities.
   *
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query orderByPriority() {
    validateNoOrderByCall();
    QueryParams newParams = params.orderBy(PriorityIndex.getInstance());
    validateQueryEndpoints(newParams);
    return new Query(repo, path, newParams, true);
  }

  /**
   * Create a query in which child nodes are ordered by their keys.
   *
   * @return A Query with the new constraint
   * @since 2.0
   */
  @NonNull
  public Query orderByKey() {
    validateNoOrderByCall();
    QueryParams newParams = this.params.orderBy(KeyIndex.getInstance());
    validateQueryEndpoints(newParams);
    return new Query(repo, path, newParams, true);
  }

  /**
   * Create a query in which nodes are ordered by their value
   *
   * @return A Query with the new constraint
   * @since 2.2
   */
  @NonNull
  public Query orderByValue() {
    validateNoOrderByCall();
    return new Query(repo, path, params.orderBy(ValueIndex.getInstance()), true);
  }

  /** @return A DatabaseReference to this location */
  @NonNull
  public DatabaseReference getRef() {
    return new DatabaseReference(repo, getPath());
  }

  // Need to hide these...

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @return The path to this location
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public Path getPath() {
    return path;
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @return The repo
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public Repo getRepo() {
    return repo;
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @return The constraints
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public QuerySpec getSpec() {
    return new QuerySpec(path, params);
  }
}
