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

import static com.google.firebase.storage.internal.ExponentialBackoffSender.RND_MAX;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.common.util.DefaultClock;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.storage.internal.AdaptiveStreamBuffer;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.internal.Sleeper;
import com.google.firebase.storage.internal.SleeperImpl;
import com.google.firebase.storage.internal.Util;
import com.google.firebase.storage.network.NetworkRequest;
import com.google.firebase.storage.network.ResumableUploadByteRequest;
import com.google.firebase.storage.network.ResumableUploadCancelRequest;
import com.google.firebase.storage.network.ResumableUploadQueryRequest;
import com.google.firebase.storage.network.ResumableUploadStartRequest;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONException;

/**
 * An controllable task that uploads and fires events for success, progress and failure. It also
 * allows pause and resume to control the upload operation.
 */
@SuppressWarnings("unused")
public class UploadTask extends StorageTask<UploadTask.TaskSnapshot> {
  @VisibleForTesting static final int PREFERRED_CHUNK_SIZE = 256 * 1024; // 256 KB
  private static final int MAXIMUM_CHUNK_SIZE = 32 * 1024 * 1024; // 32 MB
  private static final String TAG = "UploadTask";
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final String X_GOOG_UPLOAD_URL = "X-Goog-Upload-URL";
  private static final String RESUMABLE_FINAL_STATUS = "final";
  private final StorageReference mStorageRef;
  private final Uri mUri;
  private final long mTotalByteCount;
  private final AdaptiveStreamBuffer mStreamBuffer;
  // Active, current mutable state.
  private final AtomicLong mBytesUploaded = new AtomicLong(0);
  @Nullable private final InternalAuthProvider mAuthProvider;
  @Nullable private final InteropAppCheckTokenProvider mAppCheckProvider;
  private int mCurrentChunkSize = PREFERRED_CHUNK_SIZE;
  private ExponentialBackoffSender mSender;
  private boolean mIsStreamOwned;
  private volatile StorageMetadata mMetadata;
  private volatile Uri mUploadUri = null;
  private volatile Exception mException = null;
  private volatile Exception mServerException = null;
  private volatile int mResultCode = 0;
  private volatile String mServerStatus;
  private volatile long maxSleepTime;
  private static final Random random = new Random();
  /*package*/ static Sleeper sleeper = new SleeperImpl();
  /*package*/ static Clock clock = DefaultClock.getInstance();
  private int sleepTime = 0;
  private final int minimumSleepInterval = 1000;

