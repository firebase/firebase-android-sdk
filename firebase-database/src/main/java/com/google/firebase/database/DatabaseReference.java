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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.core.ValidationPath;
import com.google.firebase.database.core.utilities.Pair;
import com.google.firebase.database.core.utilities.ParsedUrl;
import com.google.firebase.database.core.utilities.PushIdGenerator;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.core.utilities.Validation;
import com.google.firebase.database.core.utilities.encoding.CustomClassMapper;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityUtilities;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * A Firebase reference represents a particular location in your Database and can be used for
 * reading or writing data to that Database location.
 *
 * <p>This class is the starting point for all Database operations. After you've initialized it with
 * a URL, you can use it to read data, write data, and to create new DatabaseReferences.
 */
public class DatabaseReference extends Query {

  private static DatabaseConfig defaultConfig;

  /**
   * This interface is used as a method of being notified when an operation has been acknowledged by
   * the Database servers and can be considered complete
   *
   * @since 1.1
   */
  public interface CompletionListener {

    /**
     * This method will be triggered when the operation has either succeeded or failed. If it has
     * failed, an error will be given. If it has succeeded, the error will be null
     *
     * @param error A description of any errors that occurred or null on success
     * @param ref A reference to the specified Firebase Database location
     */
    public void onComplete(
        @Nullable final DatabaseError error, @NonNull final DatabaseReference ref);
  }

  /**
   * @param repo The repo for this ref
   * @param path The path to reference
   */
  DatabaseReference(Repo repo, Path path) {
    super(repo, path);
  }

  /** Legacy method left here (as package private) for tests. */
  DatabaseReference(String url, DatabaseConfig config) {
    this(Utilities.parseUrl(url), config);
  }

  private DatabaseReference(ParsedUrl parsedUrl, DatabaseConfig config) {
    this(RepoManager.getRepo(config, parsedUrl.repoInfo), parsedUrl.path);
  }

  /**
   * Get a reference to location relative to this one
   *
   * @param pathString The relative path from this reference to the new one that should be created
   * @return A new DatabaseReference to the given path
   */
  @NonNull
  public DatabaseReference child(@NonNull String pathString) {
    if (pathString == null) {
      throw new NullPointerException("Can't pass null for argument 'pathString' in child()");
    }
    if (getPath().isEmpty()) {
      // If this is the root of the tree, allow '.info' nodes.
      Validation.validateRootPathString(pathString);
    } else {
      Validation.validatePathString(pathString);
    }
    Path childPath = getPath().child(new Path(pathString));
    return new DatabaseReference(repo, childPath);
  }

  /**
   * Create a reference to an auto-generated child location. The child key is generated client-side
   * and incorporates an estimate of the server's time for sorting purposes. Locations generated on
   * a single client will be sorted in the order that they are created, and will be sorted
   * approximately in order across all clients.
   *
   * @return A DatabaseReference pointing to the new location
   */
  @NonNull
  public DatabaseReference push() {
    String childNameStr = PushIdGenerator.generatePushChildName(repo.getServerTime());
    ChildKey childKey = ChildKey.fromString(childNameStr);
    return new DatabaseReference(repo, getPath().child(childKey));
  }

