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

package com.google.firebase.crashlytics.internal.metadata;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.CrashlyticsBackgroundWorker;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages keys and user-specific metadata set by the user, including serializing metadata to disk
 * as needed, so it can be retrieved and attached to a crash report, in case of a non-graceful app
 * exit.
 */
public class UserMetadata {

  public static final String USERDATA_FILENAME = "user-data";
  public static final String KEYDATA_FILENAME = "keys";
  public static final String INTERNAL_KEYDATA_FILENAME = "internal-keys";

  public static final String ROLLOUTS_STATE_FILENAME = "rollouts-state";

  @VisibleForTesting public static final int MAX_ATTRIBUTES = 64;
  @VisibleForTesting public static final int MAX_ATTRIBUTE_SIZE = 1024;
  @VisibleForTesting public static final int MAX_INTERNAL_KEY_SIZE = 8192;

  @VisibleForTesting public static final int MAX_ROLLOUT_ASSIGNMENTS = 128;

  private final MetaDataStore metaDataStore;
  private final CrashlyticsBackgroundWorker backgroundWorker;
  private String sessionIdentifier;

  // The following references contain a marker bit, which is true if the data maintained in the
  // associated reference has been serialized since the last time it was updated.
  // Design note: Though these are backed by AtomicMarkableReference classes, we occassionally
  // need to synchronize on the objects themselves, as not all required operations are avaialable
  // as atomic APIs.
  private final SerializeableKeysMap customKeys = new SerializeableKeysMap(false);
  private final SerializeableKeysMap internalKeys = new SerializeableKeysMap(true);

  private final RolloutAssignmentList rolloutsState =
      new RolloutAssignmentList(MAX_ROLLOUT_ASSIGNMENTS);

  private final AtomicMarkableReference<String> userId = new AtomicMarkableReference<>(null, false);

  @Nullable
  public static String readUserId(String sessionId, FileStore fileStore) {
    return new MetaDataStore(fileStore).readUserId(sessionId);
  }

  public static UserMetadata loadFromExistingSession(
      String sessionId, FileStore fileStore, CrashlyticsBackgroundWorker backgroundWorker) {
    MetaDataStore store = new MetaDataStore(fileStore);
    UserMetadata metadata = new UserMetadata(sessionId, fileStore, backgroundWorker);
    // We don't use the set methods in this class, because they will attempt to re-serialize the
    // data, which is unnecessary because we just read them from disk.
    metadata.customKeys.map.getReference().setKeys(store.readKeyData(sessionId, false));
    metadata.internalKeys.map.getReference().setKeys(store.readKeyData(sessionId, true));
    metadata.userId.set(store.readUserId(sessionId), false);
    metadata.rolloutsState.updateRolloutAssignmentList(store.readRolloutsState(sessionId));
    return metadata;
  }

  public UserMetadata(
      String sessionIdentifier, FileStore fileStore, CrashlyticsBackgroundWorker backgroundWorker) {
    this.sessionIdentifier = sessionIdentifier;
    this.metaDataStore = new MetaDataStore(fileStore);
    this.backgroundWorker = backgroundWorker;
  }

  /**
   * Refresh the userMetadata to reflect the status of the new session. This API is mainly for
   * on-demand fatal feature since we need to close and update to a new session. UserMetadata also
   * need to make this update instead of updating session id, we also need to manually writing the
   * into persistence for the new session.
   */
  public void setNewSession(String sessionId) {
    synchronized (sessionIdentifier) {
      sessionIdentifier = sessionId;
      Map<String, String> keyData = customKeys.getKeys();
      List<RolloutAssignment> rolloutAssignments = rolloutsState.getRolloutAssignmentList();
      if (getUserId() != null) {
        metaDataStore.writeUserData(sessionId, getUserId());
      }
      if (!keyData.isEmpty()) {
        metaDataStore.writeKeyData(sessionId, keyData);
      }
      if (!rolloutAssignments.isEmpty()) {
        metaDataStore.writeRolloutState(sessionId, rolloutAssignments);
      }
    }
  }

  @Nullable
  public String getUserId() {
    return userId.getReference();
  }

  /**
   * Sets the userId to the given identifier, and schedules a background Task to update the value on
   * disk, if needed.
   */
  public void setUserId(String identifier) {
    final String sanitizedNewId = KeysMap.sanitizeString(identifier, MAX_ATTRIBUTE_SIZE);
    synchronized (userId) {
      String currentId = userId.getReference();
      if (CommonUtils.nullSafeEquals(sanitizedNewId, currentId)) {
        return;
      }
      userId.set(sanitizedNewId, true);
    }
    backgroundWorker.submit(
        () -> {
          serializeUserDataIfNeeded();
          return null;
        });
  }

  /** @return defensive copy of the custom keys. */
  public Map<String, String> getCustomKeys() {
    return customKeys.getKeys();
  }

  /**
   * Sets the key/value, returning true if the key is new, or if the value associated with key has
   * changed. This method will also create and schedule a background Task to serialize the updated
   * data, if necessary.
   */
  public boolean setCustomKey(String key, String value) {
    return customKeys.setKey(key, value);
  }

