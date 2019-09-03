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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.internal.Slashes;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Represents a reference to a Google Cloud Storage object. Developers can upload and download
 * objects, get/set object metadata, and delete an object at a specified path. (see <a
 * href="https://cloud.google.com/storage/">Google Cloud Storage</a>)
 */
@SuppressWarnings("unused")
public class StorageReference implements Comparable<StorageReference> {
  private static final String TAG = "StorageReference";

  private final Uri mStorageUri;
  private final FirebaseStorage mFirebaseStorage;

  // region Constructors
  /*package*/
  @SuppressWarnings("ConstantConditions")
  StorageReference(@NonNull Uri storageUri, @NonNull FirebaseStorage firebaseStorage) {
    Preconditions.checkArgument(storageUri != null, "storageUri cannot be null");
    Preconditions.checkArgument(firebaseStorage != null, "FirebaseApp cannot be null");

    this.mStorageUri = storageUri;
    this.mFirebaseStorage = firebaseStorage;
  }
  // endregion

  // region Path Operations

  /**
   * Returns a new instance of {@link StorageReference} pointing to a child location of the current
   * reference. All leading and trailing slashes will be removed, and consecutive slashes will be
   * compressed to single slashes. For example:
   *
   * <pre>{@code
   * child = /foo/bar     path = foo/bar
   * child = foo/bar/     path = foo/bar
   * child = foo///bar    path = foo/bar
   * }</pre>
   *
   * @param pathString The relative path from this reference.
   * @return the child {@link StorageReference}.
   */
  @NonNull
  public StorageReference child(@NonNull String pathString) {
    Preconditions.checkArgument(
        !TextUtils.isEmpty(pathString), "childName cannot be null or empty");

    pathString = Slashes.normalizeSlashes(pathString);
    Uri child =
        mStorageUri.buildUpon().appendEncodedPath(Slashes.preserveSlashEncode(pathString)).build();
    return new StorageReference(child, mFirebaseStorage);
  }

  /**
   * Returns a new instance of {@link StorageReference} pointing to the parent location or null if
   * this instance references the root location. For example:
   *
   * <pre>{@code
   * path = foo/bar/baz   parent = foo/bar
   * path = foo           parent = (root)
   * path = (root)        parent = (null)
   * }</pre>
   *
   * @return the parent {@link StorageReference}.
   */
  @Nullable
  public StorageReference getParent() {
    String path = mStorageUri.getPath();
    if (TextUtils.isEmpty(path) || path.equals("/")) {
      return null;
    }
    int childIndex = path.lastIndexOf('/');
    if (childIndex == -1) {
      path = "/";
    } else {
      path = path.substring(0, childIndex);
    }

    Uri child = mStorageUri.buildUpon().path(path).build();
    return new StorageReference(child, mFirebaseStorage);
  }

  /**
   * Returns a new instance of {@link StorageReference} pointing to the root location.
   *
   * @return the root {@link StorageReference}.
   */
  @NonNull
  public StorageReference getRoot() {
    Uri child = mStorageUri.buildUpon().path("").build();
    return new StorageReference(child, mFirebaseStorage);
  }

  /**
   * Returns the short name of this object.
   *
   * @return the name.
   */
  @NonNull
  public String getName() {
    String path = mStorageUri.getPath();
    assert path != null;
    int lastIndex = path.lastIndexOf('/');
    if (lastIndex != -1) {
      return path.substring(lastIndex + 1);
    }
    return path;
  }

  /**
   * Returns the full path to this object, not including the Google Cloud Storage bucket.
   *
   * @return the path.
   */
  @NonNull
  public String getPath() {
    String path = mStorageUri.getPath();
    assert path != null;
    return path;
  }

  /**
   * Return the Google Cloud Storage bucket that holds this object.
   *
   * @return the bucket.
   */
  @NonNull
  public String getBucket() {
    return mStorageUri.getAuthority();
  }

  /**
   * Returns the {@link FirebaseStorage} service which created this reference.
   *
   * @return The {@link FirebaseStorage} service.
   */
  @NonNull
  public FirebaseStorage getStorage() {
    return mFirebaseStorage;
  }

  @NonNull
  /*package*/ FirebaseApp getApp() {
    return getStorage().getApp();
  }

  // endregion

  // region Upload Operations

