// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader.internal;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import android.util.LongSparseArray;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls the Android Download service to copy the model file to device (temp location) and then
 * moves file to it's permanent location, updating the model details in shared preferences
 * throughout.
 *
 * @hide
 */
public class ModelFileDownloadService {

  private final DownloadManager downloadManager;
  private final Context context;
  private final ModelFileManager fileManager;
  private final SharedPreferencesUtil sharedPreferencesUtil;

  @GuardedBy("this")
  // Mapping from download id to broadcast receiver. Because models can update, we cannot just keep
  // one instance of DownloadBroadcastReceiver per RemoteModelDownloadManager object.
  private final LongSparseArray<DownloadBroadcastReceiver> receiverMaps = new LongSparseArray<>();

  @GuardedBy("this")
  // Mapping from download id to TaskCompletionSource. Because models can update, we cannot just
  // keep one instance of TaskCompletionSource per RemoteModelDownloadManager object.
  private final LongSparseArray<TaskCompletionSource<Void>> taskCompletionSourceMaps =
      new LongSparseArray<>();

  private CustomModelDownloadConditions downloadConditions =
      new CustomModelDownloadConditions.Builder().build();

  public ModelFileDownloadService(@NonNull FirebaseApp firebaseApp) {
    this.context = firebaseApp.getApplicationContext();
    downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    this.fileManager = ModelFileManager.getInstance();
    this.sharedPreferencesUtil = new SharedPreferencesUtil(firebaseApp);
  }

  @VisibleForTesting
  ModelFileDownloadService(
      @NonNull FirebaseApp firebaseApp,
      DownloadManager downloadManager,
      ModelFileManager fileManager,
      SharedPreferencesUtil sharedPreferencesUtil) {
    this.context = firebaseApp.getApplicationContext();
    this.downloadManager = downloadManager;
    this.fileManager = fileManager;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
  }

  /**
   * Get ModelFileDownloadService instance using the firebase app returned by {@link
   * FirebaseApp#getInstance()}
   *
   * @return ModelFileDownloadService
   */
  @NonNull
  public static ModelFileDownloadService getInstance() {
    return FirebaseApp.getInstance().get(ModelFileDownloadService.class);
  }

  public Task<Void> download(
      CustomModel customModel, CustomModelDownloadConditions downloadConditions) {
    this.downloadConditions = downloadConditions;
    // todo add url tests here
    return ensureModelDownloaded(customModel);
  }

  @VisibleForTesting
  Task<Void> ensureModelDownloaded(CustomModel customModel) {
    // todo check model not already in progress of being downloaded

    // todo remove any failed download attempts

    // schedule new download of model file
    Long newDownloadId = scheduleModelDownload(customModel);
    if (newDownloadId == null) {
      return Tasks.forException(new Exception("Failed to schedule the download task"));
    }

    return registerReceiverForDownloadId(newDownloadId);
  }

  private synchronized DownloadBroadcastReceiver getReceiverInstance(long downloadId) {
    DownloadBroadcastReceiver receiver = receiverMaps.get(downloadId);
    if (receiver == null) {
      receiver =
          new DownloadBroadcastReceiver(downloadId, getTaskCompletionSourceInstance(downloadId));
      receiverMaps.put(downloadId, receiver);
    }
    return receiver;
  }

  private Task<Void> registerReceiverForDownloadId(long downloadId) {
    BroadcastReceiver broadcastReceiver = getReceiverInstance(downloadId);
    // It is okay to always register here. Since the broadcast receiver is the same via the lookup
    // for the same download id, the same broadcast receiver will be notified only once.
    context.registerReceiver(
        broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

    return getTaskCompletionSourceInstance(downloadId).getTask();
  }

  @VisibleForTesting
  synchronized TaskCompletionSource<Void> getTaskCompletionSourceInstance(long downloadId) {
    TaskCompletionSource<Void> taskCompletionSource = taskCompletionSourceMaps.get(downloadId);
    if (taskCompletionSource == null) {
      taskCompletionSource = new TaskCompletionSource<>();
      taskCompletionSourceMaps.put(downloadId, taskCompletionSource);
    }

    return taskCompletionSource;
  }

  @VisibleForTesting
  synchronized Long scheduleModelDownload(@NonNull CustomModel customModel) {
    if (downloadManager == null) {
      return null;
    }

    if (customModel.getDownloadUrl() == null || customModel.getDownloadUrl().isEmpty()) {
      return null;
    }
    // todo handle expired url here and figure out what to do about delayed downloads too..

    // Schedule a new downloading
    Request downloadRequest = new Request(Uri.parse(customModel.getDownloadUrl()));
    // check Url is not expired - get new one if necessary...

    // By setting the destination uri to null, the downloaded file will be stored in
    // DownloadManager's purgeable cache. As a result, WRITE_EXTERNAL_STORAGE permission is not
    // needed.
    downloadRequest.setDestinationUri(null);
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      downloadRequest.setRequiresCharging(downloadConditions.isChargingRequired());
      downloadRequest.setRequiresDeviceIdle(downloadConditions.isDeviceIdleRequired());
    }

    if (downloadConditions.isWifiRequired()) {
      downloadRequest.setAllowedNetworkTypes(Request.NETWORK_WIFI);
    }

    long id = downloadManager.enqueue(downloadRequest);
    // update the custom model to store the download id - do not lose current local file - in case
    // this is a background update.
    sharedPreferencesUtil.setDownloadingCustomModelDetails(
        new CustomModel(
            customModel.getName(),
            customModel.getModelHash(),
            customModel.getSize(),
            id,
            customModel.getLocalFilePath()));
    return id;
  }

