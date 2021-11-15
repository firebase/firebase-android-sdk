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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/** Handles attributes set by the user. */
public class UserMetadata {
  static final int MAX_ATTRIBUTES = 64;
  static final int MAX_ATTRIBUTE_SIZE = 1024;
  static final int MAX_INTERNAL_KEY_SIZE = 8192;

  private String userId = null;
  private final KeysMap customKeys = new KeysMap(MAX_ATTRIBUTES, MAX_ATTRIBUTE_SIZE);
  private final KeysMap internalKeys = new KeysMap(MAX_ATTRIBUTES, MAX_INTERNAL_KEY_SIZE);

  public UserMetadata() {}

  @Nullable
  public String getUserId() {
    return userId;
  }

  public void setUserId(String identifier) {
    userId = customKeys.sanitizeAttribute(identifier);
  }

  @NonNull
  public Map<String, String> getCustomKeys() {
    return customKeys.getKeys();
  }

  /** @return true when key is new, or the value associated with key has changed */
  public boolean setCustomKey(String key, String value) {
    return customKeys.setKey(key, value);
  }

  public void setCustomKeys(Map<String, String> keysAndValues) {
    customKeys.setKeys(keysAndValues);
  }

  public Map<String, String> getInternalKeys() {
    return internalKeys.getKeys();
  }

  public boolean setInternalKey(String key, String value) {
    return internalKeys.setKey(key, value);
  }
}
