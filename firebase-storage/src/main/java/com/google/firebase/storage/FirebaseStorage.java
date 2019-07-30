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

package com.google.firebase.storage;

import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.inject.Provider;
import com.google.firebase.storage.internal.Util;
import java.io.UnsupportedEncodingException;

/**
 * FirebaseStorage is a service that supports uploading and downloading large objects to Google
 * Cloud Storage. Pass a custom instance of {@link FirebaseApp} to {@link
 * FirebaseStorage#getInstance(FirebaseApp)} which will initialize it with a storage location
 * (bucket) specified via {@link FirebaseOptions.Builder#setStorageBucket(String)}.
 *
 * <p>Otherwise, if you call {@link FirebaseStorage#getReference()} without a FirebaseApp, the
 * FirebaseStorage instance will initialize with the default {@link FirebaseApp} obtainable from
 * {@link FirebaseApp#getInstance()}. The storage location in this case will come the JSON
 * configuration file downloaded from the web.
 */
public class FirebaseStorage {
  private static final String TAG = "FirebaseStorage";
  private static final String STORAGE_URI_PARSE_EXCEPTION = "The storage Uri could not be parsed.";
  private static final String STORAGE_BUCKET_WITH_PATH_EXCEPTION =
      "The storage Uri cannot contain a path element.";
  @NonNull private final FirebaseApp mApp;
  @Nullable private final Provider<InternalAuthProvider> mAuthProvider;
  @Nullable private final String mBucketName;
  private long sMaxUploadRetry = 10 * DateUtils.MINUTE_IN_MILLIS; //  10 * 60 * 1000
  private long sMaxDownloadRetry = 10 * DateUtils.MINUTE_IN_MILLIS; //  10 * 60 * 1000
  private long sMaxQueryRetry = 2 * DateUtils.MINUTE_IN_MILLIS; //  2 * 60 * 1000

  FirebaseStorage(
      @Nullable String bucketName,
      @NonNull FirebaseApp app,
      @Nullable Provider<InternalAuthProvider> authProvider) {
    mBucketName = bucketName;
    mApp = app;
    mAuthProvider = authProvider;
  }

  private static FirebaseStorage getInstanceImpl(@NonNull FirebaseApp app, @Nullable Uri url) {
    String bucketName = url != null ? url.getHost() : null;

    if (url != null && !TextUtils.isEmpty(url.getPath())) {
      throw new IllegalArgumentException(STORAGE_BUCKET_WITH_PATH_EXCEPTION);
    }

    Preconditions.checkNotNull(app, "Provided FirebaseApp must not be null.");
    FirebaseStorageComponent component = app.get(FirebaseStorageComponent.class);
    Preconditions.checkNotNull(component, "Firebase Storage component is not present.");
    return component.get(bucketName);
  }

  /**
   * Returns the {@link FirebaseStorage}, initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseStorage} instance.
   */
  @NonNull
  public static FirebaseStorage getInstance() {
    FirebaseApp app = FirebaseApp.getInstance();
    Preconditions.checkArgument(app != null, "You must call FirebaseApp.initialize() first.");
    assert app != null;
    return getInstance(app);
  }

  /**
   * Returns the {@link FirebaseStorage}, initialized with the default {@link FirebaseApp} and a
   * custom Storage Bucket.
   *
   * @param url The gs:// url to your Firebase Storage Bucket.
   * @return a {@link FirebaseStorage} instance.
   */
  @NonNull
  public static FirebaseStorage getInstance(@NonNull String url) {
    FirebaseApp app = FirebaseApp.getInstance();
    Preconditions.checkArgument(app != null, "You must call FirebaseApp.initialize() first.");
    assert app != null;
    return getInstance(app, url);
  }

