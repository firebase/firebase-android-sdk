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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.annotations.PublicApi;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.GetNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;

/** A task that downloads bytes of a GCS blob. */
@SuppressWarnings("unused")
@PublicApi
public class StreamDownloadTask extends StorageTask<StreamDownloadTask.TaskSnapshot> {
  static final long PREFERRED_CHUNK_SIZE = 256 * 1024;
  private static final String TAG = "StreamDownloadTask";
  private StorageReference mStorageRef;
  private ExponentialBackoffSender mSender;
  private volatile Exception mException = null;
  private volatile int mResultCode = 0;
  private StreamProcessor mProcessor;

  private long mTotalBytes = -1;
  private long mBytesDownloaded;
  private long mBytesDownloadedSnapped;
  private InputStream mInputStream;
  private NetworkRequest mRequest;
  private String mETagVerification;

  /*package*/ StreamDownloadTask(@NonNull StorageReference storageRef) {
    mStorageRef = storageRef;

    FirebaseStorage storage = mStorageRef.getStorage();
    mSender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            storage.getAuthProvider(),
            storage.getMaxDownloadRetryTimeMillis());
  }

  /**
   * Sets the class used to process the incoming {@link InputStream}. The {@link StreamProcessor}
   * may not be changed once set. If setStreamProcess is called twice, it will result in an
   * IllegalStateException.
   *
   * @param processor the processor of the stream, which will be called on a background thread.
   */
  /*package*/ StreamDownloadTask setStreamProcessor(@NonNull StreamProcessor processor) {
    Preconditions.checkNotNull(processor);
    Preconditions.checkState(mProcessor == null);
    this.mProcessor = processor;
    return this;
  }

  /** @return the target of the download. */
  @Override
  @NonNull
  /*package*/ StorageReference getStorage() {
    return mStorageRef;
  }

  /**
   * @return the total number of bytes to be downloaded. -1 if the content length is not known (if
   *     the source is sending using chunk encoding)
   */
  @SuppressWarnings("unused")
  /*package*/ long getTotalBytes() {
    return mTotalBytes;
  }

  void recordDownloadedBytes(long bytesDownloaded) {
    mBytesDownloaded += bytesDownloaded;
    if (mBytesDownloadedSnapped + PREFERRED_CHUNK_SIZE <= mBytesDownloaded) {
      if (getInternalState() == INTERNAL_STATE_IN_PROGRESS) {
        tryChangeState(INTERNAL_STATE_IN_PROGRESS, false);
      } else {
        mBytesDownloadedSnapped = mBytesDownloaded;
      }
    }
  }

  /** @hide */
  @SuppressWarnings("JavaDoc")
  @Override
  protected void schedule() {
    StorageTaskScheduler.getInstance().scheduleDownload(getRunnable());
  }

  @SuppressWarnings({"JavaDoc", "ThrowableResultOfMethodCallIgnored"})
  private InputStream createDownloadStream() throws Exception {
    mSender.reset();

    if (mRequest != null) {
      mRequest.performRequestEnd();
    }

    mRequest =
        new GetNetworkRequest(mStorageRef.getStorageUri(), mStorageRef.getApp(), mBytesDownloaded);

    mSender.sendWithExponentialBackoff(mRequest, false);
    mResultCode = mRequest.getResultCode();
    mException = mRequest.getException() != null ? mRequest.getException() : mException;
    boolean success =
        isValidHttpResponseCode(mResultCode)
            && mException == null
            && getInternalState() == INTERNAL_STATE_IN_PROGRESS;

    if (success) {
      String newEtag = mRequest.getResultString("ETag");
      if (!TextUtils.isEmpty(newEtag)
          && mETagVerification != null
          && !mETagVerification.equals(newEtag)) {
        mResultCode = HttpURLConnection.HTTP_CONFLICT;
        throw new IOException("The ETag on the server changed.");
      }

      mETagVerification = newEtag;
      if (mTotalBytes == -1) {
        mTotalBytes = mRequest.getResultingContentLength();
      }
      return mRequest.getStream();
    } else {
      throw new IOException("Could not open resulting stream.");
    }
  }

  /** @hide */
  @SuppressWarnings({"JavaDoc", "ThrowableResultOfMethodCallIgnored"})
  @Override
  /*package*/ void run() {
    if (mException != null) {
      tryChangeState(INTERNAL_STATE_FAILURE, false);
      return;
    }

    if (!tryChangeState(INTERNAL_STATE_IN_PROGRESS, false)) {
      return;
    }

    StreamProgressWrapper streamWrapper =
        new StreamProgressWrapper(
            new Callable<InputStream>() {
              @Override
              public InputStream call() throws Exception {
                return createDownloadStream();
              }
            },
            StreamDownloadTask.this);
    mInputStream = new BufferedInputStream(streamWrapper);

    try {
      // Open stream to fetch initial state.
      streamWrapper.ensureStream();

      if (mProcessor != null) {
        try {
          mProcessor.doInBackground(snapState(), mInputStream);
        } catch (Exception e) {
          Log.w(TAG, "Exception occurred calling doInBackground.", e);
          mException = e;
        }
      }
    } catch (IOException e) {
      Log.d(TAG, "Initial opening of Stream failed", e);
      mException = e;
    }

    if (mInputStream == null) {
      mRequest.performRequestEnd();
      mRequest = null;
    }

    boolean success = mException == null && getInternalState() == INTERNAL_STATE_IN_PROGRESS;

    if (success) {
      tryChangeState(INTERNAL_STATE_IN_PROGRESS, false);
      tryChangeState(INTERNAL_STATE_SUCCESS, false);
    } else {
      if (!tryChangeState(
          (getInternalState() == INTERNAL_STATE_CANCELING)
              ? INTERNAL_STATE_CANCELED
              : INTERNAL_STATE_FAILURE,
          false)) {
        Log.w(TAG, "Unable to change download task to final state from " + getInternalState());
      }
    }
  }

  /** @hide */
  @Override
  public boolean resume() {
    throw new UnsupportedOperationException(
        "this operation is not supported on StreamDownloadTask.");
  }

  /** @hide */
  @Override
  public boolean pause() {
    throw new UnsupportedOperationException(
        "this operation is not supported on StreamDownloadTask.");
  }

  @NonNull
  @Override
  TaskSnapshot snapStateImpl() {
    return new TaskSnapshot(
        StorageException.fromExceptionAndHttpCode(mException, mResultCode),
        mBytesDownloadedSnapped);
  }

  @Override
  protected void onCanceled() {
    mSender.cancel();
    mException = StorageException.fromErrorStatus(Status.RESULT_CANCELED);
  }

  @Override
  protected void onProgress() {
    mBytesDownloadedSnapped = mBytesDownloaded;
  }

  private boolean isValidHttpResponseCode(int code) {
    return code == 308 || (code >= 200 && code < 300);
  }

  /** A callback that is used to handle the stream download */
  @PublicApi
  public interface StreamProcessor {
    /**
     * doInBackground gets called on a background thread and should process the input stream to load
     * data as desired. The stream should be closed prior to returning or in the handler {@link
     * com.google.android.gms.tasks.OnSuccessListener#onSuccess(Object)}
     *
     * @param state is the current {@link TaskSnapshot} for this task
     * @param stream the {@link InputStream} for the downloaded bytes.
     * @throws IOException may be thrown to cancel the operation.
     */
    void doInBackground(TaskSnapshot state, InputStream stream) throws IOException;
  }

  static class StreamProgressWrapper extends InputStream {
    @Nullable private StreamDownloadTask mParentTask;
    @Nullable private InputStream mWrappedStream;
    private Callable<InputStream> mInputStreamCallable;
    private IOException mTemporaryException;
    private long mDownloadedBytes;
    private long mLastExceptionPosition;
    private boolean mStreamClosed;

    StreamProgressWrapper(
        @NonNull Callable<InputStream> inputStreamCallable,
        @Nullable StreamDownloadTask parentTask) {
      mParentTask = parentTask;
      mInputStreamCallable = inputStreamCallable;
    }

    private void checkCancel() throws IOException {
      if (mParentTask != null && mParentTask.getInternalState() == INTERNAL_STATE_CANCELING) {
        throw new CancelException();
      }
    }

    private void recordDownloadedBytes(long delta) {
      if (mParentTask != null) {
        mParentTask.recordDownloadedBytes(delta);
      }
      mDownloadedBytes += delta;
    }

    private boolean ensureStream() throws IOException {
      checkCancel();

      if (mTemporaryException != null) {
        try {
          if (mWrappedStream != null) {
            mWrappedStream.close();
          }
        } catch (IOException ignore) {
          // Ignore
        }

        mWrappedStream = null;

        if (mLastExceptionPosition == mDownloadedBytes) {
          Log.i(
              TAG, "Encountered exception during stream operation. Aborting.", mTemporaryException);
          return false;
        } else {
          Log.i(
              TAG,
              "Encountered exception during stream operation. Retrying at " + mDownloadedBytes,
              mTemporaryException);
          mLastExceptionPosition = mDownloadedBytes;
          mTemporaryException = null;
        }
      }

      if (mStreamClosed) {
        throw new IOException("Can't perform operation on closed stream");
      }

      if (mWrappedStream == null) {
        try {
          mWrappedStream = mInputStreamCallable.call();
        } catch (Exception e) {
          if (e instanceof IOException) {
            throw (IOException) e;
          } else {
            throw new IOException("Unable to open stream", e);
          }
        }
      }

      return true;
    }

    @Override
    @PublicApi
    public int read() throws IOException {
      while (ensureStream()) {
        try {
          int returnValue = mWrappedStream.read();
          if (returnValue != -1) {
            recordDownloadedBytes(1);
          }
          return returnValue;
        } catch (IOException e) {
          mTemporaryException = e;
        }
      }

      throw mTemporaryException;
    }

    @Override
    @PublicApi
    public int available() throws IOException {
      while (ensureStream()) {
        try {
          return mWrappedStream.available();
        } catch (IOException e) {
          mTemporaryException = e;
        }
      }

      throw mTemporaryException;
    }

    @Override
    @PublicApi
    public void close() throws IOException {
      if (mWrappedStream != null) {
        mWrappedStream.close();
      }
      mStreamClosed = true;
      if (mParentTask != null && mParentTask.mRequest != null) {
        mParentTask.mRequest.performRequestEnd();
        mParentTask.mRequest = null;
      }

      checkCancel();
    }

    @Override
    @PublicApi
    public void mark(int readlimit) {}

    @Override
    @PublicApi
    public boolean markSupported() {
      return false;
    }

    @Override
    @PublicApi
    public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
      int bytesRead = 0;
      while (ensureStream()) {
        try {
          while (byteCount > PREFERRED_CHUNK_SIZE) {
            int deltaBytesRead =
                mWrappedStream.read(buffer, byteOffset, (int) PREFERRED_CHUNK_SIZE);
            if (deltaBytesRead == -1) {
              return bytesRead == 0 ? -1 : bytesRead;
            }
            bytesRead += deltaBytesRead;
            byteOffset += deltaBytesRead;
            byteCount -= deltaBytesRead;
            recordDownloadedBytes(deltaBytesRead);
            checkCancel();
          }
          if (byteCount > 0) {
            int deltaBytesRead = mWrappedStream.read(buffer, byteOffset, byteCount);
            if (deltaBytesRead == -1) {
              return bytesRead == 0 ? -1 : bytesRead;
            }
            byteOffset += deltaBytesRead;
            bytesRead += deltaBytesRead;
            byteCount -= deltaBytesRead;
            recordDownloadedBytes(deltaBytesRead);
          }
          if (byteCount == 0) {
            return bytesRead;
          }
        } catch (IOException e) {
          mTemporaryException = e;
        }
      }

      throw mTemporaryException;
    }

    @Override
    @PublicApi
    public long skip(long byteCount) throws IOException {
      long bytesSkipped = 0;

      while (ensureStream()) {
        try {
          while (byteCount > PREFERRED_CHUNK_SIZE) {
            long deltaBytesSkipped = mWrappedStream.skip(PREFERRED_CHUNK_SIZE);
            if (deltaBytesSkipped < 0) {
              return bytesSkipped == 0 ? -1 : bytesSkipped;
            }
            bytesSkipped += deltaBytesSkipped;
            byteCount -= deltaBytesSkipped;
            recordDownloadedBytes(deltaBytesSkipped);
            checkCancel();
          }
          if (byteCount > 0) {
            long deltaBytesSkipped = mWrappedStream.skip(byteCount);
            if (deltaBytesSkipped < 0) {
              return bytesSkipped == 0 ? -1 : bytesSkipped;
            }
            bytesSkipped += deltaBytesSkipped;
            byteCount -= deltaBytesSkipped;
            recordDownloadedBytes(deltaBytesSkipped);
          }
          if (byteCount == 0) {
            return bytesSkipped;
          }
        } catch (IOException e) {
          mTemporaryException = e;
        }
      }

      throw mTemporaryException;
    }
  }

  /** Encapsulates state about the running {@link StreamDownloadTask} */
  @PublicApi
  public class TaskSnapshot extends StorageTask<StreamDownloadTask.TaskSnapshot>.SnapshotBase {
    private final long mBytesDownloaded;

    /**
     * For some reason the @NonNull/@nullable annotations here trigger a javac bug that results in:
     * "bad RuntimeInvisibleParameterAnnotations attribute"
     * https://bugs.openjdk.java.net/browse/JDK-8066725
     */
    /*package*/ TaskSnapshot(Exception error, long bytesDownloaded) {
      super(error);
      mBytesDownloaded = bytesDownloaded;
    }

    /** @return the total bytes downloaded so far. */
    @PublicApi
    public long getBytesTransferred() {
      return mBytesDownloaded;
    }

    /** @return the total bytes of the download. */
    @PublicApi
    public long getTotalByteCount() {
      return StreamDownloadTask.this.getTotalBytes();
    }

    /**
     * @return The stream that represents downloaded bytes from Storage. This stream should be
     *     closed either in {@link StreamProcessor#doInBackground(TaskSnapshot, InputStream)} or in
     *     {@link OnSuccessListener}, {@link OnFailureListener}
     */
    @PublicApi
    public InputStream getStream() {
      return StreamDownloadTask.this.mInputStream;
    }
  }
}