  /**
   * Asynchronously uploads byte data to this {@link StorageReference}. This is not recommended for
   * large files. Instead upload a file via {@link #putFile(Uri)} or an {@link InputStream} via
   * {@link #putStream(InputStream)}.
   *
   * @param bytes The byte array to upload.
   * @return An instance of {@link UploadTask} which can be used to monitor and manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putBytes(@NonNull byte[] bytes) {
    Preconditions.checkArgument(bytes != null, "bytes cannot be null");

    UploadTask task = new UploadTask(this, null, bytes);
    task.queue();
    return task;
  }

  /**
   * Asynchronously uploads byte data to this {@link StorageReference}. This is not recommended for
   * large files. Instead upload a file via {@link #putFile(Uri)} or a Stream via {@link
   * #putStream(InputStream)}.
   *
   * @param bytes The byte[] to upload.
   * @param metadata {@link StorageMetadata} containing additional information (MIME type, etc.)
   *     about the object being uploaded.
   * @return An instance of {@link UploadTask} which can be used to monitor and manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putBytes(@NonNull byte[] bytes, @NonNull StorageMetadata metadata) {
    Preconditions.checkArgument(bytes != null, "bytes cannot be null");
    Preconditions.checkArgument(metadata != null, "metadata cannot be null");

    UploadTask task = new UploadTask(this, metadata, bytes);
    task.queue();
    return task;
  }

  /**
   * Asynchronously uploads from a content URI to this {@link StorageReference}.
   *
   * @param uri The source of the upload. This can be a file:// scheme or any content URI. A content
   *     resolver will be used to load the data.
   * @return An instance of {@link UploadTask} which can be used to monitor or manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putFile(@NonNull Uri uri) {
    Preconditions.checkArgument(uri != null, "uri cannot be null");

    UploadTask task = new UploadTask(this, null, uri, null);
    task.queue();
    return task;
  }

  /**
   * Asynchronously uploads from a content URI to this {@link StorageReference}.
   *
   * @param uri The source of the upload. This can be a file:// scheme or any content URI. A content
   *     resolver will be used to load the data.
   * @param metadata {@link StorageMetadata} containing additional information (MIME type, etc.)
   *     about the object being uploaded.
   * @return An instance of {@link UploadTask} which can be used to monitor or manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putFile(@NonNull Uri uri, @NonNull StorageMetadata metadata) {
    Preconditions.checkArgument(uri != null, "uri cannot be null");
    Preconditions.checkArgument(metadata != null, "metadata cannot be null");

    UploadTask task = new UploadTask(this, metadata, uri, null);
    task.queue();
    return task;
  }

  /**
   * Asynchronously uploads from a content URI to this {@link StorageReference}.
   *
   * @param uri The source of the upload. This can be a file:// scheme or any content URI. A content
   *     resolver will be used to load the data.
   * @param metadata {@link StorageMetadata} containing additional information (MIME type, etc.)
   *     about the object being uploaded.
   * @param existingUploadUri If set, an attempt is made to resume an existing upload session as
   *     defined by {@link UploadTask.TaskSnapshot#getUploadSessionUri()}.
   * @return An instance of {@link UploadTask} which can be used to monitor or manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putFile(
      @NonNull Uri uri, @Nullable StorageMetadata metadata, @Nullable Uri existingUploadUri) {
    Preconditions.checkArgument(uri != null, "uri cannot be null");
    Preconditions.checkArgument(metadata != null, "metadata cannot be null");

    UploadTask task = new UploadTask(this, metadata, uri, existingUploadUri);
    task.queue();
    return task;
  }

  /**
   * Asynchronously uploads a stream of data to this {@link StorageReference}. The stream will
   * remain open at the end of the upload.
   *
   * @param stream The {@link InputStream} to upload.
   * @return An instance of {@link UploadTask} which can be used to monitor and manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putStream(@NonNull InputStream stream) {
    Preconditions.checkArgument(stream != null, "stream cannot be null");

    UploadTask task = new UploadTask(this, null, stream);
    task.queue();
    return task;
  }

  /**
   * Asynchronously uploads a stream of data to this {@link StorageReference}. The stream will
   * remain open at the end of the upload.
   *
   * @param stream The {@link InputStream} to upload.
   * @param metadata {@link StorageMetadata} containing additional information (MIME type, etc.)
   *     about the object being uploaded.
   * @return An instance of {@link UploadTask} which can be used to monitor and manage the upload.
   */
  @SuppressWarnings("ConstantConditions")
  @NonNull
  public UploadTask putStream(@NonNull InputStream stream, @NonNull StorageMetadata metadata) {
    Preconditions.checkArgument(stream != null, "stream cannot be null");
    Preconditions.checkArgument(metadata != null, "metadata cannot be null");

    UploadTask task = new UploadTask(this, metadata, stream);
    task.queue();
    return task;
  }

