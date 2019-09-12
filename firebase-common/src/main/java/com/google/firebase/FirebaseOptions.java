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

package com.google.firebase;

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;
import static com.google.android.gms.common.util.Strings.isEmptyOrWhitespace;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.internal.StringResourceValueReader;

/** Configurable Firebase options. */
public final class FirebaseOptions {

  // TODO: deprecate and remove it once we can fetch these from Remote Config.

  private static final String API_KEY_RESOURCE_NAME = "google_api_key";
  private static final String APP_ID_RESOURCE_NAME = "google_app_id";
  private static final String DATABASE_URL_RESOURCE_NAME = "firebase_database_url";
  private static final String GA_TRACKING_ID_RESOURCE_NAME = "ga_trackingId";
  private static final String GCM_SENDER_ID_RESOURCE_NAME = "gcm_defaultSenderId";
  private static final String STORAGE_BUCKET_RESOURCE_NAME = "google_storage_bucket";
  private static final String PROJECT_ID_RESOURCE_NAME = "project_id";

  private final String apiKey;
  private final String applicationId;
  private final String databaseUrl;
  private final String gaTrackingId;
  private final String gcmSenderId;
  private final String storageBucket;
  private final String projectId;

  /** Builder for constructing FirebaseOptions. */
  public static final class Builder {
    private String apiKey;
    private String applicationId;
    private String databaseUrl;
    private String gaTrackingId;
    private String gcmSenderId;
    private String storageBucket;
    private String projectId;

    /** Constructs an empty builder. */
    public Builder() {}

    /**
     * Initializes the builder's values from the options object.
     *
     * <p>The new builder is not backed by this objects values, that is changes made to the new
     * builder don't change the values of the origin object.
     */
    public Builder(@NonNull FirebaseOptions options) {
      applicationId = options.applicationId;
      apiKey = options.apiKey;
      databaseUrl = options.databaseUrl;
      gaTrackingId = options.gaTrackingId;
      gcmSenderId = options.gcmSenderId;
      storageBucket = options.storageBucket;
      projectId = options.projectId;
    }

    @NonNull
    public Builder setApiKey(@NonNull String apiKey) {
      this.apiKey = checkNotEmpty(apiKey, "ApiKey must be set.");
      return this;
    }

    @NonNull
    public Builder setApplicationId(@NonNull String applicationId) {
      this.applicationId = checkNotEmpty(applicationId, "ApplicationId must be set.");
      return this;
    }

    @NonNull
    public Builder setDatabaseUrl(@Nullable String databaseUrl) {
      this.databaseUrl = databaseUrl;
      return this;
    }

    /** @hide */
    // TODO: unhide once an API (AppInvite) starts reading it.
    @NonNull
    @KeepForSdk
    public Builder setGaTrackingId(@Nullable String gaTrackingId) {
      this.gaTrackingId = gaTrackingId;
      return this;
    }

    @NonNull
    public Builder setGcmSenderId(@Nullable String gcmSenderId) {
      this.gcmSenderId = gcmSenderId;
      return this;
    }

    @NonNull
    public Builder setStorageBucket(@Nullable String storageBucket) {
      this.storageBucket = storageBucket;
      return this;
    }

    @NonNull
    public Builder setProjectId(@Nullable String projectId) {
      this.projectId = projectId;
      return this;
    }

    @NonNull
    public FirebaseOptions build() {
      return new FirebaseOptions(
          applicationId, apiKey, databaseUrl, gaTrackingId, gcmSenderId, storageBucket, projectId);
    }
  }

  private FirebaseOptions(
      @NonNull String applicationId,
      @NonNull String apiKey,
      @Nullable String databaseUrl,
      @Nullable String gaTrackingId,
      @Nullable String gcmSenderId,
      @Nullable String storageBucket,
      @Nullable String projectId) {
    Preconditions.checkState(!isEmptyOrWhitespace(applicationId), "ApplicationId must be set.");
    this.applicationId = applicationId;
    this.apiKey = apiKey;
    this.databaseUrl = databaseUrl;
    this.gaTrackingId = gaTrackingId;
    this.gcmSenderId = gcmSenderId;
    this.storageBucket = storageBucket;
    this.projectId = projectId;
  }

  /**
   * Creates a new {@link FirebaseOptions} instance that is populated from string resouces.
   *
   * @return The populated options or null if applicationId is missing from resources.
   */
  @Nullable
  public static FirebaseOptions fromResource(@NonNull Context context) {
    StringResourceValueReader reader = new StringResourceValueReader(context);
    String applicationId = reader.getString(APP_ID_RESOURCE_NAME);
    if (TextUtils.isEmpty(applicationId)) {
      return null;
    }
    return new FirebaseOptions(
        applicationId,
        reader.getString(API_KEY_RESOURCE_NAME),
        reader.getString(DATABASE_URL_RESOURCE_NAME),
        reader.getString(GA_TRACKING_ID_RESOURCE_NAME),
        reader.getString(GCM_SENDER_ID_RESOURCE_NAME),
        reader.getString(STORAGE_BUCKET_RESOURCE_NAME),
        reader.getString(PROJECT_ID_RESOURCE_NAME));
  }

  /**
   * API key used for authenticating requests from your app, e.g.
   * AIzaSyDdVgKwhZl0sTTTLZ7iTmt1r3N2cJLnaDk, used to identify your app to Google servers.
   */
  @NonNull
  public String getApiKey() {
    return apiKey;
  }

  /** The Google App ID that is used to uniquely identify an instance of an app. */
  @NonNull
  public String getApplicationId() {
    return applicationId;
  }

  /** The database root URL, e.g. http://abc-xyz-123.firebaseio.com. */
  @Nullable
  public String getDatabaseUrl() {
    return databaseUrl;
  }

  /**
   * The tracking ID for Google Analytics, e.g. UA-12345678-1, used to configure Google Analytics.
   *
   * @hide
   */
  // TODO: unhide once an API (AppInvite) starts reading it.
  @Nullable
  @KeepForSdk
  public String getGaTrackingId() {
    return gaTrackingId;
  }

  /**
   * The Project Number from the Google Developer's console, for example 012345678901, used to
   * configure Google Cloud Messaging.
   */
  @Nullable
  public String getGcmSenderId() {
    return gcmSenderId;
  }

  /** The Google Cloud Storage bucket name, e.g. abc-xyz-123.storage.firebase.com. */
  @Nullable
  public String getStorageBucket() {
    return storageBucket;
  }

  /** The Google Cloud project ID, e.g. my-project-1234 */
  @Nullable
  public String getProjectId() {
    return projectId;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FirebaseOptions)) {
      return false;
    }
    FirebaseOptions other = (FirebaseOptions) o;
    return Objects.equal(applicationId, other.applicationId)
        && Objects.equal(apiKey, other.apiKey)
        && Objects.equal(databaseUrl, other.databaseUrl)
        && Objects.equal(gaTrackingId, other.gaTrackingId)
        && Objects.equal(gcmSenderId, other.gcmSenderId)
        && Objects.equal(storageBucket, other.storageBucket)
        && Objects.equal(projectId, other.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        applicationId, apiKey, databaseUrl, gaTrackingId, gcmSenderId, storageBucket, projectId);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("applicationId", applicationId)
        .add("apiKey", apiKey)
        .add("databaseUrl", databaseUrl)
        .add("gcmSenderId", gcmSenderId)
        .add("storageBucket", storageBucket)
        .add("projectId", projectId)
        .toString();
  }
}
