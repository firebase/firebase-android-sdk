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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.IOUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.List;
import java.util.concurrent.Semaphore;

/** Test for downloads. */
@SuppressWarnings("unused")
public class TestDownloadHelper {
  private static final String TAG = "TestDownloadHelper";
  private static Bitmap icon;
  private static byte[] bytes;

  public static class StreamDownloadResponse {
    public StringBuilder mainTask = new StringBuilder();
    public StringBuilder backgroundTask = new StringBuilder();
  }

  public static Task<StreamDownloadResponse> streamDownload(
      @Nullable final ProcessImage imageCallback,
      @Nullable final ProcessBytes byteCallback,
      String filename,
      final int cancelAfter) {

    StreamDownloadResponse response = new StreamDownloadResponse();

    StorageReference storage = FirebaseStorage.getInstance().getReference(filename);

    ControllableSchedulerHelper.getInstance().pause();
    final StreamDownloadTask task =
        storage.getStream(
            (state, stream) -> {
              try {
                long totalByteCountBeginning = state.getTotalByteCount();
                String statusMessage = "\ndoInBackground:\n" + streamTaskToString(state);
                Log.i(TAG, statusMessage);
                response.backgroundTask.append(statusMessage);

                try {
                  bytes = IOUtils.toByteArray(stream);
                  icon = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } catch (OutOfMemoryError e) {
                  Log.w(TAG, "Can't persist download due to low memory", e);
                }

                while (stream.read() != -1) {
                  // do nothing
                }

                if (state.getTotalByteCount() != -1) {
                  Preconditions.checkState(totalByteCountBeginning == state.getTotalByteCount());
                  Preconditions.checkState(bytes.length == state.getTotalByteCount());
                }
              } finally {
                // Closing stream
                stream.close();
              }
            });

    try {
      task.pause();
      throw new RuntimeException(
          "StreamDownload.pause() did not throw UnsupportedOperationException");
    } catch (UnsupportedOperationException ignore) {
      // Expected.
    }

    try {
      task.resume();
      throw new RuntimeException(
          "StreamDownload.resume() did not throw UnsupportedOperationException");
    } catch (UnsupportedOperationException ignore) {
      // Expected.
    }

    if (cancelAfter == 0) {
      task.cancel();
    }

    task.addOnSuccessListener(
            state -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonSuccess:\n" + streamTaskToString(state);
              Log.i(TAG, statusMessage);
              response.mainTask.append(statusMessage);
              if (imageCallback != null) {
                imageCallback.run(icon);
              }
              if (byteCallback != null) {
                byteCallback.run(bytes);
              }
            })
        .addOnFailureListener(
            e -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonFailure:\n" + e;
              Log.i(TAG, statusMessage);
              response.mainTask.append(statusMessage);
            })
        .addOnProgressListener(
            state -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonProgressUpdate:\n" + streamTaskToString(state);
              Log.i(TAG, statusMessage);
              response.mainTask.append(statusMessage);
              if (cancelAfter != -1 && state.getBytesTransferred() >= cancelAfter) {
                task.cancel();
              }
            })
        .addOnCanceledListener(
            () -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonCanceled:";
              Log.i(TAG, statusMessage);
              response.mainTask.append(statusMessage);
            })
        .addOnCompleteListener(
            completedTask -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonComplete:Success=\n" + completedTask.isSuccessful();
              Log.i(TAG, statusMessage);
              response.mainTask.append(statusMessage);
              long expectedTotalByteCount = completedTask.getResult().getTotalByteCount();
              if (expectedTotalByteCount != -1) {
                long bytesTransferred = completedTask.getResult().getBytesTransferred();
                Preconditions.checkState(
                    expectedTotalByteCount == bytesTransferred,
                    "Expected transfer of %s bytes, but only received %s",
                    expectedTotalByteCount,
                    bytesTransferred);
              }
            });

    ControllableSchedulerHelper.getInstance().resume();

    return task.continueWithTask(
        continuedTask -> {
          TaskCompletionSource<StreamDownloadResponse> source = new TaskCompletionSource<>();
          source.setResult(response);
          return source.getTask();
        });
  }

  public static Semaphore byteDownload(final StringBuilder builder, final ProcessBytes callback) {
    StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");

    ControllableSchedulerHelper.getInstance().pause();
    final Semaphore semaphore = new Semaphore(0);

    Task<byte[]> result = storage.getBytes(5000000);
    result.addOnSuccessListener(
        ExecutorProviderHelper.getInstance(),
        byteResult -> {
          String statusMessage = "\nonSuccess:\n";
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          if (callback != null) {
            callback.run(byteResult);
          }
          semaphore.release();
        });
    result.addOnFailureListener(
        ExecutorProviderHelper.getInstance(),
        e -> {
          String statusMessage = "\nonError:\n";
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          semaphore.release();
        });

    ControllableSchedulerHelper.getInstance().resume();
    return semaphore;
  }

  public static Task<StringBuilder> fileDownload(
      final Uri destinationUri, final Runnable callback, final int cancelAfter) {
    final StringBuilder builder = new StringBuilder();

    StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");

    ControllableSchedulerHelper.getInstance().pause();

    verifyTaskCount(storage, 0);
    final FileDownloadTask task = storage.getFile(destinationUri);
    verifyTaskCount(storage, 1);

    task.addOnSuccessListener(
            state -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonSuccess:\n" + fileTaskToString(state);
              Log.i(TAG, statusMessage);
              builder.append(statusMessage);
              if (callback != null) {
                callback.run();
              }
            })
        .addOnFailureListener(
            e -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonFailure:\n" + e;
              Log.i(TAG, statusMessage);
              builder.append(statusMessage);
            })
        .addOnCanceledListener(
            () -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonCanceled:";
              Log.i(TAG, statusMessage);
              builder.append(statusMessage);
            })
        .addOnProgressListener(
            state -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonProgressUpdate:\n" + fileTaskToString(state);
              Log.i(TAG, statusMessage);
              builder.append(statusMessage);

              if (cancelAfter != -1 && state.getBytesTransferred() > cancelAfter) {
                task.cancel();
              }
            })
        .addOnCompleteListener(
            completedTask -> {
              ControllableSchedulerHelper.getInstance().verifyCallbackThread();
              String statusMessage = "\nonComplete:Success=\n" + completedTask.isSuccessful();
              Log.i(TAG, statusMessage);
              builder.append(statusMessage);
            });

    if (cancelAfter == 0) {
      task.cancel();
    }

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWithTask(
        continuedTask -> {
          TaskCompletionSource<StringBuilder> source = new TaskCompletionSource<>();
          source.setResult(builder);
          return source.getTask();
        });
  }

  private static void verifyTaskCount(StorageReference reference, int expectedTasks) {
    List<FileDownloadTask> globalDownloadTasks = reference.getActiveDownloadTasks();
    Preconditions.checkState(
        globalDownloadTasks.size() == expectedTasks,
        "Expected active download task to contain %s item(s), but contained %s item(s)",
        globalDownloadTasks.size(),
        expectedTasks);
    List<FileDownloadTask> downloadTasksAtParent =
        StorageTaskManager.getInstance().getDownloadTasksUnder(reference.getParent());
    Preconditions.checkState(
        downloadTasksAtParent.size() == expectedTasks,
        "Expected active download task at location %s to contain %s item(s), "
            + "but contained %s item(s)",
        reference.getParent(),
        downloadTasksAtParent.size(),
        expectedTasks);
  }

  private static String fileTaskToString(FileDownloadTask.TaskSnapshot state) {
    String exceptionMessage = state.getError() != null ? state.getError().getMessage() : "<none>";
    String bytesDownloaded = Long.toString(state.getBytesTransferred());

    return "  exceptionMessage:" + exceptionMessage + "\n  bytesDownloaded:" + bytesDownloaded;
  }

  private static String streamTaskToString(StreamDownloadTask.TaskSnapshot state) {
    String exceptionMessage = state.getError() != null ? state.getError().getMessage() : "<none>";
    String bytesDownloaded = Long.toString(state.getBytesTransferred());

    return "  exceptionMessage:" + exceptionMessage + "\n  bytesDownloaded:" + bytesDownloaded;
  }

  /** for testing */
  public interface ProcessImage {
    void run(@Nullable Bitmap bitmap);
  }

  /** for testing */
  public interface ProcessBytes {
    void run(@Nullable byte[] bitmap);
  }
}
