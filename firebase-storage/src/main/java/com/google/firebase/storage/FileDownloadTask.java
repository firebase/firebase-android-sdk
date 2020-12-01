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
import com.google.android.gms.common.api.Status;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.GetNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A task that downloads bytes of a GCS blob to a specified File. */
@SuppressWarnings("unused")
public class FileDownloadTask extends StorageTask<FileDownloadTask.TaskSnapshot> {
  static final int PREFERRED_CHUNK_SIZE = 256 * 1024; // 256KB
  private static final String TAG = "FileDownloadTask";
  private final Uri mDestinationFile;
  private long mBytesDownloaded;
  private StorageReference mStorageRef;
  private ExponentialBackoffSender mSender;
  private long mTotalBytes = -1;
  private String mETagVerification = null;

  private volatile Exception mException = null;
  private long mResumeOffset = 0;
  private int mResultCode;

  /*package*/ FileDownloadTask(@NonNull StorageReference storageRef, @NonNull Uri destinationFile) {
    mStorageRef = storageRef;
    mDestinationFile = destinationFile;

    FirebaseStorage storage = mStorageRef.getStorage();
    mSender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            storage.getAuthProvider(),
            storage.getMaxDownloadRetryTimeMillis());
  }

  /** @return the number of bytes downloaded so far into the file. */
  /*package*/ long getDownloadedSizeInBytes() {
    return mBytesDownloaded;
  }

  /**
   * @return the total number of bytes to be downloaded. -1 if the content length is not known (if
   *     the source is sending using chunk encoding)
   */
  /*package*/ long getTotalBytes() {
    return mTotalBytes;
  }

  /** @return the target of the upload. */
  @NonNull
  @Override
  /*package*/ StorageReference getStorage() {
    return mStorageRef;
  }

  /** @hide */
  @SuppressWarnings("JavaDoc")
  @Override
  protected void schedule() {
    StorageTaskScheduler.getInstance().scheduleDownload(getRunnable());
  }

  @NonNull
  @Override
  TaskSnapshot snapStateImpl() {
    return new TaskSnapshot(
        StorageException.fromExceptionAndHttpCode(mException, mResultCode),
        mBytesDownloaded + mResumeOffset);
  }

  /**
   * Tries to fully saturate 'data' with data from 'stream'. May store an exception in mException.
   *
   * @param stream The strema to read from
   * @param data The buffer to read into
   * @return Number of bytes read, or -1 if no bytes were read.
   */
  private int fillBuffer(InputStream stream, byte[] data) {
    boolean readData = false;

    int offset = 0;
    int count;

    try {
      while (offset != data.length
          && (count = stream.read(data, offset, data.length - offset)) != -1) {
        readData = true;
        offset += count;
      }
    } catch (IOException e) {
      mException = e;
    }

    return readData ? offset : -1;
  }

  /**
   * @return Whether we were able to completely download the file.
   * @throws IOException
   */
  private boolean processResponse(final NetworkRequest request) throws IOException {
    boolean success = true;
    InputStream stream = request.getStream();
    OutputStream output;

    if (stream != null) {
      File outputFile = new File(mDestinationFile.getPath());
      if (!outputFile.exists()) {
        if (mResumeOffset > 0) {
          throw new IOException("The file to download to has been deleted.");
        }
        boolean created = outputFile.createNewFile();
        if (!created) {
          Log.w(TAG, "unable to create file:" + outputFile.getAbsolutePath());
        }
      }
      if (mResumeOffset > 0) {
        Log.d(
            TAG, "Resuming download file " + outputFile.getAbsolutePath() + " at " + mResumeOffset);
        output = new FileOutputStream(outputFile, true);
      } else {
        // truncate if we are starting from scratch.
        output = new FileOutputStream(outputFile);
      }

      try {
        byte data[] = new byte[PREFERRED_CHUNK_SIZE];
        int count;

        while (success && (count = fillBuffer(stream, data)) != -1) {
          output.write(data, 0, count);
          mBytesDownloaded += count;

          if (mException != null) {
            Log.d(TAG, "Exception occurred during file download. Retrying.", mException);
            mException = null;
            success = false;
          }

          if (!tryChangeState(INTERNAL_STATE_IN_PROGRESS, false)) {
            success = false;
          }
        }
      } finally {
        output.flush();
        output.close();
        stream.close();
      }
    } else {
      mException = new IllegalStateException("Unable to open Firebase Storage stream.");
      success = false;
    }

    return success;
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

    do {
      mBytesDownloaded = 0;
      mException = null;
      mSender.reset();
      final NetworkRequest request =
          new GetNetworkRequest(mStorageRef.getStorageUri(), mStorageRef.getApp(), mResumeOffset);

      mSender.sendWithExponentialBackoff(request, false);
      mResultCode = request.getResultCode();
      mException = request.getException() != null ? request.getException() : mException;

      boolean success =
          isValidHttpResponseCode(mResultCode)
              && mException == null
              && getInternalState() == INTERNAL_STATE_IN_PROGRESS;

      if (success) {
        mTotalBytes = request.getResultingContentLength();
        String newEtag = request.getResultString("ETag");
        if (!TextUtils.isEmpty(newEtag)
            && mETagVerification != null
            && !mETagVerification.equals(newEtag)) {
          Log.w(TAG, "The file at the server has changed.  Restarting from the beginning.");
          mResumeOffset = 0;
          mETagVerification = null;
          request.performRequestEnd();
          schedule(); // reschedule
          return;
        }

        mETagVerification = newEtag;

        try {
          success = processResponse(request);
        } catch (IOException e) {
          Log.e(TAG, "Exception occurred during file write.  Aborting.", e);
          mException = e;
        }
      }

      request.performRequestEnd();
      success = success && mException == null && getInternalState() == INTERNAL_STATE_IN_PROGRESS;

      if (success) {
        tryChangeState(INTERNAL_STATE_SUCCESS, false);
        return;
      } else {
        File outputFile = new File(mDestinationFile.getPath());
        if (outputFile.exists()) {
          mResumeOffset = outputFile.length();
        } else {
          mResumeOffset = 0; // start over.
        }
        if (getInternalState() == INTERNAL_STATE_PAUSING) {
          tryChangeState(INTERNAL_STATE_PAUSED, false);
          return;
        } else if (getInternalState() == INTERNAL_STATE_CANCELING) {
          if (!tryChangeState(INTERNAL_STATE_CANCELED, false)) {
            Log.w(TAG, "Unable to change download task to final state from " + getInternalState());
          }
          return;
        }
      }
    } while (mBytesDownloaded > 0);

    tryChangeState(INTERNAL_STATE_FAILURE, false);
  }

  @Override
  protected void onCanceled() {
    mSender.cancel();
    mException = StorageException.fromErrorStatus(Status.RESULT_CANCELED);
  }

  private boolean isValidHttpResponseCode(int code) {
    return code == 308 || (code >= 200 && code < 300);
  }

  /** Encapsulates state about the running {@link FileDownloadTask} */
  @SuppressWarnings("unused")
  public class TaskSnapshot extends StorageTask<FileDownloadTask.TaskSnapshot>.SnapshotBase {
    private final long mBytesDownloaded;

    /*package*/ TaskSnapshot(Exception error, long bytesDownloaded) {
      super(error);
      mBytesDownloaded = bytesDownloaded;
    }

    /** @return the total bytes downloaded so far. */
    public long getBytesTransferred() {
      return mBytesDownloaded;
    }

    /** @return the total bytes to upload.. */
    public long getTotalByteCount() {
      return FileDownloadTask.this.getTotalBytes();
    }
  }
}
