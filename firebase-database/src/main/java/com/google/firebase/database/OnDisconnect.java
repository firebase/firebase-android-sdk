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

import static com.google.firebase.database.DatabaseReference.CompletionListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.ValidationPath;
import com.google.firebase.database.core.utilities.Pair;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.core.utilities.Validation;
import com.google.firebase.database.core.utilities.encoding.CustomClassMapper;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityUtilities;
import java.util.Map;

/**
 * The OnDisconnect class is used to manage operations that will be run on the server when this
 * client disconnects. It can be used to add or remove data based on a client's connection status.
 * It is very useful in applications looking for 'presence' functionality. <br>
 * <br>
 * Instances of this class are obtained by calling {@link DatabaseReference#onDisconnect()
 * onDisconnect} on a Firebase Database ref.
 */
public class OnDisconnect {

  private Repo repo;
  private Path path;

  OnDisconnect(Repo repo, Path path) {
    this.repo = repo;
    this.path = path;
  }

  /**
   * Ensure the data at this location is set to the specified value when the client is disconnected
   * (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> setValue(@Nullable Object value) {
    return onDisconnectSetInternal(value, PriorityUtilities.NullPriority(), null);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @param priority The priority to be set when a disconnect occurs or null to clear the existing
   *     priority
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> setValue(@Nullable Object value, @Nullable String priority) {
    return onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), null);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @param priority The priority to be set when a disconnect occurs
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> setValue(@Nullable Object value, double priority) {
    return onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), null);
  }

  /**
   * Ensure the data at this location is set to the specified value when the client is disconnected
   * (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(@Nullable Object value, @Nullable CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.NullPriority(), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @param priority The priority to be set when a disconnect occurs or null to clear the existing
   *     priority
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(
      @Nullable Object value, @Nullable String priority, @Nullable CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @param priority The priority to be set when a disconnect occurs
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(
      @Nullable Object value, double priority, @Nullable CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs or null to delete the existing value
   * @param priority The priority to be set when a disconnect occurs
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(
      @Nullable Object value, @Nullable Map priority, @Nullable CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), listener);
  }

  private Task<Void> onDisconnectSetInternal(
      Object value, Node priority, final CompletionListener optListener) {
    Validation.validateWritablePath(path);
    ValidationPath.validateWithObject(path, value);
    Object bouncedValue = CustomClassMapper.convertToPlainJavaTypes(value);
    Validation.validateWritableObject(bouncedValue);
    final Node node = NodeUtilities.NodeFromJSON(bouncedValue, priority);
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.onDisconnectSetValue(path, node, wrapped.getSecond());
          }
        });
    return wrapped.getFirst();
  }

  // Update

  /**
   * Ensure the data has the specified child values updated when the client is disconnected
   *
   * @param update The paths to update, along with their desired values
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> updateChildren(@NonNull Map<String, Object> update) {
    return updateChildrenInternal(update, null);
  }

  /**
   * Ensure the data has the specified child values updated when the client is disconnected
   *
   * @param update The paths to update, along with their desired values
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void updateChildren(
      @NonNull final Map<String, Object> update, @Nullable final CompletionListener listener) {
    updateChildrenInternal(update, listener);
  }

  private Task<Void> updateChildrenInternal(
      final Map<String, Object> update, final CompletionListener optListener) {
    final Map<Path, Node> parsedUpdate = Validation.parseAndValidateUpdate(path, update);
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.onDisconnectUpdate(path, parsedUpdate, wrapped.getSecond(), update);
          }
        });
    return wrapped.getFirst();
  }

  // Remove

  /**
   * Remove the value at this location when the client disconnects
   *
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> removeValue() {
    return setValue(null);
  }

  /**
   * Remove the value at this location when the client disconnects
   *
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void removeValue(@Nullable CompletionListener listener) {
    setValue(null, listener);
  }

  // Cancel the operation

  /**
   * Cancel any disconnect operations that are queued up at this location
   *
   * @return The {@link Task} for this operation.
   */
  @NonNull
  public Task<Void> cancel() {
    return cancelInternal(null);
  }

  /**
   * Cancel any disconnect operations that are queued up at this location
   *
   * @param listener A listener that will be triggered once the server has cancelled the operations
   */
  public void cancel(@NonNull final CompletionListener listener) {
    cancelInternal(listener);
  }

  private Task<Void> cancelInternal(final CompletionListener optListener) {
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.onDisconnectCancel(path, wrapped.getSecond());
          }
        });
    return wrapped.getFirst();
  }
}
