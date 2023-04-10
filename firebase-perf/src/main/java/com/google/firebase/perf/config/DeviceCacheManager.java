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

package com.google.firebase.perf.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utilizes platform supported APIs for storing and retrieving Firebase Performance related
 * configurations.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class DeviceCacheManager {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final String PREFS_NAME = Constants.PREFS_NAME;

  private static DeviceCacheManager instance;
  // The key-value pairs are written to XML files that persist across user sessions, even if the
  // app is killed.
  // https://developer.android.com/guide/topics/data/data-storage.html#pref
  private volatile SharedPreferences sharedPref;

  // Used for retrieving the shared preferences on a separate thread.
  private final ExecutorService serialExecutor;

  @VisibleForTesting
  public DeviceCacheManager(ExecutorService serialExecutor) {
    this.serialExecutor = serialExecutor;
  }

  // TODO(b/258263016): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  public static synchronized DeviceCacheManager getInstance() {
    if (instance == null) {
      instance = new DeviceCacheManager(Executors.newSingleThreadExecutor());
    }
    return instance;
  }

  @VisibleForTesting
  public static void clearInstance() {
    instance = null;
  }

  /**
   * Triggers a getSharedPreferences call in a separate thread if shared preferences is not set.
   * This method returns immediately without waiting for the getSharedPreferences call to be
   * complete.
   */
  public synchronized void setContext(Context appContext) {
    if (sharedPref == null && appContext != null) {
      serialExecutor.execute(
          () -> {
            if (sharedPref == null && appContext != null) {
              this.sharedPref = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
          });
    }
  }

  public boolean containsKey(String key) {
    return sharedPref != null && key != null && sharedPref.contains(key);
  }

  /**
   * Retrieves value stored in caching layer for {@code key}, if caching layer is not initialized,
   * or value with the desired type doesn't exist in caching layer, return {@code Optional.empty()}.
   */
  public Optional<Boolean> getBoolean(String key) {
    if (key == null) {
      logger.debug("Key is null when getting boolean value on device cache.");
      return Optional.absent();
    }

    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return Optional.absent();
      }
    }

    if (!sharedPref.contains(key)) {
      return Optional.absent();
    }

    try {
      // Default value should never be used because key existence is checked above.
      return Optional.of(sharedPref.getBoolean(key, false));
    } catch (ClassCastException e) {
      logger.debug(
          "Key %s from sharedPreferences has type other than long: %s", key, e.getMessage());
    }
    return Optional.absent();
  }

  /** Clears the value for {@code key} in caching layer. */
  public void clear(String key) {
    if (key == null) {
      logger.debug("Key is null. Cannot clear nullable key");
      return;
    }
    sharedPref.edit().remove(key).apply();
  }

  /**
   * Sets {@code value} for {@code key} in caching layer, if caching layer is not initialized yet,
   * this value will not be saved.
   *
   * @return whether provided value has been saved to device caching layer.
   */
  public boolean setValue(String key, boolean value) {
    if (key == null) {
      logger.debug("Key is null when setting boolean value on device cache.");
      return false;
    }
    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return false;
      }
    }
    sharedPref.edit().putBoolean(key, value).apply();
    return true;
  }

  /**
   * Retrieves value stored in caching layer for {@code key}, if caching layer is not initialized,
   * or value with the desired type doesn't exist in caching layer, return {@code Optional.empty()}.
   */
  public Optional<String> getString(String key) {
    if (key == null) {
      logger.debug("Key is null when getting String value on device cache.");
      return Optional.absent();
    }

    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return Optional.absent();
      }
    }

    if (!sharedPref.contains(key)) {
      return Optional.absent();
    }

    try {
      // Default value should never be used because key existence is checked above.
      return Optional.of(sharedPref.getString(key, ""));
    } catch (ClassCastException e) {
      logger.debug(
          "Key %s from sharedPreferences has type other than String: %s", key, e.getMessage());
    }
    return Optional.absent();
  }

  /**
   * Sets {@code value} for {@code key} in caching layer, if caching layer is not initialized yet,
   * this value will not be saved. If {@code value} is null, this key will be removed from caching
   * layer.
   *
   * @return whether provided value has been saved to device caching layer.
   */
  public boolean setValue(String key, String value) {
    if (key == null) {
      logger.debug("Key is null when setting String value on device cache.");
      return false;
    }

    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return false;
      }
    }

    if (value == null) {
      sharedPref.edit().remove(key).apply();
      return true;
    }

    sharedPref.edit().putString(key, value).apply();
    return true;
  }

  /**
   * Retrieves value stored in caching layer for {@code key}, if caching layer is not initialized,
   * or value with the desired type doesn't exist in caching layer, return {@code Optional.empty()}.
   */
  public Optional<Double> getDouble(String key) {
    if (key == null) {
      logger.debug("Key is null when getting double value on device cache.");
      return Optional.absent();
    }

    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return Optional.absent();
      }
    }

    if (!sharedPref.contains(key)) {
      return Optional.absent();
    }

    try {
      // SharedPreferences does not allow storing a Double directly. We store the double's bits as a
      // long so here we convert that back to a double.
      // Default value should never be used because key existence is checked above.
      return Optional.of(Double.longBitsToDouble(sharedPref.getLong(key, 0)));
    } catch (ClassCastException unused) {
      // In the past, we used to store a Float here instead of a Double. Before the value is
      // overwritten for the first time, it will still have a Float and so this may be expected.
      try {
        return Optional.of(Float.valueOf(sharedPref.getFloat(key, 0.0f)).doubleValue());
      } catch (ClassCastException e) {
        logger.debug(
            "Key %s from sharedPreferences has type other than double: %s", key, e.getMessage());
      }
    }
    return Optional.absent();
  }

  /**
   * Sets {@code value} for {@code key} in caching layer, if caching layer is not initialized yet,
   * this value will not be saved.
   *
   * @return whether provided value has been saved to device caching layer.
   */
  public boolean setValue(String key, double value) {
    if (key == null) {
      logger.debug("Key is null when setting double value on device cache.");
      return false;
    }

    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return false;
      }
    }
    // SharedPreferences does not allow storing a Double directly. The main way to store it without
    // losing precision is to store the double's bits as a long so it can then be converted back to
    // a double.
    sharedPref.edit().putLong(key, Double.doubleToRawLongBits(value)).apply();
    return true;
  }

  /**
   * Retrieves value stored in caching layer for {@code key}, if caching layer is not initialized,
   * or value with the desired type doesn't exist in caching layer, return {@code Optional.empty()}.
   */
  public Optional<Long> getLong(String key) {
    if (key == null) {
      logger.debug("Key is null when getting long value on device cache.");
      return Optional.absent();
    }

    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return Optional.absent();
      }
    }

    if (!sharedPref.contains(key)) {
      return Optional.absent();
    }

    try {
      // Default value should never be used because key existence is checked above.
      return Optional.of(sharedPref.getLong(key, 0L));
    } catch (ClassCastException e) {
      logger.debug(
          "Key %s from sharedPreferences has type other than long: %s", key, e.getMessage());
    }
    return Optional.absent();
  }

  /**
   * Sets {@code value} for {@code key} in caching layer, if caching layer is not initialized yet,
   * this value will not be saved.
   *
   * @return whether provided value has been saved to device caching layer.
   */
  public boolean setValue(String key, long value) {
    if (key == null) {
      logger.debug("Key is null when setting long value on device cache.");
      return false;
    }
    if (sharedPref == null) {
      setContext(getFirebaseApplicationContext());
      if (sharedPref == null) {
        return false;
      }
    }
    sharedPref.edit().putLong(key, value).apply();
    return true;
  }

  @Nullable
  private Context getFirebaseApplicationContext() {
    try {
      FirebaseApp.getInstance();
    } catch (IllegalStateException e) {
      return null;
    }

    return FirebaseApp.getInstance().getApplicationContext();
  }
}
