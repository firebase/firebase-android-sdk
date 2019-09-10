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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.storage.internal.Slashes;
import com.google.firebase.storage.internal.Util;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Metadata for a {@link StorageReference}. Metadata stores default attributes such as size and
 * content type. You may also store custom metadata key value pairs. Metadata values may be used to
 * authorize operations using declarative validation rules.
 */
public class StorageMetadata {
  private static final String TAG = "StorageMetadata";

  @NonNull private static final String CONTENT_LANGUAGE = "contentLanguage";
  @NonNull private static final String CONTENT_ENCODING = "contentEncoding";
  @NonNull private static final String CONTENT_DISPOSITION = "contentDisposition";
  @NonNull private static final String CACHE_CONTROL = "cacheControl";
  @NonNull private static final String CUSTOM_METADATA_KEY = "metadata";
  @NonNull private static final String CONTENT_TYPE_KEY = "contentType";
  @NonNull private static final String MD5_HASH_KEY = "md5Hash";
  @NonNull private static final String SIZE_KEY = "size";
  @NonNull private static final String TIME_UPDATED_KEY = "updated";
  @NonNull private static final String TIME_CREATED_KEY = "timeCreated";
  @NonNull private static final String META_GENERATION_KEY = "metageneration";
  @NonNull private static final String BUCKET_KEY = "bucket";
  @NonNull private static final String NAME_KEY = "name";
  @NonNull private static final String GENERATION_KEY = "generation";

  private String mPath = null;
  private FirebaseStorage mStorage = null;
  private StorageReference mStorageRef = null;

  /** Stores metadata values and indicates whether these are defaults or user-provided. */
  private static class MetadataValue<T> {
    private final boolean userProvided;
    @Nullable private final T value;

    MetadataValue(@Nullable T value, boolean userProvided) {
      this.userProvided = userProvided;
      this.value = value;
    }

    /**
     * Creates an optional that doesn't have a user provided value and returns the default value.
     * isUserProvided() will return false.
     */
    static <T> MetadataValue<T> withDefaultValue(T value) {
      return new MetadataValue<T>(value, false);
    }

    /**
     * Creates an optional that returns the user-provided value. isUserProvided() will return true.
     */
    static <T> MetadataValue<T> withUserValue(@Nullable T value) {
      return new MetadataValue<T>(value, true);
    }

    boolean isUserProvided() {
      return userProvided;
    }

    @Nullable
    T getValue() {
      return value;
    }
  }

  private String mBucket = null;
  private String mGeneration = null;
  private MetadataValue<String> mContentType = MetadataValue.withDefaultValue("");
  private String mMetadataGeneration = null;
  private String mCreationTime = null;
  private String mUpdatedTime = null;
  private long mSize;
  private String mMD5Hash = null;
  private MetadataValue<String> mCacheControl = MetadataValue.withDefaultValue("");
  private MetadataValue<String> mContentDisposition = MetadataValue.withDefaultValue("");
  // TODO: Change the default value to "identity" with the next major API version.
  private MetadataValue<String> mContentEncoding = MetadataValue.withDefaultValue("");
  private MetadataValue<String> mContentLanguage = MetadataValue.withDefaultValue("");
  private MetadataValue<Map<String, String>> mCustomMetadata =
      MetadataValue.withDefaultValue(Collections.emptyMap());

  /** Creates a {@link StorageMetadata} object to hold metadata for a {@link StorageReference} */
  public StorageMetadata() {}

  private StorageMetadata(@NonNull StorageMetadata original, boolean fullClone) {
    Preconditions.checkNotNull(original);

    mPath = original.mPath;
    mStorage = original.mStorage;
    mStorageRef = original.mStorageRef;
    mBucket = original.mBucket;
    mContentType = original.mContentType;
    mCacheControl = original.mCacheControl;
    mContentDisposition = original.mContentDisposition;
    mContentEncoding = original.mContentEncoding;
    mContentLanguage = original.mContentLanguage;
    mCustomMetadata = original.mCustomMetadata;
    if (fullClone) {
      mMD5Hash = original.mMD5Hash;
      mSize = original.mSize;
      mUpdatedTime = original.mUpdatedTime;
      mCreationTime = original.mCreationTime;
      mMetadataGeneration = original.mMetadataGeneration;
      mGeneration = original.mGeneration;
    }
  }

  /** @return the content type of the {@link StorageReference}. */
  @Nullable
  public String getContentType() {
    return mContentType.getValue();
  }

  /**
   * Returns custom metadata for a {@link StorageReference}
   *
   * @param key The key for which the metadata should be returned
   * @return the metadata stored in the object the given key.
   */
  @Nullable
  public String getCustomMetadata(@NonNull String key) {
    if (TextUtils.isEmpty(key)) {
      return null;
    }
    Map<String, String> metadata = mCustomMetadata.getValue();
    return metadata.get(key);
  }