  UploadTask(StorageReference targetRef, StorageMetadata metadata, byte[] bytes) {
    Preconditions.checkNotNull(targetRef);
    Preconditions.checkNotNull(bytes);

    FirebaseStorage storage = targetRef.getStorage();

    this.mTotalByteCount = bytes.length;
    this.mStorageRef = targetRef;
    this.mMetadata = metadata;
    this.mAuthProvider = storage.getAuthProvider();
    this.mAppCheckProvider = storage.getAppCheckProvider();
    this.mUri = null;
    this.mStreamBuffer =
        new AdaptiveStreamBuffer(new ByteArrayInputStream(bytes), PREFERRED_CHUNK_SIZE);
    this.mIsStreamOwned = true;

    this.maxSleepTime = storage.getMaxChunkUploadRetry();
    mSender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            mAuthProvider,
            mAppCheckProvider,
            storage.getMaxDownloadRetryTimeMillis());
  }

  UploadTask(
      StorageReference targetRef, StorageMetadata metadata, Uri file, Uri existingUploadUri) {
    Preconditions.checkNotNull(targetRef);
    Preconditions.checkNotNull(file);

    FirebaseStorage storage = targetRef.getStorage();

    this.mStorageRef = targetRef;
    this.mMetadata = metadata;
    this.mAuthProvider = storage.getAuthProvider();
    this.mAppCheckProvider = storage.getAppCheckProvider();
    this.mUri = file;
    InputStream inputStream = null;
    this.maxSleepTime = storage.getMaxChunkUploadRetry();
    mSender =
        new ExponentialBackoffSender(
            mStorageRef.getApp().getApplicationContext(),
            mAuthProvider,
            mAppCheckProvider,
            storage.getMaxUploadRetryTimeMillis());
    long size = -1;
    try {
      ContentResolver resolver =
          mStorageRef.getStorage().getApp().getApplicationContext().getContentResolver();
      ParcelFileDescriptor fd = null;
      try {
        fd = resolver.openFileDescriptor(mUri, "r");
        if (fd != null) {
          size = fd.getStatSize();
          fd.close();
        }
      } catch (NullPointerException npe) {
        // happens under test.
        Log.w(TAG, "NullPointerException during file size calculation.", npe);
        size = -1;
      } catch (IOException checkSizeError) {
        Log.w(TAG, "could not retrieve file size for upload " + mUri.toString(), checkSizeError);
      }

      inputStream = resolver.openInputStream(mUri);
      if (inputStream != null) {
        if (size == -1) {
          // If we had issues calculating the size, try stream.available -- it may still work
          try {
            int streamSize = inputStream.available();
            if (streamSize >= 0) {
              size = streamSize;
            }
          } catch (IOException e) {
            // Ignore the error and continue without a size.  We document it may not be there.
          }
        }
        inputStream = new BufferedInputStream(inputStream);
      }
    } catch (FileNotFoundException e) {
      Log.e(TAG, "could not locate file for uploading:" + mUri.toString());
      mException = e; // this marks this task as failure(final)
    }
    this.mTotalByteCount = size;
    this.mStreamBuffer = new AdaptiveStreamBuffer(inputStream, PREFERRED_CHUNK_SIZE);
    this.mIsStreamOwned = true;
    this.mUploadUri = existingUploadUri;
  }

  UploadTask(StorageReference targetRef, StorageMetadata metadata, InputStream stream) {
    Preconditions.checkNotNull(targetRef);
    Preconditions.checkNotNull(stream);

    FirebaseStorage storage = targetRef.getStorage();

    this.mTotalByteCount = -1;
    this.mStorageRef = targetRef;
    this.mMetadata = metadata;
    this.mAuthProvider = storage.getAuthProvider();
    this.mAppCheckProvider = storage.getAppCheckProvider();
    this.mStreamBuffer = new AdaptiveStreamBuffer(stream, PREFERRED_CHUNK_SIZE);
    this.mIsStreamOwned = false;
    this.mUri = null;
    this.maxSleepTime = storage.getMaxChunkUploadRetry();
    mSender =
        new ExponentialBackoffSender(
            mStorageRef.getApp().getApplicationContext(),
            mAuthProvider,
            mAppCheckProvider,
            storage.getMaxUploadRetryTimeMillis());
  }

  /** @return the target of the upload. */
  @Override
  /*package*/ StorageReference getStorage() {
    return mStorageRef;
  }

  /** @return The number of bytes to upload. Will return -1 if uploading from a stream. */
  @SuppressWarnings("unused")
  /*package*/ long getTotalByteCount() {
    return mTotalByteCount;
  }

  @Override
  protected void schedule() {
    StorageTaskScheduler.getInstance().scheduleUpload(getRunnable());
  }

  /** @hide */
  @SuppressWarnings("JavaDoc")
  @Override
  /*package*/ void run() {
    mSender.reset();
    if (!tryChangeState(INTERNAL_STATE_IN_PROGRESS, false)) {
      // Unexpected starting state, bail out before trying any network ops.
      Log.d(TAG, "The upload cannot continue as it is not in a valid state.");
      return;
    }

    if (mStorageRef.getParent() == null) {
      mException =
          new IllegalArgumentException(
              "Cannot upload to getRoot. You should upload to a "
                  + "storage location such as .getReference('image.png').putFile...");
    }

    if (mException != null) {
      return;
    }

    if (mUploadUri == null) {
      beginResumableUpload();
    } else {
      recoverStatus(false);
    }

    boolean shouldContinueToRun = shouldContinue();
    while (shouldContinueToRun) {
      uploadChunk();
      shouldContinueToRun = shouldContinue();
      // we do not fire a last progress event, thats simply "complete" or "fail"
      if (shouldContinueToRun) {
        tryChangeState(INTERNAL_STATE_IN_PROGRESS, false); // progress
      }
    }

    if (mIsStreamOwned && getInternalState() != INTERNAL_STATE_PAUSED) {
      try {
        mStreamBuffer.close();
      } catch (IOException e) {
        Log.e(TAG, "Unable to close stream.", e);
      }
    }
  }

  @Override
  protected void resetState() {
    mException = null;
    mServerException = null;
    mResultCode = 0;
    mServerStatus = null;
  }

  private void beginResumableUpload() {
    // calculate the mimetype
    String mimeType = null;
    if (mMetadata != null) {
      mimeType = mMetadata.getContentType();
    }
    if (mUri != null && TextUtils.isEmpty(mimeType)) {
      Context context = mStorageRef.getStorage().getApp().getApplicationContext();
      mimeType = context.getContentResolver().getType(mUri);
    }
    if (TextUtils.isEmpty(mimeType)) {
      mimeType = APPLICATION_OCTET_STREAM;
    }
    NetworkRequest startRequest =
        new ResumableUploadStartRequest(
            mStorageRef.getStorageReferenceUri(),
            mStorageRef.getApp(),
            mMetadata != null ? mMetadata.createJSONObject() : null,
            mimeType);

    if (!sendWithRetry(startRequest)) {
      return;
    }
    String uploadURL = startRequest.getResultString(X_GOOG_UPLOAD_URL);
    if (!TextUtils.isEmpty(uploadURL)) {
      mUploadUri = Uri.parse(uploadURL);
    }
  }

  /**
   * If this method returns false, it is also responsible for ensuring the state has changed to a
   * valid final state (Canceled, Paused, Failure, Final)
   */
  private boolean shouldContinue() {
    if (getInternalState() == INTERNAL_STATE_SUCCESS) {
      return false; // already final/complete
    }

    // The following states are non-recoverable and cause us to kick out.
    if (Thread.interrupted()) {
      mException = new InterruptedException();
      tryChangeState(INTERNAL_STATE_FAILURE, false);
      return false;
    }
    if (getInternalState() == INTERNAL_STATE_CANCELING) {
      tryChangeState(INTERNAL_STATE_CANCELED, false);
      return false;
    }
    if (getInternalState() == INTERNAL_STATE_PAUSING) {
      tryChangeState(INTERNAL_STATE_PAUSED, false);
      return false;
    }
    if (!serverStateValid()) {
      return false;
    }
    if (mUploadUri == null) {
      if (mException == null) {
        mException = new IllegalStateException("Unable to obtain an upload URL.");
      }
      tryChangeState(INTERNAL_STATE_FAILURE, false);
      return false;
    }
    if (mException != null) {
      tryChangeState(INTERNAL_STATE_FAILURE, false);
      return false;
    }

    boolean inErrorState = mServerException != null || mResultCode < 200 || mResultCode >= 300;
    long deadLine = clock.elapsedRealtime() + this.maxSleepTime;
    long currentTime = clock.elapsedRealtime() + this.sleepTime;
    // we attempt to recover by calling recoverStatus(true)
    if (inErrorState) {
      if (currentTime > deadLine || !recoverStatus(true)) {
        if (serverStateValid()) {
          tryChangeState(INTERNAL_STATE_FAILURE, false);
        }
        return false;
      }
      sleepTime = Math.max(sleepTime * 2, minimumSleepInterval);
    }
    return true;
  }

  private boolean serverStateValid() {
    if (RESUMABLE_FINAL_STATUS.equals(mServerStatus)) {
      if (mException == null) {
        mException =
            new IOException("The server has terminated the upload session", mServerException);
      }
      tryChangeState(INTERNAL_STATE_FAILURE, false);
      return false;
    }
    return true;
  }

  /**
   * This is our recovery method that queries current state and attempts to sync our state with the
   * server. If withRetry = true, it indicates we are in a failback mode and a return of false at
   * this point will cause the upload process to kick out back to the developer.
   */
  private boolean recoverStatus(boolean withRetry) {
    NetworkRequest queryRequest =
        new ResumableUploadQueryRequest(
            mStorageRef.getStorageReferenceUri(), mStorageRef.getApp(), mUploadUri);

    if (RESUMABLE_FINAL_STATUS.equals(mServerStatus)) {
      return false;
    }

    if (withRetry) {
      if (!sendWithRetry(queryRequest)) {
        return false;
      }
    } else {
      if (!send(queryRequest)) {
        return false;
      }
    }

    if (RESUMABLE_FINAL_STATUS.equals(queryRequest.getResultString("X-Goog-Upload-Status"))) {
      mException = new IOException("The server has terminated the upload session");
      return false;
    }

    String bytes = queryRequest.getResultString("X-Goog-Upload-Size-Received");
    long newBytesUploaded;
    if (!TextUtils.isEmpty(bytes)) {
      newBytesUploaded = Long.parseLong(bytes);
    } else {
      newBytesUploaded = 0;
    }
    long currentBytes = mBytesUploaded.get();
    if (currentBytes > newBytesUploaded) {
      mException = new IOException("Unexpected error. The server lost a chunk update.");
      return false;
    } else if (currentBytes < newBytesUploaded) {
      try {
        if (mStreamBuffer.advance((int) (newBytesUploaded - currentBytes))
            != newBytesUploaded - currentBytes) {
          mException = new IOException("Unexpected end of stream encountered.");
          return false;
        }
        if (!mBytesUploaded.compareAndSet(currentBytes, newBytesUploaded)) {
          Log.e(
              TAG,
              "Somehow, the uploaded bytes changed during an uploaded.  This should not"
                  + "happen");
          mException = new IllegalStateException("uploaded bytes changed unexpectedly.");
          return false;
        }
      } catch (IOException e) {
        Log.e(TAG, "Unable to recover position in Stream during resumable upload", e);

        mException = e;
        return false;
      }
    }
    return true;
  }

  /**
   * Send with a delay that uses sleepTime to delay sending a request to the server. Will reset
   * sleepTime upon send success. TODO: Create an exponential backoff helper to consolidate code
   * here and in ExponentialBackoffSender.java
   *
   * @param request to send
   * @return whether the delay and send were successful
   */
  private boolean delaySend(NetworkRequest request) {
    try {
      Log.d(TAG, "Waiting " + sleepTime + " milliseconds");
      sleeper.sleep(sleepTime + random.nextInt(RND_MAX));
    } catch (InterruptedException e) {
      Log.w(TAG, "thread interrupted during exponential backoff.");

      Thread.currentThread().interrupt();
      mServerException = e;
      return false;
    }
    boolean sendRes = send(request);
    // We reset the sleepTime if the send was successful. For example,
    // uploadChunk(request) // false, then sleepTime becomes 1000
    // uploadChunk(request) // false, then sleepTime becomes 2000
    // uploadChunk(request) // true, then sleepTime becomes 0 again
    if (sendRes) {
      sleepTime = 0;
    }
    return sendRes;
  }

  private void uploadChunk() {
    try {
      mStreamBuffer.fill(mCurrentChunkSize);
      int bytesToUpload = Math.min(mCurrentChunkSize, mStreamBuffer.available());

      NetworkRequest uploadRequest =
          new ResumableUploadByteRequest(
              mStorageRef.getStorageReferenceUri(),
              mStorageRef.getApp(),
              mUploadUri,
              mStreamBuffer.get(),
              mBytesUploaded.get(),
              bytesToUpload,
              mStreamBuffer.isFinished());

      if (!delaySend(uploadRequest)) {
        mCurrentChunkSize = PREFERRED_CHUNK_SIZE;
        Log.d(TAG, "Resetting chunk size to " + mCurrentChunkSize);
        return;
      }

      mBytesUploaded.getAndAdd(bytesToUpload);

      if (!mStreamBuffer.isFinished()) {
        mStreamBuffer.advance(bytesToUpload);
        if (mCurrentChunkSize < MAXIMUM_CHUNK_SIZE) {
          mCurrentChunkSize *= 2;
          Log.d(TAG, "Increasing chunk size to " + mCurrentChunkSize);
        }
      } else {
        try {
          mMetadata =
              new StorageMetadata.Builder(uploadRequest.getResultBody(), mStorageRef).build();
        } catch (JSONException e) {
          Log.e(
              TAG,
              "Unable to parse resulting metadata from upload:" + uploadRequest.getRawResult(),
              e);

          mException = e;
          return;
        }
        tryChangeState(INTERNAL_STATE_IN_PROGRESS, false); // progress
        tryChangeState(INTERNAL_STATE_SUCCESS, false);
      }
    } catch (IOException e) {
      Log.e(TAG, "Unable to read bytes for uploading", e);
      mException = e;
    }
  }

  private boolean isValidHttpResponseCode(int code) {
    return code == 308 || (code >= 200 && code < 300);
  }

  private boolean send(NetworkRequest request) {
    request.performRequest(
        Util.getCurrentAuthToken(mAuthProvider),
        Util.getCurrentAppCheckToken(mAppCheckProvider),
        mStorageRef.getApp().getApplicationContext());
    return processResultValid(request);
  }

  private boolean sendWithRetry(NetworkRequest request) {
    mSender.sendWithExponentialBackoff(request);
    return processResultValid(request);
  }

  private boolean processResultValid(NetworkRequest request) {
    int resultCode = request.getResultCode();
    mResultCode = mSender.isRetryableError(resultCode) ? Util.NETWORK_UNAVAILABLE : resultCode;
    mServerException = request.getException();
    mServerStatus = request.getResultString("X-Goog-Upload-Status");
    return isValidHttpResponseCode(mResultCode) && mServerException == null;
  }

  @Override
  protected void onCanceled() {
    mSender.cancel();

    NetworkRequest cancelRequest = null;
    if (mUploadUri != null) {
      cancelRequest =
          new ResumableUploadCancelRequest(
              mStorageRef.getStorageReferenceUri(), mStorageRef.getApp(), mUploadUri);
    }

    if (cancelRequest != null) {
      final NetworkRequest finalCancelRequest = cancelRequest;
      StorageTaskScheduler.getInstance()
          .scheduleCommand(
              new Runnable() {
                @Override
                public void run() {
                  finalCancelRequest.performRequest(
                      Util.getCurrentAuthToken(mAuthProvider),
                      Util.getCurrentAppCheckToken(mAppCheckProvider),
                      mStorageRef.getApp().getApplicationContext());
                }
              });
    }
    mException = StorageException.fromErrorStatus(Status.RESULT_CANCELED);

    super.onCanceled();
  }

  @Override
  @NonNull
  /*package*/ TaskSnapshot snapStateImpl() {
    Exception error = mException != null ? mException : mServerException;
    return new TaskSnapshot(
        StorageException.fromExceptionAndHttpCode(error, mResultCode),
        mBytesUploaded.get(),
        mUploadUri,
        mMetadata);
  }

  /** Encapsulates state about the running {@link UploadTask} */
  public class TaskSnapshot extends StorageTask<UploadTask.TaskSnapshot>.SnapshotBase {
    private final long mBytesUploaded;
    private final Uri mUploadUri;
    private final StorageMetadata mMetadata;

    /*
     * For some reason the @NonNull/@Nullable annotations here trigger a javac bug that results in:
     * "bad RuntimeInvisibleParameterAnnotations attribute"
     * https://bugs.openjdk.java.net/browse/JDK-8066725
     */
    /*package*/ TaskSnapshot(
        /*Nullable*/ Exception error, long bytesuploaded, Uri uploadUri, StorageMetadata metadata) {
      super(error);
      mBytesUploaded = bytesuploaded;
      mUploadUri = uploadUri;
      mMetadata = metadata;
    }

    /** @return the total bytes uploaded so far. */
    public long getBytesTransferred() {
      return mBytesUploaded;
    }

    /** @return The number of bytes to upload. Will return -1 if uploading from a stream. */
    public long getTotalByteCount() {
      return UploadTask.this.getTotalByteCount();
    }

    /**
     * @return the session Uri, valid for approximately one week, which can be used to resume an
     *     upload later by passing this value into {@link StorageReference#putFile(Uri,
     *     StorageMetadata, Uri)}
     */
    @Nullable
    public Uri getUploadSessionUri() {
      return mUploadUri;
    }

    /**
     * @return the metadata for the object. After uploading, this will return the resulting final
     *     Metadata which will include the upload URL.
     */
    @Nullable
    public StorageMetadata getMetadata() {
      return mMetadata;
    }
  }
}