  /**
   * Returns the {@link FirebaseStorage}, initialized with a custom {@link FirebaseApp}
   *
   * @param app The custom {@link FirebaseApp} used for initialization.
   * @return a {@link FirebaseStorage} instance.
   */
  @NonNull
  public static FirebaseStorage getInstance(@NonNull FirebaseApp app) {
    // noinspection ConstantConditions
    Preconditions.checkArgument(app != null, "Null is not a valid value for the FirebaseApp.");

    String storageBucket = app.getOptions().getStorageBucket();
    if (storageBucket == null) {
      return getInstanceImpl(app, null);
    } else {
      try {
        return getInstanceImpl(
            app, Util.normalize(app, "gs://" + app.getOptions().getStorageBucket()));
      } catch (UnsupportedEncodingException e) {
        Log.e(TAG, "Unable to parse bucket:" + storageBucket, e);
        throw new IllegalArgumentException(STORAGE_URI_PARSE_EXCEPTION);
      }
    }
  }

  /**
   * Returns the {@link FirebaseStorage}, initialized with a custom {@link FirebaseApp} and a custom
   * Storage Bucket.
   *
   * @param app The custom {@link FirebaseApp} used for initialization.
   * @param url The gs:// url to your Firebase Storage Bucket.
   * @return a {@link FirebaseStorage} instance.
   */
  @NonNull
  public static FirebaseStorage getInstance(@NonNull FirebaseApp app, @NonNull String url) {
    // noinspection ConstantConditions
    Preconditions.checkArgument(app != null, "Null is not a valid value for the FirebaseApp.");
    Preconditions.checkArgument(
        url != null, "Null is not a valid value for the Firebase Storage URL.");

    if (!url.toLowerCase().startsWith("gs://")) {
      throw new IllegalArgumentException(
          "Please use a gs:// URL for your Firebase Storage bucket.");
    }

    try {
      return getInstanceImpl(app, Util.normalize(app, url));
    } catch (UnsupportedEncodingException e) {
      Log.e(TAG, "Unable to parse url:" + url, e);
      throw new IllegalArgumentException(STORAGE_URI_PARSE_EXCEPTION);
    }
  }

  /**
   * Returns the maximum time to retry a download if a failure occurs.
   *
   * @return maximum time in milliseconds. Defaults to 10 minutes (600,000 milliseconds).
   */
  public long getMaxDownloadRetryTimeMillis() {
    return sMaxDownloadRetry;
  }

  /**
   * Sets the maximum time to retry a download if a failure occurs.
   *
   * @param maxTransferRetryMillis the maximum time in milliseconds. Defaults to 10 minutes (600,000
   *     milliseconds).
   */
  public void setMaxDownloadRetryTimeMillis(long maxTransferRetryMillis) {
    sMaxDownloadRetry = maxTransferRetryMillis;
  }

  /**
   * Returns the maximum time to retry an upload if a failure occurs.
   *
   * @return the maximum time in milliseconds. Defaults to 10 minutes (600,000 milliseconds).
   */
  public long getMaxUploadRetryTimeMillis() {
    return sMaxUploadRetry;
  }

  /**
   * Sets the maximum time to retry an upload if a failure occurs.
   *
   * @param maxTransferRetryMillis the maximum time in milliseconds. Defaults to 10 minutes (600,000
   *     milliseconds).
   */
  public void setMaxUploadRetryTimeMillis(long maxTransferRetryMillis) {
    sMaxUploadRetry = maxTransferRetryMillis;
  }

  /**
   * Returns the maximum time to retry operations other than upload and download if a failure
   * occurs.
   *
   * @return the maximum time in milliseconds. Defaults to 2 minutes (120,000 milliseconds).
   */
  public long getMaxOperationRetryTimeMillis() {
    return sMaxQueryRetry;
  }

  /**
   * Sets the maximum time to retry operations other than upload and download if a failure occurs.
   *
   * @param maxTransferRetryMillis the maximum time in milliseconds. Defaults to 2 minutes (120,000
   *     milliseconds).
   */
  @SuppressWarnings("unused")
  public void setMaxOperationRetryTimeMillis(long maxTransferRetryMillis) {
    sMaxQueryRetry = maxTransferRetryMillis;
  }