  // endregion

  // region LifeCycle support

  /** @return the set of active upload tasks currently in progress or recently completed. */
  @NonNull
  public List<UploadTask> getActiveUploadTasks() {
    return StorageTaskManager.getInstance().getUploadTasksUnder(this);
  }

  /** @return the set of active download tasks currently in progress or recently completed. */
  @NonNull
  public List<FileDownloadTask> getActiveDownloadTasks() {
    return StorageTaskManager.getInstance().getDownloadTasksUnder(this);
  }
  // endregion

  // region Metadata

  /**
   * Retrieves metadata associated with an object at this {@link StorageReference}.
   *
   * @return the metadata.
   */
  @SuppressWarnings("deprecation")
  @NonNull
  public Task<StorageMetadata> getMetadata() {
    TaskCompletionSource<StorageMetadata> pendingResult = new TaskCompletionSource<>();
    StorageTaskScheduler.getInstance().scheduleCommand(new GetMetadataTask(this, pendingResult));
    return pendingResult.getTask();
  }

  /**
   * Asynchronously retrieves a long lived download URL with a revokable token. This can be used to
   * share the file with others, but can be revoked by a developer in the Firebase Console if
   * desired.
   *
   * @return The {@link Uri} representing the download URL. You can feed this URL into a {@link
   *     java.net.URL} and download the object via {@link URL#openStream()}.
   */
  @SuppressWarnings("deprecation,unused")
  @NonNull
  public Task<Uri> getDownloadUrl() {
    TaskCompletionSource<Uri> pendingResult = new TaskCompletionSource<>();
    StorageTaskScheduler.getInstance().scheduleCommand(new GetDownloadUrlTask(this, pendingResult));
    return pendingResult.getTask();
  }

  /**
   * Updates the metadata associated with this {@link StorageReference}.
   *
   * @param metadata A {@link StorageMetadata} object with the metadata to update.
   * @return a {@link Task} that will return the final {@link StorageMetadata} once the operation is
   *     complete.
   */
  @SuppressWarnings("deprecation")
  @NonNull
  public Task<StorageMetadata> updateMetadata(@NonNull StorageMetadata metadata) {
    Preconditions.checkNotNull(metadata);

    TaskCompletionSource<StorageMetadata> pendingResult = new TaskCompletionSource<>();
    StorageTaskScheduler.getInstance()
        .scheduleCommand(new UpdateMetadataTask(this, pendingResult, metadata));
    return pendingResult.getTask();
  }
  // endregion

  // region Download Operations

  /**
   * Asynchronously downloads the object from this {@link StorageReference} A byte array will be
   * allocated large enough to hold the entire file in memory. Therefore, using this method will
   * impact memory usage of your process. If you are downloading many large files, {@link
   * StorageReference#getStream(StreamDownloadTask.StreamProcessor)} may be a better option.
   *
   * @param maxDownloadSizeBytes The maximum allowed size in bytes that will be allocated. Set this
   *     parameter to prevent out of memory conditions from occurring. If the download exceeds this
   *     limit, the task will fail and an {@link IndexOutOfBoundsException} will be returned.
   * @return The bytes downloaded.
   */
  @SuppressWarnings("deprecation")
  @NonNull
  public Task<byte[]> getBytes(final long maxDownloadSizeBytes) {
    final TaskCompletionSource<byte[]> pendingResult = new TaskCompletionSource<>();

    StreamDownloadTask task = new StreamDownloadTask(this);
    task.setStreamProcessor(
            new StreamDownloadTask.StreamProcessor() {
              @Override
              public void doInBackground(StreamDownloadTask.TaskSnapshot state, InputStream stream)
                  throws IOException {
                try {
                  ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                  int totalRead = 0;
                  int nRead;
                  byte[] data = new byte[16384];
                  while ((nRead = stream.read(data, 0, data.length)) != -1) {
                    totalRead += nRead;
                    if (totalRead > maxDownloadSizeBytes) {
                      Log.e(TAG, "the maximum allowed buffer size was exceeded.");
                      throw new IndexOutOfBoundsException(
                          "the maximum allowed buffer size was exceeded.");
                    }
                    buffer.write(data, 0, nRead);
                  }
                  buffer.flush();
                  pendingResult.setResult(buffer.toByteArray());
                } finally {
                  stream.close();
                }
              }
            })
        .addOnSuccessListener(
            new OnSuccessListener<StreamDownloadTask.TaskSnapshot>() {
              @Override
              public void onSuccess(StreamDownloadTask.TaskSnapshot state) {
                if (!pendingResult.getTask().isComplete()) {
                  // something went wrong and we didn't set results, but we think it worked.
                  Log.e(TAG, "getBytes 'succeeded', but failed to set a Result.");
                  pendingResult.setException(
                      StorageException.fromErrorStatus(Status.RESULT_INTERNAL_ERROR));
                }
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                StorageException se = StorageException.fromExceptionAndHttpCode(e, 0);
                assert se != null;
                pendingResult.setException(se);
              }
            });
    task.queue();

    return pendingResult.getTask();
  }