  @Nullable
  @VisibleForTesting
  synchronized Integer getDownloadingModelStatusCode(Long downloadingId) {
    if (downloadManager == null || downloadingId == null) {
      return null;
    }

    Integer statusCode = null;

    try (Cursor cursor = downloadManager.query(new Query().setFilterById(downloadingId))) {

      if (cursor != null && cursor.moveToFirst()) {
        statusCode = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
      }

      if (statusCode == null) {
        return null;
      }

      if (statusCode != DownloadManager.STATUS_RUNNING
          && statusCode != DownloadManager.STATUS_PAUSED
          && statusCode != DownloadManager.STATUS_PENDING
          && statusCode != DownloadManager.STATUS_SUCCESSFUL
          && statusCode != DownloadManager.STATUS_FAILED) {
        // Unknown status
        statusCode = null;
      }
      return statusCode;
    }
  }

  @Nullable
  private synchronized ParcelFileDescriptor getDownloadedFile(Long downloadingId) {
    if (downloadManager == null || downloadingId == null) {
      return null;
    }

    ParcelFileDescriptor fileDescriptor = null;
    try {
      fileDescriptor = downloadManager.openDownloadedFile(downloadingId);
    } catch (FileNotFoundException e) {
      System.out.println("Downloaded file is not found");
    }
    return fileDescriptor;
  }

  public void maybeCheckDownloadingComplete() throws Exception {
    for (String key : sharedPreferencesUtil.getSharedPreferenceKeySet()) {
      // if a local file path is present - get model details.
      Matcher matcher =
          Pattern.compile(SharedPreferencesUtil.DOWNLOADING_MODEL_ID_MATCHER).matcher(key);
      if (matcher.find()) {
        String modelName = matcher.group(matcher.groupCount());
        CustomModel downloadingModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
        Integer statusCode = getDownloadingModelStatusCode(downloadingModel.getDownloadId());
        if (statusCode == DownloadManager.STATUS_SUCCESSFUL
            || statusCode == DownloadManager.STATUS_FAILED) {
          loadNewlyDownloadedModelFile(downloadingModel);
        }
      }
    }
  }

  @Nullable
  @WorkerThread
  public File loadNewlyDownloadedModelFile(CustomModel model) throws Exception {
    Long downloadingId = model.getDownloadId();
    String downloadingModelHash = model.getModelHash();

    if (downloadingId == null || downloadingModelHash == null) {
      // no downloading model file or incomplete info.
      return null;
    }

    Integer statusCode = getDownloadingModelStatusCode(downloadingId);
    if (statusCode == null) {
      return null;
    }

    if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
      // Get downloaded file.
      ParcelFileDescriptor fileDescriptor = getDownloadedFile(downloadingId);
      if (fileDescriptor == null) {
        // reset original model - removing download id.
        sharedPreferencesUtil.setFailedUploadedCustomModelDetails(model.getName());
        // todo call the download register?
        return null;
      }

      // Try to move it to destination folder.
      File newModelFile = fileManager.moveModelToDestinationFolder(model, fileDescriptor);

      if (newModelFile == null) {
        // reset original model - removing download id.
        // todo call the download register?
        sharedPreferencesUtil.setFailedUploadedCustomModelDetails(model.getName());
        return null;
      }

      // Successfully moved,  update share preferences
      sharedPreferencesUtil.setUploadedCustomModelDetails(
          new CustomModel(
              model.getName(), model.getModelHash(), model.getSize(), 0, newModelFile.getPath()));

      // todo(annzimmer) Cleans up the old files if it is the initial creation.
      return newModelFile;
    } else if (statusCode == DownloadManager.STATUS_FAILED) {
      // reset original model - removing download id.
      sharedPreferencesUtil.setFailedUploadedCustomModelDetails(model.getName());
      // todo - determine if the temp files need to be clean up? Does one exist?
    }
    // Other cases, return as null and wait for download finish.
    return null;
  }

  // This class runs totally on worker thread because we registered the receiver with a worker
  // thread handler.
  @WorkerThread
  private class DownloadBroadcastReceiver extends BroadcastReceiver {

    // Download Id is captured inside this class in memory. So there is no concern of inconsistency
    // with the persisted download id in shared preferences.
    private final long downloadId;
    private final TaskCompletionSource<Void> taskCompletionSource;

    private DownloadBroadcastReceiver(
        long downloadId, TaskCompletionSource<Void> taskCompletionSource) {
      this.downloadId = downloadId;
      this.taskCompletionSource = taskCompletionSource;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
      if (id != downloadId) {
        return;
      }

      Integer statusCode = getDownloadingModelStatusCode(downloadId);
      synchronized (ModelFileDownloadService.this) {
        try {
          context.getApplicationContext().unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
          // If we try to unregister a receiver that was never registered or has been unregistered,
          // IllegalArgumentException will be thrown by the Android Framework.
          // Our current code does not have this problem. However, in order to be safer in the
          // future, we just ignore the exception here, because it is not a big deal. The code can
          // move on.
        }

        receiverMaps.remove(downloadId);
        taskCompletionSourceMaps.remove(downloadId);
      }

      if (statusCode != null) {
        if (statusCode == DownloadManager.STATUS_FAILED) {
          // todo add failure reason and logging
          System.out.println("Download Failed for id: " + id);
          taskCompletionSource.setException(new Exception("Failed"));
          return;
        }

        if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
          System.out.println("Download Succeeded for id: " + id);
          taskCompletionSource.setResult(null);
          return;
        }
      }

      // Status code is null or not one of success or fail.
      taskCompletionSource.setException(new Exception("Model downloading failed"));
    }
  }
}
