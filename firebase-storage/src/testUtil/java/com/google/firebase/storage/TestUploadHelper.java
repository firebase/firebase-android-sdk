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

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.UploadTask.TaskSnapshot;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** tests for uploads. */
@SuppressWarnings("unused")
public class TestUploadHelper {
  private static final String TAG = "TestDownloadHelper";

  static int progressCount = 0;
  static boolean hitPause;
  static UploadTask inProgressTask;

  /** Attach the provided listeners or default ones if null. */
  private static void attachListeners(
      final StringBuilder builder,
      final UploadTask task,
      @Nullable OnSuccessListener<TaskSnapshot> onSuccess,
      @Nullable OnFailureListener onFailure,
      @Nullable OnCanceledListener onCanceled,
      @Nullable OnPausedListener<TaskSnapshot> onPaused,
      @Nullable OnProgressListener<TaskSnapshot> onProgress,
      @Nullable OnCompleteListener<TaskSnapshot> onComplete) {
    task.addOnSuccessListener(
        onSuccess != null
            ? onSuccess
            : new OnSuccessListener<TaskSnapshot>() {
              @Override
              public void onSuccess(TaskSnapshot state) {
                ControllableSchedulerHelper.getInstance().verifyCallbackThread();
                String statusMessage = "\nonSuccess:\n" + uploadTaskStatetoString(state);
                Log.i(TAG, statusMessage);
                builder.append(statusMessage);
                task.removeOnSuccessListener(this);
              }
            });

    task.addOnFailureListener(
        onFailure != null
            ? onFailure
            : (OnFailureListener)
                e -> {
                  ControllableSchedulerHelper.getInstance().verifyCallbackThread();
                  String statusMessage = "\nonFailure:\n" + e;
                  Log.i(TAG, statusMessage);
                  builder.append(statusMessage);
                });

    task.addOnCanceledListener(
        onCanceled != null
            ? onCanceled
            : (OnCanceledListener)
                () -> {
                  ControllableSchedulerHelper.getInstance().verifyCallbackThread();
                  String statusMessage = "\nonCanceled:";
                  Log.i(TAG, statusMessage);
                  builder.append(statusMessage);
                });

    task.addOnPausedListener(
        onPaused != null
            ? onPaused
            : (OnPausedListener<TaskSnapshot>)
                result -> {
                  ControllableSchedulerHelper.getInstance().verifyCallbackThread();
                  String statusMessage = "\nonPaused:\n" + uploadTaskStatetoString(result);
                  Log.i(TAG, statusMessage);
                  builder.append(statusMessage);
                });

    task.addOnProgressListener(
        onProgress != null
            ? onProgress
            : (OnProgressListener<TaskSnapshot>)
                result -> {
                  ControllableSchedulerHelper.getInstance().verifyCallbackThread();
                  String statusMessage = "\nonProgress:\n" + uploadTaskStatetoString(result);
                  Log.i(TAG, statusMessage);
                  builder.append(statusMessage);
                });

    task.addOnCompleteListener(
        onComplete != null
            ? onComplete
            : (OnCompleteListener<TaskSnapshot>)
                completedTask -> {
                  ControllableSchedulerHelper.getInstance().verifyCallbackThread();
                  String statusMessage = "\nonComplete:Success=\n" + completedTask.isSuccessful();
                  Log.i(TAG, statusMessage);
                  builder.append(statusMessage);
                });

    task.onSuccessTask(
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonSuccessTask:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          return Tasks.forResult(null);
        });
  }

  public static Task<StringBuilder> byteUpload(StorageReference storage) {
    final StringBuilder builder = new StringBuilder();
    String foo = "This is a test!!!";
    byte[] bytes = foo.getBytes(Charset.forName("UTF-8"));
    StorageMetadata metadata = new StorageMetadata.Builder().setContentType("text/plain").build();

    ControllableSchedulerHelper.getInstance().pause();

    final UploadTask task = storage.putBytes(bytes, metadata);
    attachListeners(
        builder,
        task,
        new OnSuccessListener<UploadTask.TaskSnapshot>() {
          @Override
          public void onSuccess(UploadTask.TaskSnapshot state) {
            ControllableSchedulerHelper.getInstance().verifyCallbackThread();
            String statusMessage = "\nonSuccess:\n" + uploadTaskStatetoString(state) + "\n";
            Log.i(TAG, statusMessage);
            builder.append(statusMessage);
            TestCommandHelper.dumpMetadata(builder, state.getMetadata());
            task.removeOnSuccessListener(this);
          }
        },
        null,
        null,
        null,
        null,
        null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWithTask(
        continuedTask -> {
          TaskCompletionSource<StringBuilder> downloadResult = new TaskCompletionSource<>();
          storage
              .getDownloadUrl()
              .addOnSuccessListener(
                  uri ->
                      FirebaseStorage.getInstance()
                          .getReferenceFromUrl(uri.toString())
                          .getBytes(Integer.MAX_VALUE)
                          .addOnSuccessListener(
                              bytes1 -> {
                                Preconditions.checkState(
                                    new String(bytes1).equals(foo),
                                    "Downloaded bytes do not match uploaded bytes");
                                downloadResult.setResult(builder);
                              }));

          return downloadResult.getTask();
        });
  }

  public static Task<StringBuilder> smallTextUpload() {
    final StringBuilder builder = new StringBuilder();
    StorageReference storage = FirebaseStorage.getInstance().getReference("flubbertest.txt");
    String foo = "This is a test!!!";
    byte[] bytes = foo.getBytes(Charset.forName("UTF-8"));
    StorageMetadata metadata = new StorageMetadata.Builder().setContentType("text/plain").build();

    ControllableSchedulerHelper.getInstance().pause();

    verifyTaskCount(storage, 0);
    final UploadTask task = storage.putBytes(bytes, metadata);
    verifyTaskCount(storage, 1);

    attachListeners(
        builder,
        task,
        new OnSuccessListener<UploadTask.TaskSnapshot>() {
          @Override
          public void onSuccess(UploadTask.TaskSnapshot state) {
            ControllableSchedulerHelper.getInstance().verifyCallbackThread();
            String statusMessage = "\nonSuccess:\n" + uploadTaskStatetoString(state) + "\n";
            Log.i(TAG, statusMessage);
            builder.append(statusMessage);
            TestCommandHelper.dumpMetadata(builder, state.getMetadata());
            task.removeOnSuccessListener(this);
          }
        },
        null,
        null,
        null,
        null,
        null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWithTask(
        continuedTask -> {
          TaskCompletionSource<StringBuilder> source = new TaskCompletionSource<>();
          source.setResult(builder);
          return source.getTask();
        });
  }

  public static Task<StringBuilder> smallTextUpload2() {
    final StringBuilder builder = new StringBuilder();
    StorageReference storage = FirebaseStorage.getInstance().getReference("flubbertest.txt");
    String foo = "This is a test!!!";
    byte[] bytes = foo.getBytes(Charset.forName("UTF-8"));
    StorageMetadata metadata =
        new StorageMetadata.Builder()
            .setContentType("text/plain")
            .setCustomMetadata("myData", "myFoo")
            .build();

    ControllableSchedulerHelper.getInstance().pause();
    final UploadTask task = storage.putStream(new ByteArrayInputStream(bytes), metadata);
    attachListeners(
        builder,
        task,
        new OnSuccessListener<UploadTask.TaskSnapshot>() {
          @Override
          public void onSuccess(UploadTask.TaskSnapshot state) {
            ControllableSchedulerHelper.getInstance().verifyCallbackThread();
            String statusMessage = "\nonSuccess:\n" + uploadTaskStatetoString(state) + "\n";
            Log.i(TAG, statusMessage);
            builder.append(statusMessage);
            TestCommandHelper.dumpMetadata(builder, state.getMetadata());
            task.removeOnSuccessListener(this);
          }
        },
        null,
        null,
        null,
        null,
        null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWith(ignored -> builder);
  }

  public static Task<StringBuilder> fileUpload(final Uri sourcefile, final String filename) {
    final StringBuilder builder = new StringBuilder();

    return fileUploadImpl(builder, sourcefile, filename);
  }

  private static Task<StringBuilder> fileUploadImpl(
      final StringBuilder builder, final Uri sourcefile, String destinationName) {
    StorageReference storage = FirebaseStorage.getInstance().getReference(destinationName);
    StorageMetadata metadata =
        new StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("myData", "myFoo")
            .build();

    ControllableSchedulerHelper.getInstance().pause();

    verifyTaskCount(storage, 0);
    final UploadTask task = storage.putFile(sourcefile, metadata);
    verifyTaskCount(storage, 1);

    attachListeners(builder, task, null, null, null, null, null, null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWith(ignored -> builder);
  }

  public static Task<StringBuilder> streamUploadWithInterruptions() {
    /**
     * Stream that has more data than advertised through its available()/read() method. Only -1
     * indicates end of stream.
     */
    class WonkyStream extends InputStream {
      private final ArrayList<byte[]> streamData = new ArrayList<>();

      private WonkyStream() {
        streamData.add(new byte[] {0, 1, 2});
        streamData.add(new byte[] {3, 4});
        streamData.add(new byte[] {5, 6, 7, 8});
      }

      @Override
      public int read() {
        if (streamData.isEmpty()) {
          return -1;
        } else {
          int data = streamData.get(0)[0];
          removeData(1);
          return data;
        }
      }

      @Override
      public int read(byte[] b, int off, int len) {
        if (streamData.isEmpty()) {
          return -1;
        } else {
          int length = Math.min(len - off, streamData.get(0).length);
          System.arraycopy(streamData.get(0), 0, b, off, length);
          removeData(length);
          return length;
        }
      }

      private void removeData(int removeFirst) {
        if (streamData.get(0).length == removeFirst) {
          streamData.remove(0);
        } else {
          streamData.set(
              0,
              Arrays.copyOfRange(
                  streamData.get(0), removeFirst, streamData.get(0).length - removeFirst));
        }
      }

      @Override
      public int available() {
        if (streamData.isEmpty()) {
          return -1;
        } else {
          return streamData.get(0).length;
        }
      }

      boolean isEmpty() {
        return streamData.isEmpty();
      }
    }

    final StringBuilder builder = new StringBuilder();
    final WonkyStream wonkyStream = new WonkyStream();
    StorageReference storage = FirebaseStorage.getInstance().getReference("testdata.dat");

    ControllableSchedulerHelper.getInstance().pause();
    final UploadTask task = storage.putStream(wonkyStream);
    attachListeners(builder, task, null, null, null, null, null, null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWith(ignored -> builder);
  }

  public static Task<StringBuilder> fileUploadWithPauseCancel(
      final Semaphore semaphore, final Uri sourcefile) {
    final StringBuilder builder = new StringBuilder();
    final StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");
    StorageMetadata metadata =
        new StorageMetadata.Builder()
            .setContentType("text/plain")
            .setCustomMetadata("myData", "myFoo")
            .build();

    ControllableSchedulerHelper.getInstance().pause();

    progressCount = 0;
    hitPause = false;

    final UploadTask task = storage.putFile(sourcefile, metadata);
    attachListeners(
        builder,
        task,
        null,
        null,
        null,
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonPaused:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          hitPause = true;
          task.resume();
          if (semaphore != null) {
            semaphore.release();
          }
        },
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          if (hitPause || progressCount < 3) {
            String statusMessage = "\nonProgress:\n" + uploadTaskStatetoString(result);
            Log.i(TAG, statusMessage);
            builder.append(statusMessage);
            progressCount++;
            if (progressCount == 3) {
              task.pause();
            }
            if (hitPause) {
              hitPause = false;
              task.cancel();
            }
          }
        },
        null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWith(ignored -> builder);
  }

  public static Task<StringBuilder> byteUploadCancel() {
    final StringBuilder builder = new StringBuilder();
    final StorageReference storage = FirebaseStorage.getInstance().getReference("foo.txt");

    ControllableSchedulerHelper.getInstance().pause();

    AtomicBoolean taskCancelled = new AtomicBoolean();

    final UploadTask task = storage.putBytes(new byte[] {1, 2, 3});

    progressCount = 0;

    task.addOnProgressListener(
        (result) -> {
          ++progressCount;

          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonProgress:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          if (progressCount == 2) {
            task.cancel();
          }
        });

    task.addOnCanceledListener(
        () -> {
          String statusMessage = "\nonCanceled:\n";
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
        });

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWith(
        ignored -> {
          System.out.println(builder);
          return builder;
        });
  }

  public static Task<StringBuilder> fileUploadWithPauseResume(
      final Semaphore semaphore, final Uri sourcefile) {
    final StringBuilder builder = new StringBuilder();
    final StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");
    StorageMetadata metadata =
        new StorageMetadata.Builder()
            .setContentType("text/plain")
            .setCustomMetadata("myData", "myFoo")
            .build();

    ControllableSchedulerHelper.getInstance().pause();

    progressCount = 0;
    final UploadTask task = storage.putFile(sourcefile, metadata);
    attachListeners(
        builder,
        task,
        null,
        null,
        null,
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonPaused:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          task.resume();
          if (semaphore != null) {
            semaphore.release();
          }
        },
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonProgress:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          progressCount++;
          if (progressCount == 3) {
            task.pause();
          }
        },
        null);

    ControllableSchedulerHelper.getInstance().resume();
    return task.continueWith(ignored -> builder);
  }

  public static Task<Void> fileUploadQueuedCancel(
      final StringBuilder builder, final Uri sourcefile) {
    TaskCompletionSource<Void> result = new TaskCompletionSource<>();

    final StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");
    StorageMetadata metadata =
        new StorageMetadata.Builder()
            .setContentType("text/plain")
            .setCustomMetadata("myData", "myFoo")
            .build();

    ControllableSchedulerHelper.getInstance().pause();
    final UploadTask task = storage.putFile(sourcefile, metadata);
    final Semaphore semaphore = new Semaphore(0);
    attachListeners(
        builder,
        task,
        null,
        null,
        null,
        null,
        null,
        completedTask -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonComplete:Success=\n" + completedTask.isSuccessful();
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          result.setResult(null);
        });

    // cancel while the task is still queued.
    task.cancel();

    ControllableSchedulerHelper.getInstance().resume();

    return result.getTask();
  }

  public static Task<StringBuilder> adaptiveChunking() {
    final StringBuilder builder = new StringBuilder();
    final StorageReference storage = FirebaseStorage.getInstance().getReference("adaptive.dat");
    final byte[] data = new byte[2 * 1024 * 1024];

    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) i;
    }

    final ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

    // This test will upload 2 MB of data:
    //  - it will read and upload one chunk of 256KB (258KB read, 258KB uploaded)
    //  - it will read and upload one chunk of 512KB (768KB read, 768KB uploaded)
    //  - it will read and fail the upload one chunk of 1MB (1.75MB read, 768KB uploaded)
    //  - it will upload 256KB from its local cache (1.75MB read, 1MB uploaded)
    //  - it will upload 512KB from its local cache (1.75MB read, 1.5MB uploaded)
    //  - it will try to read 1MB (256KB from cache, 256KB from the stream and -1 from the stream)
    //    and upload the last chunk of 512KB (2MB read, 2MB uploaded)
    final ArrayList<Integer> expectedReadSize = new ArrayList<>();
    expectedReadSize.add(256 * 1024);
    expectedReadSize.add(512 * 1024);
    expectedReadSize.add(1024 * 1024);
    expectedReadSize.add(768 * 1024);
    expectedReadSize.add(512 * 1024);

    ControllableSchedulerHelper.getInstance().pause();

    final UploadTask task =
        storage.putStream(
            new InputStream() {
              @Override
              public int read() {
                return inputStream.read();
              }

              @Override
              public int read(byte[] buffer, int offset, int length) {
                int expectedRead = expectedReadSize.remove(0);
                Preconditions.checkState(
                    expectedRead == length,
                    "Expected to be reading %s bytes, but only %s were requested",
                    expectedRead,
                    length);
                return inputStream.read(buffer, offset, length);
              }
            });

    attachListeners(
        builder,
        task,
        null,
        null,
        null,
        null,
        null,
        completedTask -> {
          String statusMessage = "\nonComplete:Success=\n" + completedTask.isSuccessful();
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          Preconditions.checkState(
              expectedReadSize.isEmpty(),
              "Expected to have no remaining reads, but found %s",
              expectedReadSize.size());
        });

    ControllableSchedulerHelper.getInstance().resume();

    return task.continueWith(ignored -> builder);
  }

  public static Task<StringBuilder> fileUploadWithPauseActSub(
      Activity activity, final Uri sourcefile) {
    final StringBuilder builder = new StringBuilder();
    final StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");
    StorageMetadata metadata =
        new StorageMetadata.Builder()
            .setContentType("text/plain")
            .setCustomMetadata("myData", "myFoo")
            .build();

    ControllableSchedulerHelper.getInstance().pause();
    inProgressTask = storage.putFile(sourcefile, metadata);

    progressCount = 0;
    hitPause = false;

    attachListeners(
        builder,
        inProgressTask,
        state -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          inProgressTask = null;
          String statusMessage = "\nonSuccess:\n" + uploadTaskStatetoString(state);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
        },
        e -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          inProgressTask = null;
          String statusMessage = "\nonFailure:\n" + e;
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
        },
        null,
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonPaused:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          hitPause = true;
        },
        result -> {
          ControllableSchedulerHelper.getInstance().verifyCallbackThread();
          String statusMessage = "\nonProgress:\n" + uploadTaskStatetoString(result);
          Log.i(TAG, statusMessage);
          builder.append(statusMessage);
          progressCount++;
          if (progressCount == 2) {
            inProgressTask.pause();
          }
          if (hitPause) {
            result.getTask().cancel();
          }
        },
        null);

    ControllableSchedulerHelper.getInstance().resume();
    return inProgressTask.continueWith(task -> builder);
  }

  public static void cancelInProgressTask() {
    if (inProgressTask != null) {
      inProgressTask.resume();
    }
  }

  private static void verifyTaskCount(StorageReference reference, int expectedTasks) {
    List<UploadTask> globalUploadTasks = reference.getActiveUploadTasks();
    Preconditions.checkState(
        globalUploadTasks.size() == expectedTasks,
        "Expected active upload task to contain %s item(s), but contained %s item(s)",
        globalUploadTasks.size());
    List<UploadTask> uploadTasksAtParent =
        StorageTaskManager.getInstance().getUploadTasksUnder(reference.getParent());
    Preconditions.checkState(
        uploadTasksAtParent.size() == expectedTasks,
        "Expected active upload task at location %s to contain %s item(s), "
            + "but contained %s item(s)",
        reference.getParent(),
        uploadTasksAtParent.size());
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private static String uploadTaskStatetoString(UploadTask.TaskSnapshot task) {
    String exceptionMessage = task.getError() != null ? task.getError().toString() : "<none>";
    String targetStorageString = task.getStorage().toString();
    String bytesUploaded = Long.toString(task.getBytesTransferred());
    int currentState = task.getTask().getInternalState();
    String uploadUri =
        task.getUploadSessionUri() != null ? task.getUploadSessionUri().toString() : "<none>";
    return "  exceptionMessage:"
        + exceptionMessage
        + "\n  targetStorageString:"
        + targetStorageString
        + "\n  bytesUploaded:"
        + bytesUploaded
        + "\n  currentState:"
        + currentState
        + "\n  uploadUri:"
        + uploadUri
        + "\n  total bytes:"
        + task.getTotalByteCount();
  }
}
