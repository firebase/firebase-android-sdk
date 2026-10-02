// Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.internal.network;

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorker;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import com.google.uploader.client.DataStream;
import com.google.uploader.client.FileDataStream;
import com.google.uploader.client.HttpHeaders;
import com.google.uploader.client.HttpResponse;
import com.google.uploader.client.HttpUrlConnectionHttpClient;
import com.google.uploader.client.Transfer;
import com.google.uploader.client.TransferException;
import com.google.uploader.client.TransferExceptionOrHttpResponse;
import com.google.uploader.client.TransferListener;
import com.google.uploader.client.TransferOptions;
import com.google.uploader.client.UploadClient;
import com.google.uploader.client.UploadClientImpl;
import com.google.uploader.shaded.guava.util.concurrent.Futures;
import com.google.uploader.shaded.guava.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class ScottyUploader {
  private static final String URL_PREFIX =
      DataTransportCrashlyticsReportSender.mergeStrings(
          "hts/frbslgigp.ogepscmula/1frlglgc/pod", "tp:/ieaeogn-agolai.o/podv/ieo/eayula");

  private final String key;
  private final FileStore fileStore;

  private final UploadClient uploadClient;

  private volatile ListenableFuture<TransferExceptionOrHttpResponse> upload;

  public abstract static class Listener extends TransferListener {
    private volatile DataStream stream;

    public Listener() {}

    FileDataStream withManagedStream(File heapdump) throws FileNotFoundException {
      FileDataStream stream = new FileDataStream(heapdump);
      this.stream = stream;
      return stream;
    }

    public void closeStream() {
      if (stream == null) {
        return;
      }

      try {
        stream.close();
      } catch (IOException e) {
        Logger.getLogger().e("Failed to close DataStream", e);
      } finally {
        stream = null;
      }
    }

    @Override
    public void onTransferHandleReady(Transfer transfer) {
      if (transfer != null && transfer.getTransferHandle() != null) {
        onHandleAvailable(transfer.getTransferHandle());
      }
    }

    @Override
    public void onUploadProgress(Transfer transfer) {}

    @Override
    public void onResponseReceived(Transfer transfer, HttpResponse response) {
      closeStream();
      if (isSuccess(response) || !isRecoverable(response)) {
        onDoneOrUnrecoverable();
      }
    }

    @Override
    public void onException(Transfer transfer, TransferException exception) {
      closeStream();
      if (exception != null && !exception.isRecoverable()) {
        onDoneOrUnrecoverable();
      }
    }

    public void onDoneOrUnrecoverable() {}

    public void onHandleAvailable(String handle) {}

    public static boolean isRecoverable(HttpResponse response) {
      if (response == null) {
        return false;
      }

      int status = response.getResponseCode();
      return status == 504 // Gateway Timeout
          || status == 503 // Service Unavailable
          || status == 502 // Bad Gateway
          || status == 500 // Internal Server Error
          || status == 429 // Too Many Requests
          || status == 425 // Too Early
          || status == 408; // Request Timeout
    }

    public static boolean isSuccess(HttpResponse response) {
      return response != null && response.getResponseCode() == 200;
    }
  }

  public ScottyUploader(String key, FileStore fileStore, UploadClient uploadClient) {
    this.key = key;
    this.fileStore = fileStore;
    this.uploadClient = uploadClient;
    this.upload = Futures.immediateCancelledFuture();
  }

  public static UploadClient makeUploadClient(CrashlyticsWorker worker) {
    return UploadClientImpl.newBuilder(new HttpUrlConnectionHttpClient(worker)).build();
  }

  public void triggerUpload(
      String gmpAppId, String sessionId, int type, String path, Listener listener) {
    if (upload.isCancelled() || upload.isDone()) {
      triggerNewUpload(gmpAppId, sessionId, type, path, listener);
    }
  }

  public void resumeUpload(String path, String handle, Listener listener) {
    if (upload.isCancelled() || upload.isDone()) {
      resumeExistingUpload(path, handle, listener);
    }
  }

  private void triggerNewUpload(
      String gmpAppId, String sessionId, int type, String filename, Listener listener) {
    File heapdump = getHeapdumpFile(filename);

    if (heapdump.exists()) {
      triggerNewUpload(gmpAppId, sessionId, type, heapdump, listener);
      return;
    }

    Logger.getLogger().w(String.format("Heapdump does not exist, skipping upload; %s", filename));
    listener.onDoneOrUnrecoverable();
  }

  private void triggerNewUpload(
      String gmpAppId, String sessionId, int type, File heapdump, Listener listener) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.set("Content-Type", "application/octet-stream");

      String url = getUrl(key, gmpAppId, sessionId, type);

      TransferOptions options = TransferOptions.newBuilder().build();
      Transfer transfer =
          uploadClient.createTransfer(
              url, "POST", headers, listener.withManagedStream(heapdump), "", options);

      transfer.attachListener(
          listener, /* progressThresholdBytes= */ 1024 * 1024, /* progressThresholdMillis= */ 1000);

      upload = transfer.send();
    } catch (Exception e) {
      Logger.getLogger().e("Couldn't upload", e);
      listener.closeStream();
      listener.onDoneOrUnrecoverable();
    }
  }

  private void resumeExistingUpload(String filename, String handle, Listener listener) {
    File heapdump = getHeapdumpFile(filename);

    if (heapdump.exists()) {
      resumeExistingUpload(heapdump, handle, listener);
      return;
    }

    Logger.getLogger()
        .w(String.format("Heapdump no longer exists, skipping resuming upload; %s", filename));
    listener.onDoneOrUnrecoverable();
  }

  private void resumeExistingUpload(File heapdump, String handle, Listener listener) {
    try {
      TransferOptions options = TransferOptions.newBuilder().build();
      Transfer transfer =
          uploadClient.resumeTransfer(handle, listener.withManagedStream(heapdump), options);

      transfer.attachListener(
          listener, /* progressThresholdBytes= */ 1024 * 1024, /* progressThresholdMillis= */ 1000);

      upload = transfer.send();
    } catch (Exception e) {
      Logger.getLogger().e("Couldn't upload", e);
      listener.closeStream();
      listener.onDoneOrUnrecoverable();
    }
  }

  private File getHeapdumpFile(String filename) {
    // Heapdumps are stored in the app's files directory under "profiling/"
    // See SessionReportingCoordinator#heapDumpForSessionTime
    File current = fileStore.getCommonFile("");
    while (current != null) {
      File profilingDir = new File(current, "profiling");
      if (profilingDir.exists() && profilingDir.isDirectory()) {
        return new File(profilingDir, filename);
      }
      current = current.getParentFile();
    }

    Logger.getLogger().w("Could not find profiling directory, falling back to relative path");
    return fileStore.getCommonFile(String.format("../../profiling/%s", filename));
  }

  private static String getUrl(String key, String gmpAppId, String sessionId, int type) {
    Map<String, String> params =
        Map.of(
            "uploadType", "media",
            "crashlytics_app_id", gmpAppId,
            "trigger_type", Integer.toString(type),
            "session_id", sessionId,
            "key", key);

    return String.format(
        "%s?%s",
        URL_PREFIX,
        params.entrySet().stream()
            .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining("&")));
  }
}