  /**
   * Asynchronously downloads the object at this {@link StorageReference} to a specified system
   * filepath.
   *
   * @param destinationUri A file system URI representing the path the object should be downloaded
   *     to.
   * @return A {@link FileDownloadTask} that can be used to monitor or manage the download.
   */
  @NonNull
  public FileDownloadTask getFile(@NonNull Uri destinationUri) {
    FileDownloadTask task = new FileDownloadTask(this, destinationUri);
    task.queue();
    return task;
  }

  /**
   * Asynchronously downloads the object at this {@link StorageReference} to a specified system
   * filepath.
   *
   * @param destinationFile A {@link File} representing the path the object should be downloaded to.
   * @return A {@link FileDownloadTask} that can be used to monitor or manage the download.
   */
  @NonNull
  public FileDownloadTask getFile(@NonNull File destinationFile) {
    return getFile(Uri.fromFile(destinationFile));
  }

  /**
   * Asynchronously downloads the object at this {@link StorageReference} via a {@link InputStream}.
   * The InputStream should be read on an {@link OnSuccessListener} registered to run on a
   * background thread via {@link StreamDownloadTask#addOnSuccessListener(Executor,
   * OnSuccessListener)}
   *
   * @return A {@link FileDownloadTask} that can be used to monitor or manage the download.
   */
  @NonNull
  public StreamDownloadTask getStream() {
    StreamDownloadTask task = new StreamDownloadTask(this);
    task.queue();
    return task;
  }

  /**
   * Asynchronously downloads the object at this {@link StorageReference} via a {@link InputStream}.
   *
   * @param processor A {@link StreamDownloadTask.StreamProcessor} that is responsible for reading
   *     data from the {@link InputStream}. The {@link StreamDownloadTask.StreamProcessor} is called
   *     on a background thread and checked exceptions thrown from this object will be returned as a
   *     failure to the {@link OnFailureListener} registered on the {@link StreamDownloadTask}.
   * @return A {@link FileDownloadTask} that can be used to monitor or manage the download.
   */
  @NonNull
  public StreamDownloadTask getStream(@NonNull StreamDownloadTask.StreamProcessor processor) {
    StreamDownloadTask task = new StreamDownloadTask(this);
    task.setStreamProcessor(processor);
    task.queue();
    return task;
  }
  // endregion

  // region Delete

  /**
   * Deletes the object at this {@link StorageReference}.
   *
   * @return A {@link Task} that indicates whether the operation succeeded or failed.
   */
  @NonNull
  public Task<Void> delete() {
    TaskCompletionSource<Void> pendingResult = new TaskCompletionSource<>();
    StorageTaskScheduler.getInstance().scheduleCommand(new DeleteStorageTask(this, pendingResult));
    return pendingResult.getTask();
  }

  // region List

  /**
   * List up to {@code maxResults} items (files) and prefixes (folders) under this StorageReference.
   *
   * <p>"/" is treated as a path delimiter. Cloud Storage for Firebase does not support object paths
   * that end with "/" or contain two consecutive "/"s. All invalid objects in Google Cloud Storage
   * will be filtered.
   *
   * <p>{@code list()} is only available for projects using <a
   * href="https://firebase.google.com/docs/rules/rules-behavior#security_rules_version_2">Firebase
   * Rules Version 2</a>.
   *
   * @param maxResults The maximum number of results to return in a single page. Must be greater
   *     than 0 and at most 1000.
   * @return A a {@link Task} that returns up to maxResults items and prefixes under the current
   *     StorageReference.
   */
  @NonNull
  public Task<ListResult> list(int maxResults) {
    Preconditions.checkArgument(maxResults > 0, "maxResults must be greater than zero");
    Preconditions.checkArgument(maxResults <= 1000, "maxResults must be at most 1000");
    return listHelper(maxResults, /* pageToken */ null);
  }

