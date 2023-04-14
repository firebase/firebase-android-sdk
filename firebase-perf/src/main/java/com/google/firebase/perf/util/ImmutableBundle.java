// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

import android.os.Bundle;
import com.google.firebase.perf.logging.AndroidLogger;

/**
 * An immutable, thread safe wrapper around @{link android.os.Bundle} that only exposes get methods.
 * This assumes that the keys and values themselves are immutable and so it only performs a shallow
 * copy of the bundle.
 */
public final class ImmutableBundle {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private final Bundle bundle;

  /** Constructs a new empty ImmutableBundle. */
  public ImmutableBundle() {
    this(new Bundle());
  }

  /** Constructs a new ImmutableBundle with the given Bundle. */
  public ImmutableBundle(Bundle bundle) {
    this.bundle = (Bundle) bundle.clone();
  }

  /**
   * Returns true if the given key is contained in the mapping of this Bundle.
   *
   * @param key a String key
   * @return true if the key is part of the mapping, false otherwise or key is null
   */
  public boolean containsKey(String key) {
    return key != null && bundle.containsKey(key);
  }

  /**
   * Returns bool value associated with the given key, or not present if no mapping of the desired
   * type exists for the given key.
   */
  public Optional<Boolean> getBoolean(String key) {
    if (!containsKey(key)) {
      return Optional.absent();
    }

    try {
      Object o = bundle.get(key);
      return Optional.fromNullable((Boolean) o);
    } catch (ClassCastException e) {
      logger.debug("Metadata key %s contains type other than boolean: %s", key, e.getMessage());
    }

    return Optional.absent();
  }

  /**
   * Returns double value associated with the given key, or not present if no mapping of the desired
   * type exists for the given key.
   */
  public Optional<Double> getDouble(String key) {
    if (!containsKey(key)) {
      return Optional.absent();
    }

    Object o = bundle.get(key);
    if (o == null) {
      return Optional.absent();
    }
    if (o instanceof Float) {
      return Optional.of(((Float) o).doubleValue());
    }
    if (o instanceof Double) {
      return Optional.of((Double) o);
    }

    logger.debug("Metadata key %s contains type other than double: %s", key);
    return Optional.absent();
  }

  /**
   * Returns long value associated with the given key, or not present if no mapping of the desired
   * type exists for the given key.
   */
  public Optional<Long> getLong(String key) {
    Optional<Integer> intValue = getInt(key);
    if (intValue.isAvailable()) {
      return Optional.of((long) intValue.get());
    }
    return Optional.absent();
  }

  /**
   * Returns int value associated with the given key, or not present if no mapping of the desired
   * type exists for the given key.
   */
  private Optional<Integer> getInt(String key) {
    if (!containsKey(key)) {
      return Optional.absent();
    }

    try {
      Object o = bundle.get(key);
      return Optional.fromNullable((Integer) o);
    } catch (ClassCastException e) {
      logger.debug("Metadata key %s contains type other than int: %s", key, e.getMessage());
    }

    return Optional.absent();
  }
}
