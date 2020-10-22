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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Settings used to configure a {@link FirebaseFirestore} instance. */
public final class FirebaseFirestoreSettings {
  /**
   * Constant to use with {@link FirebaseFirestoreSettings.Builder#setCacheSizeBytes(long)} to
   * disable garbage collection.
   */
  public static final long CACHE_SIZE_UNLIMITED = -1;

  /** @hide */
  public static final String DEFAULT_HOST = "firestore.googleapis.com";

  private static final long MINIMUM_CACHE_BYTES = 1 * 1024 * 1024; // 1 MB
  private static final long DEFAULT_CACHE_SIZE_BYTES = 100 * 1024 * 1024; // 100 MB

  /** A Builder for creating {@code FirebaseFirestoreSettings}. */
  public static final class Builder {
    private String host;
    private boolean sslEnabled;
    private boolean persistenceEnabled;
    private long cacheSizeBytes;

    /** Constructs a new {@code FirebaseFirestoreSettings} Builder object. */
    public Builder() {
      host = DEFAULT_HOST;
      sslEnabled = true;
      persistenceEnabled = true;
      cacheSizeBytes = DEFAULT_CACHE_SIZE_BYTES;
    }

    /**
     * Constructs a new {@code FirebaseFirestoreSettings} Builder based on an existing {@code
     * FirebaseFirestoreSettings} object.
     */
    public Builder(@NonNull FirebaseFirestoreSettings settings) {
      checkNotNull(settings, "Provided settings must not be null.");
      host = settings.host;
      sslEnabled = settings.sslEnabled;
      persistenceEnabled = settings.persistenceEnabled;
    }

    /**
     * Sets the host of the Cloud Firestore backend.
     *
     * @param host The host string
     * @return A settings object with the host set.
     */
    @NonNull
    public Builder setHost(@NonNull String host) {
      this.host = checkNotNull(host, "Provided host must not be null.");
      return this;
    }

    /**
     * Enables or disables SSL for communication. The default is to use SSL.
     *
     * @return A settings object that uses SSL as specified by the <tt>value</tt>.
     */
    @NonNull
    public Builder setSslEnabled(boolean value) {
      this.sslEnabled = value;
      return this;
    }

    /**
     * Enables or disables local persistent storage. The default is to use local persistent storage.
     *
     * @return A settings object that uses local persistent storage as specified by the given
     *     <tt>value</tt>.
     */
    @NonNull
    public Builder setPersistenceEnabled(boolean value) {
      this.persistenceEnabled = value;
      return this;
    }

    /**
     * Sets an approximate cache size threshold for the on-disk data. If the cache grows beyond this
     * size, Cloud Firestore will start removing data that hasn't been recently used. The size is
     * not a guarantee that the cache will stay below that size, only that if the cache exceeds the
     * given size, cleanup will be attempted.
     *
     * <p>By default, collection is enabled with a cache size of 100 MB. The minimum value is 1 MB.
     *
     * @return A settings object on which the cache size is configured as specified by the given
     *     {@code value}.
     */
    @NonNull
    public Builder setCacheSizeBytes(long value) {
      if (value != CACHE_SIZE_UNLIMITED && value < MINIMUM_CACHE_BYTES) {
        throw new IllegalArgumentException(
            "Cache size must be set to at least " + MINIMUM_CACHE_BYTES + " bytes");
      }
      this.cacheSizeBytes = value;
      return this;
    }

    /** @return the host of the Cloud Firestore backend. */
    @NonNull
    public String getHost() {
      return host;
    }

    /** @return boolean indicating whether SSL is enabled or not. */
    public boolean isSslEnabled() {
      return sslEnabled;
    }

    /** @return boolean indicating whether local persistent storage is enabled or not. */
    public boolean isPersistenceEnabled() {
      return persistenceEnabled;
    }

    /** @return cache size for on-disk data. */
    public long getCacheSizeBytes() {
      return cacheSizeBytes;
    }

    @NonNull
    public FirebaseFirestoreSettings build() {
      if (!this.sslEnabled && this.host.equals(DEFAULT_HOST)) {
        throw new IllegalStateException(
            "You can't set the 'sslEnabled' setting unless you also set a non-default 'host'.");
      }
      return new FirebaseFirestoreSettings(this);
    }
  }

  private final String host;
  private final boolean sslEnabled;
  private final boolean persistenceEnabled;
  private final long cacheSizeBytes;

  /** Constructs a {@code FirebaseFirestoreSettings} object based on the values in the Builder. */
  private FirebaseFirestoreSettings(Builder builder) {
    host = builder.host;
    sslEnabled = builder.sslEnabled;
    persistenceEnabled = builder.persistenceEnabled;
    cacheSizeBytes = builder.cacheSizeBytes;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FirebaseFirestoreSettings that = (FirebaseFirestoreSettings) o;
    return host.equals(that.host)
        && sslEnabled == that.sslEnabled
        && persistenceEnabled == that.persistenceEnabled
        && cacheSizeBytes == that.cacheSizeBytes;
  }

  @Override
  public int hashCode() {
    int result = host.hashCode();
    result = 31 * result + (sslEnabled ? 1 : 0);
    result = 31 * result + (persistenceEnabled ? 1 : 0);
    result = 31 * result + (int) cacheSizeBytes;
    return result;
  }

  @Override
  @NonNull
  public String toString() {
    return "FirebaseFirestoreSettings{"
        + "host="
        + host
        + ", sslEnabled="
        + sslEnabled
        + ", persistenceEnabled="
        + persistenceEnabled
        + ", cacheSizeBytes="
        + cacheSizeBytes
        + "}";
  }

  /** Returns the host of the Cloud Firestore backend. */
  @NonNull
  public String getHost() {
    return host;
  }

  /** Returns whether or not to use SSL for communication. */
  public boolean isSslEnabled() {
    return sslEnabled;
  }

  /** Returns whether or not to use local persistent storage. */
  public boolean isPersistenceEnabled() {
    return persistenceEnabled;
  }

  /**
   * Returns the threshold for the cache size above which the SDK will attempt to collect the least
   * recently used documents.
   */
  public long getCacheSizeBytes() {
    return cacheSizeBytes;
  }
}
