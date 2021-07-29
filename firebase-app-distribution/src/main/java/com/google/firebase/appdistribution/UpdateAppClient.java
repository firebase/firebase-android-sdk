
package com.google.firebase.appdistribution;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;

public class UpdateClient {

    private TaskCompletionSource<UpdateState> updateAppTaskCompletionSource = null;
    private CancellationTokenSource updateAppCancellationSource;
    private UpdateTaskImpl updateTask;
    private Context context;

    public UpdateClient(Context context) {
        this.context = context;
    }

    public UpdateTask getUpdateTask(
            AppDistributionReleaseInternal latestRelease, Activity currentActivity)
            throws FirebaseAppDistributionException {
        if (latestRelease == null) {
            return new UpdateTaskImpl(
                    Tasks.forException(
                            new FirebaseAppDistributionException(
                                    Constants.ErrorMessages.NOT_FOUND_ERROR, UPDATE_NOT_AVAILABLE)));
        }

        if (updateAppTaskCompletionSource != null
                && !updateAppTaskCompletionSource.getTask().isComplete()) {
            updateAppCancellationSource.cancel();
        }

        updateAppCancellationSource = new CancellationTokenSource();
        updateAppTaskCompletionSource =
                new TaskCompletionSource<>(updateAppCancellationSource.getToken());
        this.updateTask = new UpdateTaskImpl(updateAppTaskCompletionSource.getTask());

        if (latestRelease.getBinaryType() == BinaryType.AAB) {
            redirectToPlayForAabUpdate(latestRelease.getDownloadUrl(), currentActivity);
        } else {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        return this.updateTask;
    }

    private void redirectToPlayForAabUpdate(String downloadUrl, Activity currentActivity)
            throws FirebaseAppDistributionException {
        if (downloadUrl == null) {
            throw new FirebaseAppDistributionException(
                    "Download URL not found.", FirebaseAppDistributionException.Status.NETWORK_FAILURE);
        }
        Intent updateIntent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(downloadUrl);
        updateIntent.setData(uri);
        updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        currentActivity.startActivity(updateIntent);
        UpdateState updateState =
                UpdateState.builder()
                        .setApkBytesDownloaded(-1)
                        .setApkTotalBytesToDownload(-1)
                        .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
                        .build();
        updateAppTaskCompletionSource.setResult(updateState);
        this.updateTask.updateProgress(updateState);
    }
}