  /**
   * Set the data at this location to the given value. Passing null to setValue() will delete the
   * data at the specified location. The native types accepted by this method for the value
   * correspond to the JSON types:
   *
   * <ul>
   *   <li>Boolean
   *   <li>Long
   *   <li>Double
   *   <li>String
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * <br>
   * <br>
   * In addition, you can set instances of your own class into this location, provided they satisfy
   * the following constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * <br>
   * <br>
   * Generic collections of objects that satisfy the above constraints are also permitted, i.e.
   * <code>Map&lt;String, MyPOJO&gt;</code>, as well as null values.
   *
   * @param value The value to set at this location or null to delete the existing data
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> setValue(@Nullable Object value) {
    return setValueInternal(value, PriorityUtilities.parsePriority(this.path, null), null);
  }

  /**
   * Set the data and priority to the given values. Passing null to setValue() will delete the data
   * at the specified location. The native types accepted by this method for the value correspond to
   * the JSON types:
   *
   * <ul>
   *   <li>Boolean
   *   <li>Long
   *   <li>Double
   *   <li>String
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * <br>
   * <br>
   * In addition, you can set instances of your own class into this location, provided they satisfy
   * the following constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * <br>
   * <br>
   * Generic collections of objects that satisfy the above constraints are also permitted, i.e.
   * <code>Map&lt;String, MyPOJO&gt;</code>, as well as null values.
   *
   * @param value The value to set at this location or null to delete the existing data
   * @param priority The priority to set at this location or null to clear the existing priority
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> setValue(@Nullable Object value, @Nullable Object priority) {
    return setValueInternal(value, PriorityUtilities.parsePriority(this.path, priority), null);
  }

  /**
   * Set the data at this location to the given value. Passing null to setValue() will delete the
   * data at the specified location. The native types accepted by this method for the value
   * correspond to the JSON types:
   *
   * <ul>
   *   <li>Boolean
   *   <li>Long
   *   <li>Double
   *   <li>String
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * <br>
   * <br>
   * In addition, you can set instances of your own class into this location, provided they satisfy
   * the following constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * <br>
   * <br>
   * Generic collections of objects that satisfy the above constraints are also permitted, i.e.
   * <code>Map&lt;String, MyPOJO&gt;</code>, as well as null values.
   *
   * @param value The value to set at this location or null to delete the existing data
   * @param listener A listener that will be triggered with the results of the operation
   */
  public void setValue(@Nullable Object value, @Nullable CompletionListener listener) {
    setValueInternal(value, PriorityUtilities.parsePriority(this.path, null), listener);
  }

  /**
   * Set the data and priority to the given values. The native types accepted by this method for the
   * value correspond to the JSON types:
   *
   * <ul>
   *   <li>Boolean
   *   <li>Long
   *   <li>Double
   *   <li>String
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * <br>
   * <br>
   * In addition, you can set instances of your own class into this location, provided they satisfy
   * the following constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * <br>
   * <br>
   * Generic collections of objects that satisfy the above constraints are also permitted, i.e.
   * <code>Map&lt;String, MyPOJO&gt;</code>, as well as null values.
   *
   * @param value The value to set at this location or null to delete the existing data
   * @param priority The priority to set at this location or null to clear the existing priority
   * @param listener A listener that will be triggered with the results of the operation
   */
  public void setValue(
      @Nullable Object value, @Nullable Object priority, @Nullable CompletionListener listener) {
    setValueInternal(value, PriorityUtilities.parsePriority(this.path, priority), listener);
  }

