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
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.gms.common.util.IOUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.concurrent.Semaphore;
import junit.framework.Assert;

/** Test for downloads. */
@SuppressWarnings("unused")
public class TestDownloadHelper {
  private static final String TAG = "TestDownloadHelper";
  public static Bitmap mIcon;
  public static byte[] mBytes;

  public static class StreamDownloadResponse {
    public StringBuilder mainTask = new StringBuilder();
    public StringBuilder backgroundTask = new StringBuilder();
  }

  public static Task<StreamDownloadResponse> streamDownload(
      @Nullable final ProcessImage imageCallback,
      @Nullable final ProcessBytes byteCallback,
      String filename,
      final int cancelAfter)
      throws InterruptedException {

    StreamDownloadResponse response = new StreamDownloadResponse();

    StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/" + filename);

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
                  mBytes = IOUtils.toByteArray(stream);
                  mIcon = BitmapFactory.decodeByteArray(mBytes, 0, mBytes.length);
                } catch (OutOfMemoryError e) {
                  Log.w(TAG, "Can't persist download due to low memory", e);
                }

                while (stream.read() != -1) {
                  // do nothing
                }
                Assert.assertEquals(totalByteCountBeginning, state.getTotalByteCount());
                Assert.assertEquals(mBytes.length, state.getTotalByteCount());
              } finally {
                // Closing stream
                stream.close();
              }
            });

    try {
      task.pause();
      Assert.fail();
    } catch (UnsupportedOperationException ignore) {
      // Expected.
    }

    try {
      task.resume();
      Assert.fail();
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
                imageCallback.run(mIcon);
              }
              if (byteCallback != null) {
                byteCallback.run(mBytes);
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
              Assert.assertEquals(
                  completedTask.getResult().getTotalByteCount(),
                  completedTask.getResult().getBytesTransferred());
            });

    ControllableSchedulerHelper.getInstance().resume();

    return task.continueWithTask(
        continuedTask -> {
          TaskCompletionSource<StreamDownloadResponse> source = new TaskCompletionSource<>();
          source.setResult(response);
          return source.getTask();
        });
  }

  public static Semaphore byteDownload(final StringBuilder builder, final ProcessBytes callback)
      throws InterruptedException {
    StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/image.jpg");

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
      final Uri destinationUri, final Runnable callback, final int cancelAfter)
      throws InterruptedException {
    final StringBuilder builder = new StringBuilder();

    StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/image.jpg");

    ControllableSchedulerHelper.getInstance().pause();

    Assert.assertTrue(storage.getActiveDownloadTasks().isEmpty());
    Assert.assertTrue(
        StorageTaskManager.getInstance().getDownloadTasksUnder(storage.getParent()).isEmpty());
    final FileDownloadTask task = storage.getFile(destinationUri);
    Assert.assertEquals(1, storage.getActiveDownloadTasks().size());
    Assert.assertFalse(
        StorageTaskManager.getInstance().getDownloadTasksUnder(storage.getParent()).isEmpty());

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
              String statusMessage = "\nonFailure:\n" + e.toString();
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

  static String fileTaskToString(FileDownloadTask.TaskSnapshot state) {
    String exceptionMessage = state.getError() != null ? state.getError().getMessage() : "<none>";
    String bytesDownloaded = Long.toString(state.getBytesTransferred());

    return "  exceptionMessage:" + exceptionMessage + "\n  bytesDownloaded:" + bytesDownloaded;
  }

  static String streamTaskToString(StreamDownloadTask.TaskSnapshot state) {
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
