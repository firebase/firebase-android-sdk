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
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.util.Map;
import java.util.concurrent.atomic.AtomicMarkableReference;

/** Handles attributes set by the user. */
public class UserMetadata {

  public static final String USERDATA_FILENAME = "user-data";
  public static final String KEYDATA_FILENAME = "keys";
  public static final String INTERNAL_KEYDATA_FILENAME = "internal-keys";

  @VisibleForTesting public static final int MAX_ATTRIBUTES = 64;
  @VisibleForTesting public static final int MAX_ATTRIBUTE_SIZE = 1024;
  @VisibleForTesting public static final int MAX_INTERNAL_KEY_SIZE = 8192;

  private final MetaDataStore metaDataStore;
  private final String sessionIdentifier;

  // The following references contain a marker bit, which is true if the data maintained in the
  // associated reference has been serialized since the last time it was updated.
  // Design note: Though these are "AtomicXXX" classes, the atomic get & set operations are not used
  // extensively. The use of AtomicMarkableReference is justified as concurrent access to the
  // reference and marker bit would otherwise require volatile, and would require us to maintain
  // separate objects and booleans for synchronization locks and the marker bits.
  private final AtomicMarkableReference<KeysMap> customKeys =
      new AtomicMarkableReference<>(new KeysMap(MAX_ATTRIBUTES, MAX_ATTRIBUTE_SIZE), false);
  private final AtomicMarkableReference<KeysMap> internalKeys =
      new AtomicMarkableReference<>(new KeysMap(MAX_ATTRIBUTES, MAX_INTERNAL_KEY_SIZE), false);
  private final AtomicMarkableReference<String> userId = new AtomicMarkableReference<>(null, false);

  public static UserMetadata loadFromExistingSession(String sessionId, FileStore fileStore) {
    MetaDataStore store = new MetaDataStore(fileStore);
    UserMetadata metadata = new UserMetadata(sessionId, fileStore);
    metadata.customKeys.getReference().setKeys(store.readKeyData(sessionId, false));
    metadata.internalKeys.getReference().setKeys(store.readKeyData(sessionId, true));
    metadata.setUserId(store.readUserId(sessionId));

    return metadata;
  }

  public UserMetadata(String sessionIdentifier, FileStore fileStore) {
    this.sessionIdentifier = sessionIdentifier;
    this.metaDataStore = new MetaDataStore(fileStore);
  }

  @Nullable
  public String getUserId() {
    return userId.getReference();
  }

  public void setUserId(String identifier) {
    final String sanitizedNewId = customKeys.getReference().sanitizeAttribute(identifier);
    synchronized (userId) {
      String currentId = userId.getReference();
      if (CommonUtils.nullSafeEquals(sanitizedNewId, currentId)) {
        return;
      }
      userId.set(sanitizedNewId, true);
    }
  }

  public Map<String, String> getCustomKeys() {
    return customKeys.getReference().getKeys();
  }

  /** @return true when key is new, or the value associated with key has changed */
  public boolean setCustomKey(String key, String value) {
    return setKey(customKeys, key, value);
  }

  public void setCustomKeys(Map<String, String> keysAndValues) {
    setKeys(customKeys, keysAndValues);
  }

  /** @return defensive copy of the internal keys. */
  public Map<String, String> getInternalKeys() {
    synchronized (internalKeys) {
      return internalKeys.getReference().getKeys();
    }
  }

  public boolean setInternalKey(String key, String value) {
    return setKey(internalKeys, key, value);
  }

  private static boolean setKey(AtomicMarkableReference<KeysMap> map, String key, String value) {
    // It is safe to synchronize on map, because this method is private and we can carefully
    // manage all locks on the AtomicMarkableReferences wthin this class.
    synchronized (map) {
      // If the <key, value> pair has not changed, the marker can remain unchanged
      if (map.getReference().setKey(key, value)) {
        map.set(map.getReference(), true);
        return true;
      }
      return false;
    }
  }

  private static void setKeys(
      AtomicMarkableReference<KeysMap> map, Map<String, String> keysAndValues) {
    synchronized (map) {
      map.getReference().setKeys(keysAndValues);
      // Rather than checking the full set for equivillence, we assume the there's at least one
      // change. So we set the marker bit.
      map.set(map.getReference(), true);
    }
  }

  public void serializeKeysIfNeeded(boolean isInternal) {
    if (isInternal) {
      serializeInternalKeysIfNeeded();
    } else {
      serializeCustomKeysIfNeeded();
    }
  }

  /**
   * Write the custom keys to disk, if they have changed since the last time this method was called.
   * This method should be called on a worker thread, since the write operation (if necessary) may
   * be costly.
   */
  public void serializeCustomKeysIfNeeded() {
    Map<String, String> keyData = getKeyDataForSerializationIfDirty(customKeys);
    if (keyData != null) {
      metaDataStore.writeKeyData(sessionIdentifier, keyData, false);
    }
  }

  /**
   * Write the internal keys to disk, if they have changed since the last time this method was
   * called. This method should be called on a worker thread, since the write operation (if
   * necessary) may be costly.
   */
  public void serializeInternalKeysIfNeeded() {
    Map<String, String> keyData = getKeyDataForSerializationIfDirty(internalKeys);
    if (keyData != null) {
      metaDataStore.writeKeyData(sessionIdentifier, keyData, true);
    }
  }

  /**
   * Write the user-specific metadata to disk, if it has changed since the last time this method was
   * called. This method should be called on a worker thread, since the write operation (if
   * necessary) may be costly.
   */
  public void serializeUserDataIfNeeded() {
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
   * Helper function that should only be called from the serializeCustomKeysIfNeeded or
   * serializeInternalKeysIfNeeded methods. It returns the key data associated with map iff map's
   * marker bit is true. If so, it sets the marker bit to false, so the returned data is assumed to
   * contain all updates until the next time the marker is flipped to true.
   *
   * <p>If there's nothing to serialize (i.e., the marker bit is false), this method returns null.
   */
  @Nullable
  private static Map<String, String> getKeyDataForSerializationIfDirty(
      AtomicMarkableReference<KeysMap> map) {
    Map<String, String> keyData = null;
    synchronized (map) {
      if (map.isMarked()) {
        keyData = map.getReference().getKeys();
        map.set(map.getReference(), false);
      }
    }
    return keyData;
  }
}