  /** @return the keys for custom metadata. */
  @NonNull
  public Set<String> getCustomMetadataKeys() {
    Map<String, String> metadata = mCustomMetadata.getValue();
    return metadata.keySet();
  }

  /** @return the path of the {@link StorageReference} object */
  @NonNull
  public String getPath() {
    return mPath != null ? mPath : "";
  }

  /** @return a simple name of the {@link StorageReference} object */
  @Nullable
  public String getName() {
    String path = getPath();
    if (TextUtils.isEmpty(path)) {
      return null;
    }
    int lastIndex = path.lastIndexOf('/');
    if (lastIndex != -1) {
      return path.substring(lastIndex + 1);
    }
    return path;
  }

  /** @return the owning Google Cloud Storage bucket for the {@link StorageReference} */
  @Nullable
  public String getBucket() {
    return mBucket;
  }

  /** @return a version String indicating what version of the {@link StorageReference} */
  @Nullable
  public String getGeneration() {
    return mGeneration;
  }

  /** @return a version String indicating the version of this {@link StorageMetadata} */
  @Nullable
  public String getMetadataGeneration() {
    return mMetadataGeneration;
  }

  /** @return the time the {@link StorageReference} was created. */
  public long getCreationTimeMillis() {
    return Util.parseDateTime(mCreationTime);
  }

  /** @return the time the {@link StorageReference} was last updated. */
  public long getUpdatedTimeMillis() {
    return Util.parseDateTime(mUpdatedTime);
  }

  /** @return the stored Size in bytes of the {@link StorageReference} object */
  public long getSizeBytes() {
    return mSize;
  }

  /** @return the MD5Hash of the {@link StorageReference} object */
  @Nullable
  public String getMd5Hash() {
    return mMD5Hash;
  }

  /** @return the Cache Control setting of the {@link StorageReference} */
  @Nullable
  public String getCacheControl() {
    return mCacheControl.getValue();
  }

  /** @return the content disposition of the {@link StorageReference} */
  @Nullable
  public String getContentDisposition() {
    return mContentDisposition.getValue();
  }

  /** @return the content encoding for the {@link StorageReference} */
  @Nullable
  public String getContentEncoding() {
    return mContentEncoding.getValue();
  }

  /** @return the content language for the {@link StorageReference} */
  @Nullable
  public String getContentLanguage() {
    return mContentLanguage.getValue();
  }

  /** @return the associated {@link StorageReference} for which this metadata belongs to. */
  @Nullable
  public StorageReference getReference() {
    if (mStorageRef == null) {
      if (mStorage != null) {
        String bucket = getBucket();
        String path = getPath();
        if (TextUtils.isEmpty(bucket) || TextUtils.isEmpty(path)) {
          return null;
        }
        Uri uri =
            new Uri.Builder()
                .scheme("gs")
                .authority(bucket)
                .encodedPath(Slashes.preserveSlashEncode(path))
                .build();

        return new StorageReference(uri, mStorage);
      }
    }
    return mStorageRef;
  }

  @NonNull
  JSONObject createJSONObject() {
    Map<String, Object> jsonData = new HashMap<>();

    if (mContentType.isUserProvided()) {
      jsonData.put(CONTENT_TYPE_KEY, getContentType());
    }
    if (mCustomMetadata.isUserProvided()) {
      jsonData.put(CUSTOM_METADATA_KEY, new JSONObject(mCustomMetadata.getValue()));
    }
    if (mCacheControl.isUserProvided()) {
      jsonData.put(CACHE_CONTROL, getCacheControl());
    }
    if (mContentDisposition.isUserProvided()) {
      jsonData.put(CONTENT_DISPOSITION, getContentDisposition());
    }
    if (mContentEncoding.isUserProvided()) {
      jsonData.put(CONTENT_ENCODING, getContentEncoding());
    }
    if (mContentLanguage.isUserProvided()) {
      jsonData.put(CONTENT_LANGUAGE, getContentLanguage());
    }

    return new JSONObject(jsonData);
  }

  /** Creates a StorageMetadata object. */
  public static class Builder {
    StorageMetadata mMetadata;
    boolean mFromJSON;

    /** Creates an empty set of metadata. */
    public Builder() {
      mMetadata = new StorageMetadata();
    }

    /**
     * Used to create a modified version of the original set of metadata.
     *
     * @param original The source of the metadata to build from.
     */
    public Builder(@NonNull StorageMetadata original) {
      mMetadata = new StorageMetadata(original, false);
    }

    /*package*/ Builder(JSONObject resultBody, StorageReference thisStorageRef)
        throws JSONException {
      this(resultBody);
      mMetadata.mStorageRef = thisStorageRef;
    }

    /*package*/ Builder(JSONObject resultBody) throws JSONException {
      mMetadata = new StorageMetadata();

      if (resultBody != null) {
        parseJSON(resultBody);
        mFromJSON = true;
      }
    }

    @Nullable
    private String extractString(JSONObject jsonObject, String key) throws JSONException {
      if (jsonObject.has(key) && !jsonObject.isNull(key)) {
        return jsonObject.getString(key);
      }
      return null;
    }