  @Nullable
  private String getBucketName() {
    return mBucketName;
  }

  /**
   * Creates a new {@link StorageReference} initialized at the root Firebase Storage location.
   *
   * @return An instance of {@link StorageReference}.
   */
  @NonNull
  public StorageReference getReference() {
    String bucketName = getBucketName();
    if (TextUtils.isEmpty(bucketName)) {
      throw new IllegalStateException("FirebaseApp was not initialized with a bucket name.");
    }
    Uri uri = new Uri.Builder().scheme("gs").authority(getBucketName()).path("/").build();

    return getReference(uri);
  }

  /**
   * Creates a {@link StorageReference} given a gs:// or https:// URL pointing to a Firebase Storage
   * location.
   *
   * @param fullUrl A gs:// or http[s]:// URL used to initialize the reference. For example, you can
   *     pass in a download URL retrieved from {@link StorageReference#getDownloadUrl()} or the uri
   *     retrieved from {@link StorageReference#toString()} An error is thrown if fullUrl is not
   *     associated with the {@link FirebaseApp} used to initialize this {@link FirebaseStorage}.
   */
  @NonNull
  public StorageReference getReferenceFromUrl(@NonNull String fullUrl) {
    Preconditions.checkArgument(!TextUtils.isEmpty(fullUrl), "location must not be null or empty");
    String lowerCaseLocation = fullUrl.toLowerCase();
    if (lowerCaseLocation.startsWith("gs://")
        || lowerCaseLocation.startsWith("https://")
        || lowerCaseLocation.startsWith("http://")) {
      try {
        Uri uri = Util.normalize(mApp, fullUrl);
        if (uri == null) {
          throw new IllegalArgumentException(STORAGE_URI_PARSE_EXCEPTION);
        }
        return getReference(uri);
      } catch (UnsupportedEncodingException e) {
        Log.e(TAG, "Unable to parse location:" + fullUrl, e);

        throw new IllegalArgumentException(STORAGE_URI_PARSE_EXCEPTION);
      }
    } else {
      throw new IllegalArgumentException(STORAGE_URI_PARSE_EXCEPTION);
    }
  }

  /**
   * Creates a new {@link StorageReference} initialized with a child Firebase Storage location.
   *
   * @param location A relative path from the root to initialize the reference with, for instance
   *     "path/to/object"
   * @return An instance of {@link StorageReference} at the given child path.
   */
  @NonNull
  public StorageReference getReference(@NonNull String location) {
    Preconditions.checkArgument(!TextUtils.isEmpty(location), "location must not be null or empty");
    String lowerCaseLocation = location.toLowerCase();
    if (lowerCaseLocation.startsWith("gs://")
        || lowerCaseLocation.startsWith("https://")
        || lowerCaseLocation.startsWith("http://")) {
      throw new IllegalArgumentException("location should not be a full URL.");
    }
    return getReference().child(location);
  }

  @NonNull
  private StorageReference getReference(@NonNull Uri uri) {
    // ensure that the authority represents the correct bucket.
    Preconditions.checkNotNull(uri, "uri must not be null");
    String bucketName = getBucketName();
    Preconditions.checkArgument(
        TextUtils.isEmpty(bucketName) || uri.getAuthority().equalsIgnoreCase(bucketName),
        "The supplied bucketname does not match the storage bucket of the current instance.");

    return new StorageReference(uri, this);
  }

  /** The {@link FirebaseApp} associated with this {@link FirebaseStorage} instance. */
  @NonNull
  public FirebaseApp getApp() {
    return mApp;
  }

  @Nullable
  InternalAuthProvider getAuthProvider() {
    return mAuthProvider != null ? mAuthProvider.get() : null;
  }
}