  private Task<Void> setValueInternal(Object value, Node priority, CompletionListener optListener) {
    Validation.validateWritablePath(getPath());
    ValidationPath.validateWithObject(getPath(), value);
    Object bouncedValue = CustomClassMapper.convertToPlainJavaTypes(value);
    Validation.validateWritableObject(bouncedValue);
    final Node node = NodeUtilities.NodeFromJSON(bouncedValue, priority);
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.setValue(getPath(), node, wrapped.getSecond());
          }
        });
    return wrapped.getFirst();
  }

  // Set priority
  /**
   * Set a priority for the data at this Database location. Priorities can be used to provide a
   * custom ordering for the children at a location (if no priorities are specified, the children
   * are ordered by key). <br>
   * <br>
   * You cannot set a priority on an empty location. For this reason setValue(data, priority) should
   * be used when setting initial data with a specific priority and setPriority should be used when
   * updating the priority of existing data. <br>
   * <br>
   * Children are sorted based on this priority using the following rules:
   *
   * <ul>
   *   <li>Children with no priority come first.
   *   <li>Children with a number as their priority come next. They are sorted numerically by
   *       priority (small to large).
   *   <li>Children with a string as their priority come last. They are sorted lexicographically by
   *       priority.
   *   <li>Whenever two children have the same priority (including no priority), they are sorted by
   *       key. Numeric keys come first (sorted numerically), followed by the remaining keys (sorted
   *       lexicographically).
   * </ul>
   *
   * Note that numerical priorities are parsed and ordered as IEEE 754 double-precision
   * floating-point numbers. Keys are always stored as strings and are treated as numeric only when
   * they can be parsed as a 32-bit integer.
   *
   * @param priority The priority to set at the specified location or null to clear the existing
   *     priority
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> setPriority(@Nullable Object priority) {
    return setPriorityInternal(PriorityUtilities.parsePriority(this.path, priority), null);
  }

  /**
   * Set a priority for the data at this Database location. Priorities can be used to provide a
   * custom ordering for the children at a location (if no priorities are specified, the children
   * are ordered by key). <br>
   * <br>
   * You cannot set a priority on an empty location. For this reason setValue(data, priority) should
   * be used when setting initial data with a specific priority and setPriority should be used when
   * updating the priority of existing data. <br>
   * <br>
   * Children are sorted based on this priority using the following rules:
   *
   * <ul>
   *   <li>Children with no priority come first.
   *   <li>Children with a number as their priority come next. They are sorted numerically by
   *       priority (small to large).
   *   <li>Children with a string as their priority come last. They are sorted lexicographically by
   *       priority.
   *   <li>Whenever two children have the same priority (including no priority), they are sorted by
   *       key. Numeric keys come first (sorted numerically), followed by the remaining keys (sorted
   *       lexicographically).
   * </ul>
   *
   * Note that numerical priorities are parsed and ordered as IEEE 754 double-precision
   * floating-point numbers. Keys are always stored as strings and are treated as numeric only when
   * they can be parsed as a 32-bit integer.
   *
   * @param priority The priority to set at the specified location or null to clear the existing
   *     priority
   * @param listener A listener that will be triggered with results of the operation
   */
  public void setPriority(@Nullable Object priority, @Nullable CompletionListener listener) {
    setPriorityInternal(PriorityUtilities.parsePriority(this.path, priority), listener);
  }

  private Task<Void> setPriorityInternal(final Node priority, CompletionListener optListener) {
    Validation.validateWritablePath(getPath());

    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.setValue(
                getPath().child(ChildKey.getPriorityKey()), priority, wrapped.getSecond());
          }
        });
    return wrapped.getFirst();
  }

  // Update

  /**
   * Update the specific child keys to the specified values. Passing null in a map to
   * updateChildren() will remove the value at the specified location.
   *
   * @param update The paths to update and their new values
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> updateChildren(@NonNull Map<String, Object> update) {
    return updateChildrenInternal(update, null);
  }

  /**
   * Update the specific child keys to the specified values. Passing null in a map to
   * updateChildren() will remove the value at the specified location.
   *
   * @param update The paths to update and their new values
   * @param listener A listener that will be triggered with results of the operation
   */
  public void updateChildren(
      @NonNull final Map<String, Object> update, @Nullable final CompletionListener listener) {
    updateChildrenInternal(update, listener);
  }

  private Task<Void> updateChildrenInternal(
      final Map<String, Object> update, final CompletionListener optListener) {
    if (update == null) {
      throw new NullPointerException("Can't pass null for argument 'update' in updateChildren()");
    }
    final Map<String, Object> bouncedUpdate = CustomClassMapper.convertToPlainJavaTypes(update);
    final Map<Path, Node> parsedUpdate =
        Validation.parseAndValidateUpdate(getPath(), bouncedUpdate);
    final CompoundWrite merge = CompoundWrite.fromPathMerge(parsedUpdate);

    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.updateChildren(getPath(), merge, wrapped.getSecond(), bouncedUpdate);
          }
        });
    return wrapped.getFirst();
  }

  // Remove

  /**
   * Set the value at this location to 'null'
   *
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> removeValue() {
    return setValue(null);
  }

  /**
   * Set the value at this location to 'null'
   *
   * @param listener A listener that will be triggered when the operation is complete
   */
  public void removeValue(@Nullable CompletionListener listener) {
    setValue(null, listener);
  }

  // Access to disconnect operations

  /**
   * Provides access to disconnect operations at this location
   *
   * @return An object for managing disconnect operations at this location
   */
  @NonNull
  public OnDisconnect onDisconnect() {
    Validation.validateWritablePath(getPath());
    return new OnDisconnect(repo, getPath());
  }

  // Transactions

  /**
   * Run a transaction on the data at this location. For more information on running transactions,
   * see {@link com.google.firebase.database.Transaction.Handler Transaction.Handler}.
   *
   * @param handler An object to handle running the transaction
   */
  public void runTransaction(@NonNull Transaction.Handler handler) {
    runTransaction(handler, true);
  }

  /**
   * Run a transaction on the data at this location. For more information on running transactions,
   * see {@link com.google.firebase.database.Transaction.Handler Transaction.Handler}.
   *
   * @param handler An object to handle running the transaction
   * @param fireLocalEvents Defaults to true. If set to false, events will only be fired for the
   *     final result state of the transaction, and not for any intermediate states
   */
  public void runTransaction(
      @NonNull final Transaction.Handler handler, final boolean fireLocalEvents) {
    if (handler == null) {
      throw new NullPointerException("Can't pass null for argument 'handler' in runTransaction()");
    }
    Validation.validateWritablePath(getPath());
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.startTransaction(getPath(), handler, fireLocalEvents);
          }
        });
  }

  // Manual Connection Management

  /**
   * The Firebase Database client automatically maintains a persistent connection to the Database
   * server, which will remain active indefinitely and reconnect when disconnected. However, the
   * goOffline( ) and goOnline( ) methods may be used to manually control the client connection in
   * cases where a persistent connection is undesirable.
   *
   * <p>While offline, the Firebase Database client will no longer receive data updates from the
   * server. However, all Database operations performed locally will continue to immediately fire
   * events, allowing your application to continue behaving normally. Additionally, each operation
   * performed locally will automatically be queued and retried upon reconnection to the Database
   * server.
   *
   * <p>To reconnect to the Database server and begin receiving remote events, see goOnline( ). Once
   * the connection is reestablished, the Database client will transmit the appropriate data and
   * fire the appropriate events so that your client "catches up" automatically.
   */

  /**
   * Manually disconnect the Firebase Database client from the server and disable automatic
   * reconnection.
   *
   * <p>Note: Invoking this method will impact all Firebase Database connections.
   */
  public static void goOffline() {
    goOffline(getDefaultConfig());
  }

  static void goOffline(DatabaseConfig config) {
    RepoManager.interrupt(config);
  }

  /**
   * Manually reestablish a connection to the Firebase Database server and enable automatic
   * reconnection.
   *
   * <p>Note: Invoking this method will impact all Firebase Database connections.
   */
  public static void goOnline() {
    goOnline(getDefaultConfig());
  }

  static void goOnline(DatabaseConfig config) {
    RepoManager.resume(config);
  }

  // Getters and other auxiliary methods

  /**
   * Gets the Database instance associated with this reference.
   *
   * @return The Database object for this reference.
   */
  @NonNull
  public FirebaseDatabase getDatabase() {
    return this.repo.getDatabase();
  }

  /** @return The full location url for this reference */
  @Override
  public String toString() {
    DatabaseReference parent = getParent();
    if (parent == null) {
      return repo.toString();
    } else {
      try {
        return parent.toString() + "/" + URLEncoder.encode(getKey(), "UTF-8").replace("+", "%20");
      } catch (UnsupportedEncodingException e) {
        throw new DatabaseException("Failed to URLEncode key: " + getKey(), e);
      }
    }
  }

  /**
   * @return A DatabaseReference to the parent location, or null if this instance references the
   *     root location
   */
  @Nullable
  public DatabaseReference getParent() {
    Path parentPath = getPath().getParent();
    if (parentPath != null) {
      return new DatabaseReference(repo, parentPath);
    } else {
      return null;
    }
  }

  /** @return A reference to the root location of this Firebase Database */
  @NonNull
  public DatabaseReference getRoot() {
    return new DatabaseReference(repo, new Path(""));
  }

  /**
   * @return The last token in the location pointed to by this reference or null if this reference
   *     points to the database root
   */
  @Nullable
  public String getKey() {
    if (getPath().isEmpty()) {
      return null;
    }
    return getPath().getBack().asString();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DatabaseReference && toString().equals(other.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  void setHijackHash(final boolean hijackHash) {
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.setHijackHash(hijackHash);
          }
        });
  }

  /**
   * Legacy method for legacy creation of DatabaseReference for tests.
   *
   * @return A reference to the default config object. This can be modified up until your first
   *     Database call
   */
  private static synchronized DatabaseConfig getDefaultConfig() {
    if (defaultConfig == null) {
      defaultConfig = new DatabaseConfig();
    }
    return defaultConfig;
  }
}