    private void parseJSON(JSONObject jsonObject) throws JSONException {
      mMetadata.mGeneration = jsonObject.optString(GENERATION_KEY);
      mMetadata.mPath = jsonObject.optString(NAME_KEY);
      mMetadata.mBucket = jsonObject.optString(BUCKET_KEY);
      mMetadata.mMetadataGeneration = jsonObject.optString(META_GENERATION_KEY);
      mMetadata.mCreationTime = jsonObject.optString(TIME_CREATED_KEY);
      mMetadata.mUpdatedTime = jsonObject.optString(TIME_UPDATED_KEY);
      mMetadata.mSize = jsonObject.optLong(SIZE_KEY);
      mMetadata.mMD5Hash = jsonObject.optString(MD5_HASH_KEY);

      if (jsonObject.has(CUSTOM_METADATA_KEY) && !jsonObject.isNull(CUSTOM_METADATA_KEY)) {
        JSONObject customMetadata = jsonObject.getJSONObject(CUSTOM_METADATA_KEY);
        for (Iterator<String> keyIterator = customMetadata.keys(); keyIterator.hasNext(); ) {
          String key = keyIterator.next();
          setCustomMetadata(key, customMetadata.getString(key));
        }
      }

      String value;
      if ((value = extractString(jsonObject, CONTENT_TYPE_KEY)) != null) {
        this.setContentType(value);
      }
      if ((value = extractString(jsonObject, CACHE_CONTROL)) != null) {
        this.setCacheControl(value);
      }
      if ((value = extractString(jsonObject, CONTENT_DISPOSITION)) != null) {
        this.setContentDisposition(value);
      }
      if ((value = extractString(jsonObject, CONTENT_ENCODING)) != null) {
        this.setContentEncoding(value);
      }
      if ((value = extractString(jsonObject, CONTENT_LANGUAGE)) != null) {
        this.setContentLanguage(value);
      }
    }

    @NonNull
    public StorageMetadata build() {
      return new StorageMetadata(mMetadata, mFromJSON);
    }

    /**
     * Changes the content language for the {@link StorageReference}
     *
     * @param contentLanguage the new content language.
     */
    @NonNull
    public Builder setContentLanguage(@Nullable String contentLanguage) {
      mMetadata.mContentLanguage = MetadataValue.withUserValue(contentLanguage);
      return this;
    }

    /** @return the content language for the {@link StorageReference} */
    @Nullable
    public String getContentLanguage() {
      return mMetadata.mContentLanguage.getValue();
    }

    /**
     * Changes the content encoding for the {@link StorageReference}
     *
     * @param contentEncoding the new encoding to use.
     */
    @NonNull
    public Builder setContentEncoding(@Nullable String contentEncoding) {
      mMetadata.mContentEncoding = MetadataValue.withUserValue(contentEncoding);
      return this;
    }

    /** @return the content encoding for the {@link StorageReference} */
    @Nullable
    public String getContentEncoding() {
      return mMetadata.mContentEncoding.getValue();
    }

    /**
     * Changes the content disposition for the {@link StorageReference}
     *
     * @param contentDisposition the new content disposition to use.
     */
    @NonNull
    public Builder setContentDisposition(@Nullable String contentDisposition) {
      mMetadata.mContentDisposition = MetadataValue.withUserValue(contentDisposition);
      return this;
    }

    /** @return the content disposition for the {@link StorageReference} */
    @Nullable
    public String getContentDisposition() {
      return mMetadata.mContentDisposition.getValue();
    }

    /**
     * Sets the Cache Control header for the {@link StorageReference}
     *
     * @param cacheControl the new Cache Control setting.
     */
    @NonNull
    public Builder setCacheControl(@Nullable String cacheControl) {
      mMetadata.mCacheControl = MetadataValue.withUserValue(cacheControl);
      return this;
    }

    /** @return the Cache Control header for the {@link StorageReference} */
    @Nullable
    public String getCacheControl() {
      return mMetadata.mCacheControl.getValue();
    }

    /**
     * Sets custom metadata
     *
     * @param key the key of the new value
     * @param value the value to set.
     */
    @NonNull
    public Builder setCustomMetadata(@NonNull String key, @Nullable String value) {
      if (!mMetadata.mCustomMetadata.isUserProvided()) {
        mMetadata.mCustomMetadata = MetadataValue.withUserValue(new HashMap<>());
      }
      mMetadata.mCustomMetadata.getValue().put(key, value);
      return this;
    }

    /**
     * Changes the content Type of this associated {@link StorageReference}
     *
     * @param contentType the new Content Type.
     */
    @NonNull
    public Builder setContentType(@Nullable String contentType) {
      mMetadata.mContentType = MetadataValue.withUserValue(contentType);
      return this;
    }

    /** @return the Content Type of this associated {@link StorageReference} */
    @Nullable
    public String getContentType() {
      return mMetadata.mContentType.getValue();
    }
  }
}