  /**
   * Overwrites all key the key/value, returning true if the key is new, or if the value associated
   * with key has changed. This method will also create and schedule a background Task to serialize
   * the updated data, if necessary.
   */
  public void setCustomKeys(Map<String, String> keysAndValues) {
    customKeys.setKeys(keysAndValues);
  }

  /** @return defensive copy of the internal keys. */
  public Map<String, String> getInternalKeys() {
    return internalKeys.getKeys();
  }

  /**
   * Sets the key/value, returning true if the key is new, or if the value associated with key has
   * changed. This method will also create and schedule a background Task to serialize the updated
   * data, if necessary.
   */
  public boolean setInternalKey(String key, String value) {
    return internalKeys.setKey(key, value);
  }

  public List<CrashlyticsReport.Session.Event.RolloutAssignment> getRolloutsState() {
    return rolloutsState.getReportRolloutsState();
  }

  /**
   * Update RolloutsState in memory and persistence. Return True if update successfully, false
   * otherwise
   */
  @CanIgnoreReturnValue
  public boolean updateRolloutsState(List<RolloutAssignment> rolloutAssignments) {
    synchronized (rolloutsState) {
      if (!rolloutsState.updateRolloutAssignmentList(rolloutAssignments)) {
        return false;
      }
      List<RolloutAssignment> updatedRolloutAssignments = rolloutsState.getRolloutAssignmentList();
      backgroundWorker.submit(
          () -> {
            metaDataStore.writeRolloutState(sessionIdentifier, updatedRolloutAssignments);
            return null;
          });
      return true;
    }
  }

  /**
   * Write the user-specific metadata to disk, if it has changed since the last time this method was
   * called. This method should be called on a worker thread, since the write operation (if
   * necessary) may be costly.
   */
  private void serializeUserDataIfNeeded() {
    String userIdString = null;
    boolean needsUpdate = false;
    synchronized (userId) {
      if (userId.isMarked()) {
        userIdString = getUserId();
        needsUpdate = true;
        userId.set(userIdString, false);
      }
    }
    if (needsUpdate) {
      metaDataStore.writeUserData(sessionIdentifier, userIdString);
    }
  }

  /**
   * Helper class to maintain & asynchronously cache key data asynchronously in case of a
   * non-graceful process exit. Instances will only schedule one serialization task at a time,
   * because a queued task will always write the latest copy of the data when the task executes.
   */
  private class SerializeableKeysMap {
    final AtomicMarkableReference<KeysMap> map;
    private final AtomicReference<Callable<Void>> queuedSerializer = new AtomicReference<>(null);
    private final boolean isInternal;

    public SerializeableKeysMap(boolean isInternal) {
      this.isInternal = isInternal;
      KeysMap keysMap =
          new KeysMap(MAX_ATTRIBUTES, isInternal ? MAX_INTERNAL_KEY_SIZE : MAX_ATTRIBUTE_SIZE);
      this.map = new AtomicMarkableReference<>(keysMap, false);
    }

    public Map<String, String> getKeys() {
      return map.getReference().getKeys();
    }

    public boolean setKey(String key, String value) {
      synchronized (this) {
        // If the <key, value> pair has not changed, the marker can remain unchanged and we don't
        // need to schedule an update task.
        if (!map.getReference().setKey(key, value)) {
          return false;
        }
        map.set(map.getReference(), true);
      }
      scheduleSerializationTaskIfNeeded();
      return true;
    }

    public void setKeys(Map<String, String> keysAndValues) {
      synchronized (this) {
        map.getReference().setKeys(keysAndValues);
        // Always assume at least one value was updated when setKeys(...) is used, so set the
        // marker bit to true.
        map.set(map.getReference(), true);
      }
      scheduleSerializationTaskIfNeeded();
    }

    private void scheduleSerializationTaskIfNeeded() {
      Callable<Void> newCallable =
          () -> {
            queuedSerializer.set(null);
            serializeIfMarked();
            return null;
          };

      // Don't schedule the task if there's another queued task waiting, because the already-queued
      // task will write the latest data.
      if (queuedSerializer.compareAndSet(null, newCallable)) {
        backgroundWorker.submit(newCallable);
      }
    }

    /**
     * Helper function that should only be called from the Callable in
     * scheduleSerializationTaskIfNeeded It will write the key data iff map's marker bit is true. If
     * so, it sets the marker bit to false, so the serialized data is guaranteed to contain all
     * updates until the next time the marker is flipped to true.
     *
     * <p>If there's nothing to serialize (i.e., the marker bit is false), this method does nothing.
     */
    private void serializeIfMarked() {
      Map<String, String> keyData = null;
      // Lock while checking and updating the marker bit
      // Unfortunately there's no API that allows us to do this in one atomic call.
      synchronized (this) {
        if (map.isMarked()) {
          keyData = map.getReference().getKeys();
          map.set(map.getReference(), false);
        }
      }
      // Release the lock before doing the actual write.
      if (keyData != null) {
        metaDataStore.writeKeyData(sessionIdentifier, keyData, isInternal);
      }
    }
  }
}
