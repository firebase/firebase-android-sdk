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

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.MoreObjects;

/** Settings used to configure a {@link FirebaseFirestore} instance. */
public final class FirebaseFirestoreSettings {
  /**
   * Constant to use with {@link FirebaseFirestoreSettings.Builder#setCacheSizeBytes(long)} to
   * disable garbage collection.
   */
  public static final long CACHE_SIZE_UNLIMITED = -1;

  private static final long MINIMUM_CACHE_BYTES = 1 * 1024 * 1024; // 1 MB
  private static final long DEFAULT_CACHE_SIZE_BYTES = 100 * 1024 * 1024; // 100 MB
  private static final String DEFAULT_HOST = "firestore.googleapis.com";
  private static final boolean DEFAULT_TIMESTAMPS_IN_SNAPSHOTS_ENABLED = true;

  /** A Builder for creating {@code FirebaseFirestoreSettings}. */
  public static final class Builder {
    private String host;
    private boolean sslEnabled;
    private boolean persistenceEnabled;
    private boolean timestampsInSnapshotsEnabled;
    private long cacheSizeBytes;

    /** Constructs a new {@code FirebaseFirestoreSettings} Builder object. */
    public Builder() {
      host = DEFAULT_HOST;
      sslEnabled = true;
      persistenceEnabled = true;
      timestampsInSnapshotsEnabled = DEFAULT_TIMESTAMPS_IN_SNAPSHOTS_ENABLED;
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
      timestampsInSnapshotsEnabled = settings.timestampsInSnapshotsEnabled;
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
     * Specifies whether to use {@link com.google.firebase.Timestamp Timestamps} for timestamp
     * fields in {@link DocumentSnapshot DocumentSnapshots}. This is now enabled by default and
     * should not be disabled.
     *
     * <p>Previously, Cloud Firestore returned timestamp fields as {@link java.util.Date} but {@link
     * java.util.Date} only supports millisecond precision, which leads to truncation and causes
     * unexpected behavior when using a timestamp from a snapshot as a part of a subsequent query.
     *
     * <p>So now Cloud Firestore returns {@link com.google.firebase.Timestamp Timestamp} values
     * instead of {@link java.util.Date}, avoiding this kind of problem.
     *
     * <p>To opt into the old behavior of returning {@link java.util.Date Dates}, you can
     * temporarily set {@link FirebaseFirestoreSettings#areTimestampsInSnapshotsEnabled} to false.
     *
     * @deprecated This setting now defaults to true and will be removed in a future release. If you
     *     are already setting it to true, just remove the setting. If you are setting it to false,
     *     you should update your code to expect {@link com.google.firebase.Timestamp Timestamps}
     *     instead of {@link java.util.Date Dates} and then remove the setting.
     */
    @NonNull
    @Deprecated
    public Builder setTimestampsInSnapshotsEnabled(boolean value) {
      this.timestampsInSnapshotsEnabled = value;
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
  private final boolean timestampsInSnapshotsEnabled;
  private final long cacheSizeBytes;

  /** Constructs a {@code FirebaseFirestoreSettings} object based on the values in the Builder. */
  private FirebaseFirestoreSettings(Builder builder) {
    host = builder.host;
    sslEnabled = builder.sslEnabled;
    persistenceEnabled = builder.persistenceEnabled;
    timestampsInSnapshotsEnabled = builder.timestampsInSnapshotsEnabled;
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
        && timestampsInSnapshotsEnabled == that.timestampsInSnapshotsEnabled
        && cacheSizeBytes == that.cacheSizeBytes;
  }

  @Override
  public int hashCode() {
    int result = host.hashCode();
    result = 31 * result + (sslEnabled ? 1 : 0);
    result = 31 * result + (persistenceEnabled ? 1 : 0);
    result = 31 * result + (timestampsInSnapshotsEnabled ? 1 : 0);
    result = 31 * result + (int) cacheSizeBytes;
    return result;
  }

  @Override
  @NonNull
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("host", host)
        .add("sslEnabled", sslEnabled)
        .add("persistenceEnabled", persistenceEnabled)
        .add("timestampsInSnapshotsEnabled", timestampsInSnapshotsEnabled)
        .toString();
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
   * Returns whether or not {@link DocumentSnapshot DocumentSnapshots} return timestamp fields as
   * {@link com.google.firebase.Timestamp Timestamps}.
   */
  public boolean areTimestampsInSnapshotsEnabled() {
    return timestampsInSnapshotsEnabled;
  }

  /**
   * Returns the threshold for the cache size above which the SDK will attempt to collect the least
   * recently used documents.
   */
  public long getCacheSizeBytes() {
    return cacheSizeBytes;
  }
}
