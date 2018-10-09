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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.common.base.MoreObjects;
import com.google.firebase.annotations.PublicApi;

/** Settings used to configure a FirebaseFirestore instance. */
@PublicApi
public final class FirebaseFirestoreSettings {
  public static final long CACHE_SIZE_UNLIMITED = -1;

  private static final long DEFAULT_CACHE_SIZE_BYTES = 100 * 1024 * 1024; // 100mb
  private static final String DEFAULT_HOST = "firestore.googleapis.com";
  private static final boolean DEFAULT_TIMESTAMPS_IN_SNAPSHOTS_ENABLED = false;

  /** A Builder for creating {@link FirebaseFirestoreSettings}. */
  @PublicApi
  public static final class Builder {
    private String host;
    private boolean sslEnabled;
    private boolean persistenceEnabled;
    private boolean timestampsInSnapshotsEnabled;
    private long cacheSizeBytes;

    /** Constructs a new FirebaseFirestoreSettings Builder object. */
    @PublicApi
    public Builder() {
      host = DEFAULT_HOST;
      sslEnabled = true;
      persistenceEnabled = true;
      timestampsInSnapshotsEnabled = DEFAULT_TIMESTAMPS_IN_SNAPSHOTS_ENABLED;
      cacheSizeBytes = DEFAULT_CACHE_SIZE_BYTES;
    }

    /**
     * Constructs a new FirebaseFirestoreSettings Builder based on an existing
     * FirebaseFirestoreSettings object.
     */
    @PublicApi
    public Builder(@NonNull FirebaseFirestoreSettings settings) {
      checkNotNull(settings, "Provided settings must not be null.");
      host = settings.host;
      sslEnabled = settings.sslEnabled;
      persistenceEnabled = settings.persistenceEnabled;
      timestampsInSnapshotsEnabled = settings.timestampsInSnapshotsEnabled;
    }

    /**
     * Sets the host of the Firestore backend.
     *
     * @param host The host string
     * @return A settings object with the host set.
     */
    @NonNull
    @PublicApi
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
    @PublicApi
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
    @PublicApi
    public Builder setPersistenceEnabled(boolean value) {
      this.persistenceEnabled = value;
      return this;
    }

    /**
     * Enables the use of {@link com.google.firebase.Timestamp Timestamps} for timestamp fields in
     * {@link DocumentSnapshot DocumentSnapshots}.
     *
     * <p>Currently, Firestore returns timestamp fields as {@link java.util.Date} but {@link
     * java.util.Date Date} only supports millisecond precision, which leads to truncation and
     * causes unexpected behavior when using a timestamp from a snapshot as a part of a subsequent
     * query.
     *
     * <p>Setting {@code setTimestampsInSnapshotsEnabled(true)} will cause Firestore to return
     * {@link com.google.firebase.Timestamp Timestamp} values instead of {@link java.util.Date
     * Date}, avoiding this kind of problem. To make this work you must also change any code that
     * uses {@link java.util.Date Date} to use {@link com.google.firebase.Timestamp Timestamp}
     * instead.
     *
     * <p>NOTE: in the future {@link FirebaseFirestoreSettings#areTimestampsInSnapshotsEnabled} will
     * default to true and this option will be removed so you should change your code to use
     * Timestamp now and opt-in to this new behavior as soon as you can.
     *
     * @return A settings object on which the return type for timestamp fields is configured as
     *     specified by the given {@code value}.
     */
    @NonNull
    @PublicApi
    public Builder setTimestampsInSnapshotsEnabled(boolean value) {
      this.timestampsInSnapshotsEnabled = value;
      return this;
    }

    /**
     * Sets the cache size threshold above which the SDK will attempt to collect least-recently-used
     * documents. The size is not a guarantee that the cache will stay below that size, only that if
     * the cache exceeds the given size, cleanup will be attempted.
     *
     * @return A settings object on which the cache size is configured as specified by the given
     *     {@code value}.
     */
    @NonNull
    @PublicApi
    public Builder setCacheSizeBytes(long value) {
      this.cacheSizeBytes = value;
      return this;
    }

    @NonNull
    @PublicApi
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

  /** Constructs a FirebaseFirestoreSettings object based on the values in the Builder. */
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

  /** Returns the host of the Firestore backend. */
  @NonNull
  @PublicApi
  public String getHost() {
    return host;
  }

  /** Returns whether or not to use SSL for communication. */
  @PublicApi
  public boolean isSslEnabled() {
    return sslEnabled;
  }

  /** Returns whether or not to use local persistent storage. */
  @PublicApi
  public boolean isPersistenceEnabled() {
    return persistenceEnabled;
  }

  /**
   * Returns whether or not {@link DocumentSnapshot DocumentSnapshots} return timestamp fields as
   * {@link com.google.firebase.Timestamp Timestamps}.
   */
  @PublicApi
  public boolean areTimestampsInSnapshotsEnabled() {
    return timestampsInSnapshotsEnabled;
  }

  /**
   * Returns the threshold for the cache size above which the SDK will attempt to collect the least
   * recently used documents.
   */
  @PublicApi
  public long getCacheSizeBytes() {
    return cacheSizeBytes;
  }
}