  /**
   * Resumes a previous call to {@link #list(int)}, starting after a pagination token. Returns the
   * next set of items (files) and prefixes (folders) under this StorageReference.
   *
   * <p>"/" is treated as a path delimiter. Cloud Storage for Firebase does not support object paths
   * that end with "/" or contain two consecutive "/"s. All invalid objects in Google Cloud Storage
   * will be filtered.
   *
   * <p>{@code list()} is only available for projects using <a
   * href="https://firebase.google.com/docs/rules/rules-behavior#security_rules_version_2">Firebase
   * Rules Version 2</a>.
   *
   * @param maxResults The maximum number of results to return in a single page. Must be greater
   *     than 0 and at most 1000.
   * @param pageToken A page token from a previous call to list.
   * @return A a {@link Task} that returns the next items and prefixes under the current
   *     StorageReference.
   */
  @NonNull
  public Task<ListResult> list(int maxResults, @NonNull String pageToken) {
    Preconditions.checkArgument(maxResults > 0, "maxResults must be greater than zero");
    Preconditions.checkArgument(maxResults <= 1000, "maxResults must be at most 1000");
    Preconditions.checkArgument(
        pageToken != null, "pageToken must be non-null to resume a previous list() operation");
    return listHelper(maxResults, pageToken);
  }

  /**
   * List all items (files) and prefixes (folders) under this StorageReference.
   *
   * <p>This is a helper method for calling {@code list()} repeatedly until there are no more
   * results. Consistency of the result is not guaranteed if objects are inserted or removed while
   * this operation is executing.
   *
   * <p>{@code listAll()} is only available for projects using <a
   * href="https://firebase.google.com/docs/rules/rules-behavior#security_rules_version_2">Firebase
   * Rules Version 2</a>.
   *
   * @throws OutOfMemoryError If there are too many items at this location.
   * @return A {@link Task} that returns all items and prefixes under the current StorageReference.
   */
  @NonNull
  public Task<ListResult> listAll() {
    TaskCompletionSource<ListResult> pendingResult = new TaskCompletionSource<>();

    List<StorageReference> prefixes = new ArrayList<>();
    List<StorageReference> items = new ArrayList<>();

    Executor executor = StorageTaskScheduler.getInstance().getCommandPoolExecutor();
    Task<ListResult> list = listHelper(/* maxResults= */ null, /* pageToken= */ null);

    Continuation<ListResult, Task<Void>> continuation =
        new Continuation<ListResult, Task<Void>>() {
          @Override
          public Task<Void> then(@NonNull Task<ListResult> currentPage) {
            if (currentPage.isSuccessful()) {
              ListResult result = currentPage.getResult();
              prefixes.addAll(result.getPrefixes());
              items.addAll(result.getItems());

              if (result.getPageToken() != null) {
                Task<ListResult> nextPage =
                    listHelper(/* maxResults= */ null, result.getPageToken());
                nextPage.continueWithTask(executor, this);
              } else {
                pendingResult.setResult(new ListResult(prefixes, items, /* pageToken= */ null));
              }
            } else {
              pendingResult.setException(currentPage.getException());
            }

            return Tasks.forResult(null);
          }
        };

    list.continueWithTask(executor, continuation);

    return pendingResult.getTask();
  }

  private Task<ListResult> listHelper(@Nullable Integer maxResults, @Nullable String pageToken) {
    TaskCompletionSource<ListResult> pendingResult = new TaskCompletionSource<>();
    StorageTaskScheduler.getInstance()
        .scheduleCommand(new ListTask(this, maxResults, pageToken, pendingResult));
    return pendingResult.getTask();
  }

  // endregion

  // region package private methods
  @NonNull
  /*package*/ Uri getStorageUri() {
    return mStorageUri;
  }

  // endregion

  /**
   * @return This object in URI form, which can then be shared and passed into {@link
   *     FirebaseStorage#getReferenceFromUrl(String)}.
   */
  @Override
  public String toString() {
    return "gs://" + mStorageUri.getAuthority() + mStorageUri.getEncodedPath();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof StorageReference)) {
      return false;
    }
    StorageReference otherStorage = (StorageReference) other;
    return (otherStorage.toString().equals(toString()));
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public int compareTo(@NonNull StorageReference other) {
    // mStorageUri contains a reference to the GCS bucket as well as the fully qualified path
    // of this reference.
    return mStorageUri.compareTo(other.mStorageUri);
  }
}
