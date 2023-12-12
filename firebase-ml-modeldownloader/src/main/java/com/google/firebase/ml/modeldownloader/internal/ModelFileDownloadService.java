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

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.LongSparseArray;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Calls the Android Download service to copy the model file to device (temp location) and then
 * moves file to it's permanent location, updating the model details in shared preferences
 * throughout.
 *
 * @hide
 */
public class ModelFileDownloadService {

  private static final String TAG = "ModelFileDownloadSer";
  private static final int COMPLETION_BUFFER_IN_MS = 60 * 5 * 1000;

  private final DownloadManager downloadManager;
  private final Context context;
  private final ModelFileManager fileManager;
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final FirebaseMlLogger eventLogger;
  private final CustomModel.Factory modelFactory;

  private boolean isInitialLoad;

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

  @Inject
  public ModelFileDownloadService(
      Context context,
      FirebaseMlLogger eventLogger,
      ModelFileManager modelFileManager,
      SharedPreferencesUtil sharedPreferencesUtil,
      CustomModel.Factory modelFactory) {
    this(
        context,
        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE),
        modelFileManager,
        sharedPreferencesUtil,
        eventLogger,
        true,
        modelFactory);
  }

  @VisibleForTesting
  ModelFileDownloadService(
      Context context,
      DownloadManager downloadManager,
      ModelFileManager fileManager,
      SharedPreferencesUtil sharedPreferencesUtil,
      FirebaseMlLogger eventLogger,
      boolean isInitialLoad,
      CustomModel.Factory modelFactory) {
    this.context = context;
    this.downloadManager = downloadManager;
    this.fileManager = fileManager;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.eventLogger = eventLogger;
    this.isInitialLoad = isInitialLoad;
    this.modelFactory = modelFactory;
  }

  public Task<Void> download(
      CustomModel customModel, CustomModelDownloadConditions downloadConditions) {
    this.downloadConditions = downloadConditions;
    return ensureModelDownloaded(customModel);
  }

  @VisibleForTesting
  Task<Void> ensureModelDownloaded(CustomModel customModel) {
    eventLogger.logDownloadEventWithErrorCode(
        customModel, false, DownloadStatus.EXPLICITLY_REQUESTED, ErrorCode.NO_ERROR);
    // check model download already in progress
    CustomModel downloadingModel =
        sharedPreferencesUtil.getDownloadingCustomModelDetails(customModel.getName());
    if (downloadingModel != null) {
      if (downloadingModel.getDownloadId() != 0
          && existTaskCompletionSourceInstance(downloadingModel.getDownloadId())) {
        Integer statusCode = getDownloadingModelStatusCode(downloadingModel.getDownloadId());
        Date now = new Date();

        // check if download has completed or still has time to finish.
        // Give a buffer above url expiry to continue if in progress.
        if (statusCode != null
            && (statusCode == DownloadManager.STATUS_SUCCESSFUL
                || statusCode == DownloadManager.STATUS_FAILED
                || (customModel.getDownloadUrlExpiry()
                    > (now.getTime() - COMPLETION_BUFFER_IN_MS)))) {
          // download in progress - return this task result.

          Log.d(TAG, "New model is already in downloading, return existing task.");
          eventLogger.logDownloadEventWithErrorCode(
              downloadingModel, false, DownloadStatus.DOWNLOADING, ErrorCode.NO_ERROR);
          return getExistingDownloadTask(downloadingModel.getDownloadId());
        }
      }

      // remove previous failed download attempts
      removeOrCancelDownloadModel(downloadingModel.getName(), downloadingModel.getDownloadId());
    }

    // schedule new download of model file
    Log.d(TAG, "Need to download a new model.");
    Long newDownloadId = null;
    try {
      newDownloadId = scheduleModelDownload(customModel);
    } catch (FirebaseMlException fex) {
      if (fex.getCode() == FirebaseMlException.DOWNLOAD_URL_EXPIRED) {
        return Tasks.forException(fex);
      }
      eventLogger.logDownloadFailureWithReason(
          customModel, false, ErrorCode.DOWNLOAD_FAILED.getValue());
    }
    if (newDownloadId == null) {
      return Tasks.forException(
          new FirebaseMlException(
              "Failed to schedule the download task", FirebaseMlException.INTERNAL));
    }

    return registerReceiverForDownloadId(newDownloadId, customModel.getName());
  }

  /** Removes or cancels the downloading model if exists. */
  synchronized void removeOrCancelDownloadModel(String modelName, Long downloadId) {
    if (downloadManager != null && downloadId != 0) {
      downloadManager.remove(downloadId);
    }
    // clean up the downloading task and the stored model.
    // TODO(annzimmer) should this task clean up include an intent to trigger onCompletion handler
    // with failure?
    removeDownloadTaskInstance(downloadId);
    sharedPreferencesUtil.clearDownloadCustomModelDetails(modelName);
  }

  private synchronized DownloadBroadcastReceiver getReceiverInstance(
      long downloadId, String modelName) {
    DownloadBroadcastReceiver receiver = this.receiverMaps.get(downloadId);
    if (receiver == null) {
      receiver =
          new DownloadBroadcastReceiver(
              downloadId, modelName, getTaskCompletionSourceInstance(downloadId));
      this.receiverMaps.put(downloadId, receiver);
    }
    return receiver;
  }

  private synchronized void removeDownloadTaskInstance(long downloadId) {
    this.taskCompletionSourceMaps.remove(downloadId);
    this.receiverMaps.remove(downloadId);
  }

  @SuppressLint("WrongConstant")
  private Task<Void> registerReceiverForDownloadId(long downloadId, String modelName) {
    BroadcastReceiver broadcastReceiver = getReceiverInstance(downloadId, modelName);
    // It is okay to always register here. Since the broadcast receiver is the same via the lookup
    // for the same download id, the same broadcast receiver will be notified only once.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(
          broadcastReceiver,
          new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
          Context.RECEIVER_EXPORTED);
    } else {
      context.registerReceiver(
          broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    return getTaskCompletionSourceInstance(downloadId).getTask();
  }

  /**
   * Returns the in progress download task if one exists, otherwise returns null.
   *
   * @param downloadId - download id associated with the requested task.
   */
  @Nullable
  public Task<Void> getExistingDownloadTask(long downloadId) {
    if (existTaskCompletionSourceInstance(downloadId)) {
      return getTaskCompletionSourceInstance(downloadId).getTask();
    }
    return null;
  }

  @VisibleForTesting
  synchronized TaskCompletionSource<Void> getTaskCompletionSourceInstance(long downloadId) {
    TaskCompletionSource<Void> taskCompletionSource = this.taskCompletionSourceMaps.get(downloadId);
    if (taskCompletionSource == null) {
      taskCompletionSource = new TaskCompletionSource<>();
      this.taskCompletionSourceMaps.put(downloadId, taskCompletionSource);
    }

    return taskCompletionSource;
  }

  @VisibleForTesting
  synchronized boolean existTaskCompletionSourceInstance(long downloadId) {
    TaskCompletionSource<Void> taskCompletionSource = this.taskCompletionSourceMaps.get(downloadId);
    return (taskCompletionSource != null);
  }

  // Scheduled downloading of this model - does not check for existing downloads.
  @VisibleForTesting
  synchronized Long scheduleModelDownload(@NonNull CustomModel customModel)
      throws FirebaseMlException {

    if (downloadManager == null) {
      Log.d(TAG, "Download manager service is not available in the service.");
      return null;
    }

    if (customModel.getDownloadUrl() == null || customModel.getDownloadUrl().isEmpty()) {
      return null;
    }
    // check for expired download url and trigger re-fetch if necessary
    Date now = new Date();
    if (customModel.getDownloadUrlExpiry() < now.getTime()) {
      eventLogger.logDownloadFailureWithReason(
          customModel, false, ErrorCode.URI_EXPIRED.getValue());
      throw new FirebaseMlException(
          "Expired url, fetch new url and retry.", FirebaseMlException.DOWNLOAD_URL_EXPIRED);
    }

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
    Log.d(TAG, "Schedule a new downloading task: " + id);
    // update the custom model to store the download id - do not lose current local file - in case
    // this is a background update.
    CustomModel model =
        modelFactory.create(
            customModel.getName(),
            customModel.getModelHash(),
            customModel.getSize(),
            id,
            customModel.getLocalFilePath());
    sharedPreferencesUtil.setDownloadingCustomModelDetails(model);
    eventLogger.logDownloadEventWithErrorCode(
        model, false, DownloadStatus.SCHEDULED, ErrorCode.NO_ERROR);
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
      Log.d(TAG, "Downloaded file is not found.");
    }
    return fileDescriptor;
  }

  public void maybeCheckDownloadingComplete() {
    for (String key : sharedPreferencesUtil.getSharedPreferenceKeySet()) {
      // if a local file path is present - get model details.
      Matcher matcher =
          Pattern.compile(SharedPreferencesUtil.DOWNLOADING_MODEL_ID_MATCHER).matcher(key);
      if (matcher.find()) {
        String modelName = matcher.group(matcher.groupCount());
        CustomModel downloadingModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
        if (downloadingModel != null) {
          Integer statusCode = getDownloadingModelStatusCode(downloadingModel.getDownloadId());
          if (statusCode != null
              && (statusCode == DownloadManager.STATUS_SUCCESSFUL
                  || statusCode == DownloadManager.STATUS_FAILED)) {
            loadNewlyDownloadedModelFile(downloadingModel);
          }
        }
      }
    }
  }

  @Nullable
  @WorkerThread
  public File loadNewlyDownloadedModelFile(CustomModel model) {
    if (model == null) {
      return null;
    }

    Long downloadingId = model.getDownloadId();
    String downloadingModelHash = model.getModelHash();

    if (downloadingId == 0 || downloadingModelHash.isEmpty()) {
      // Clear the downloading info completely.
      // It handles the case: developer clear the app cache but downloaded model file in
      // DownloadManager's cache would not be cleared.
      removeOrCancelDownloadModel(model.getName(), model.getDownloadId());
      return null;
    }

    Integer statusCode = getDownloadingModelStatusCode(downloadingId);
    if (statusCode == null) {
      Log.d(TAG, "Download failed - no download status available.");
      // No status code, it may mean no such download or no download manager.
      removeOrCancelDownloadModel(model.getName(), model.getDownloadId());
      return null;
    }

    if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
      Log.d(TAG, "Model downloaded successfully");
      eventLogger.logDownloadEventWithErrorCode(
          model, true, DownloadStatus.SUCCEEDED, ErrorCode.NO_ERROR);
      // Get downloaded file.
      ParcelFileDescriptor fileDescriptor = getDownloadedFile(downloadingId);
      if (fileDescriptor == null) {
        // reset original model - removing download id.
        removeOrCancelDownloadModel(model.getName(), model.getDownloadId());
        return null;
      }

      // Try to move it to destination folder.
      File newModelFile;
      try {
        Log.d(TAG, "Moving downloaded model from external storage to destination folder.");
        newModelFile = fileManager.moveModelToDestinationFolder(model, fileDescriptor);
      } catch (FirebaseMlException ex) {
        // add logging for this error
        newModelFile = null;
      } finally {
        removeOrCancelDownloadModel(model.getName(), model.getDownloadId());
      }

      if (newModelFile == null) {
        return null;
      }

      Log.d(
          TAG,
          "Moved the downloaded model to destination folder successfully: "
              + newModelFile.getParent());
      // Successfully moved,  update share preferences
      sharedPreferencesUtil.setLoadedCustomModelDetails(
          modelFactory.create(
              model.getName(), model.getModelHash(), model.getSize(), 0, newModelFile.getPath()));

      maybeCleanUpOldModels();

      return newModelFile;
    } else if (statusCode == DownloadManager.STATUS_FAILED) {
      Log.d(TAG, "Model downloaded failed.");
      eventLogger.logDownloadFailureWithReason(
          model, false, getFailureReason(model.getDownloadId()));

      // reset original model - removing downloading details.
      removeOrCancelDownloadModel(model.getName(), model.getDownloadId());
    }
    // Other cases, return as null and wait for download finish.
    return null;
  }

  private Task<Void> maybeCleanUpOldModels() {
    if (!isInitialLoad) {
      return Tasks.forResult(null);
    }

    // only do once per initialization.
    isInitialLoad = false;

    // for each custom model directory, find out the latest model and delete the other files.
    // If no corresponding model, clean up the full directory.
    try {
      fileManager.deleteNonLatestCustomModels();
    } catch (FirebaseMlException fex) {
      Log.d(TAG, "Failed to clean up old models.");
    }
    return Tasks.forResult(null);
  }

  private FirebaseMlException getExceptionAccordingToDownloadManager(Long downloadId) {
    int errorCode = FirebaseMlException.INTERNAL;
    String errorMessage = "Model downloading failed";
    Cursor cursor =
        (downloadManager == null || downloadId == null)
            ? null
            : downloadManager.query(new Query().setFilterById(downloadId));
    if (cursor != null && cursor.moveToFirst()) {
      int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
      if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
        errorMessage = "Model downloading failed due to insufficient space on the device.";
        errorCode = FirebaseMlException.NOT_ENOUGH_SPACE;
      } else {
        errorMessage =
            "Model downloading failed due to error code: "
                + reason
                + " from Android DownloadManager";
      }
    }
    return new FirebaseMlException(errorMessage, errorCode);
  }

  /**
   * Gets the failure reason for the {@code downloadId}. Returns 0 if there isn't a record for the
   * specified {@code downloadId}.
   */
  int getFailureReason(Long downloadId) {
    int failureReason = FirebaseMlLogEvent.NO_INT_VALUE;
    Cursor cursor =
        (downloadManager == null || downloadId == null)
            ? null
            : downloadManager.query(new Query().setFilterById(downloadId));
    if (cursor != null && cursor.moveToFirst()) {
      int index = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
      if (index != -1) {
        failureReason = cursor.getInt(index);
      }
    }
    return failureReason;
  }

  // This class runs totally on worker thread because we registered the receiver with a worker
  // thread handler.
  @WorkerThread
  private class DownloadBroadcastReceiver extends BroadcastReceiver {

    // Download Id is captured inside this class in memory. So there is no concern of inconsistency
    // with the persisted download id in shared preferences.
    private final long downloadId;
    private final String modelName;
    private final TaskCompletionSource<Void> taskCompletionSource;

    private DownloadBroadcastReceiver(
        long downloadId, String modelName, TaskCompletionSource<Void> taskCompletionSource) {
      this.downloadId = downloadId;
      this.modelName = modelName;
      this.taskCompletionSource = taskCompletionSource;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
      if (id != downloadId) {
        return;
      }

      // check to prevent DuplicateTaskCompletionException - this was already updated and removed.
      // Just return.
      if (!existTaskCompletionSourceInstance(downloadId)) {
        removeDownloadTaskInstance(downloadId);
        return;
      }

      Integer statusCode = getDownloadingModelStatusCode(downloadId);
      // check to prevent DuplicateTaskCompletionException - this was already updated and removed.
      // Just return.
      if (!existTaskCompletionSourceInstance(downloadId)) {
        removeDownloadTaskInstance(downloadId);
        return;
      }

      synchronized (ModelFileDownloadService.this) {
        try {
          context.getApplicationContext().unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
          // If we try to unregister a receiver that was never registered or has been unregistered,
          // IllegalArgumentException will be thrown by the Android Framework.
          // Our current code does not have this problem. However, in order to be safer in the
          // future, we just ignore the exception here, because it is not a big deal. The code can
          // move on.
          Log.w(
              TAG,
              "Exception thrown while trying to unregister the broadcast receiver for the download",
              e);
        }

        removeDownloadTaskInstance(downloadId);
      }

      CustomModel downloadingModel =
          sharedPreferencesUtil.getDownloadingCustomModelDetails(modelName);

      if (statusCode != null) {
        if (statusCode == DownloadManager.STATUS_FAILED) {
          int failureReason = getFailureReason(id);
          if (downloadingModel != null) {
            eventLogger.logDownloadFailureWithReason(downloadingModel, false, failureReason);
            if (checkErrorCausedByExpiry(downloadingModel.getDownloadUrlExpiry(), failureReason)) {
              // this error will trigger a specific number of retries.
              taskCompletionSource.setException(
                  new FirebaseMlException(
                      "Retry: Expired URL for id: " + downloadingModel.getDownloadId(),
                      FirebaseMlException.DOWNLOAD_URL_EXPIRED));
              return;
            }
          }
          taskCompletionSource.setException(getExceptionAccordingToDownloadManager(id));
          return;
        }

        if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
          if (downloadingModel == null) {
            // model update might have been completed already get the downloaded model.
            downloadingModel = sharedPreferencesUtil.getCustomModelDetails(modelName);
            if (downloadingModel == null) {
              taskCompletionSource.setException(
                  new FirebaseMlException(
                      "Possible caching issues: No model associated with name: " + modelName,
                      FirebaseMlException.INTERNAL));
              return;
            }
          }
          eventLogger.logDownloadEventWithExactDownloadTime(
              downloadingModel, ErrorCode.NO_ERROR, DownloadStatus.SUCCEEDED);
          taskCompletionSource.setResult(null);
          return;
        }
      }

      // Status code is null or not one of success or fail.
      if (downloadingModel != null) {
        eventLogger.logDownloadFailureWithReason(
            downloadingModel, false, FirebaseMlLogger.NO_FAILURE_VALUE);
      }
      taskCompletionSource.setException(
          new FirebaseMlException("Model downloading failed", FirebaseMlException.INTERNAL));
    }

    private boolean checkErrorCausedByExpiry(long downloadUrlExpiry, int failureReason) {
      final Date time = new Date();
      return (failureReason == 400 && downloadUrlExpiry < time.getTime());
    }
  }
}
