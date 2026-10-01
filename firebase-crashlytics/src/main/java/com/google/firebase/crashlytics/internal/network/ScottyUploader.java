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

import com.google.common.util.concurrent.ListenableFuture;
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
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ScottyUploader {
  private static final String URL_PREFIX =
      DataTransportCrashlyticsReportSender.mergeStrings(
          "hts/frbslgigp.ogepscmula/1frlglgc/pod", "tp:/ieaeogn-agolai.o/podv/ieo/eayula");

  private final String key;
  private final FileStore fileStore;

  private final UploadClient uploadClient;

  private ListenableFuture<TransferExceptionOrHttpResponse> upload;

  public abstract static class Listener extends TransferListener {
    public Listener() {}

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
      if (isSuccess(response) || !isRecoverable(response)) {
        onDoneOrUnrecoverable();
      }
    }

    @Override
    public void onException(Transfer transfer, TransferException exception) {
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
    this.upload =
        new ListenableFuture<TransferExceptionOrHttpResponse>() {
          @Override
          public void addListener(Runnable listener, Executor executor) {
            executor.execute(listener);
          }

          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
          }

          @Override
          public TransferExceptionOrHttpResponse get()
              throws ExecutionException, InterruptedException {
            return null;
          }

          @Override
          public TransferExceptionOrHttpResponse get(long timeout, TimeUnit unit)
              throws ExecutionException, InterruptedException, TimeoutException {
            return null;
          }

          @Override
          public boolean isCancelled() {
            return true;
          }

          @Override
          public boolean isDone() {
            return false;
          }
        };
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
    File heapdump = fileStore.getCommonFile(String.format("../../profiling/%s", filename));

    if (heapdump.exists()) {
      triggerNewUpload(gmpAppId, sessionId, type, heapdump, listener);
      return;
    }

    Logger.getLogger().w(String.format("Heapdump does not exist, skipping upload; %s", filename));
    listener.onDoneOrUnrecoverable();
  }

  private void triggerNewUpload(
      String gmpAppId, String sessionId, int type, File heapdump, Listener listener) {
    stream(
        heapdump,
        stream -> {
          HttpHeaders headers = new HttpHeaders();
          headers.set("Content-Type", "application/octet-stream");

          String url = getUrl(key, gmpAppId, sessionId, type);

          TransferOptions options = TransferOptions.newBuilder().build();
          Transfer transfer =
              uploadClient.createTransfer(url, "POST", headers, stream, "", options);

          transfer.attachListener(
              listener,
              /* progressThresholdBytes= */ 1024 * 1024,
              /* progressThresholdMillis= */ 1000);

          upload = transfer.send();
        });
  }

  private void resumeExistingUpload(String filename, String handle, Listener listener) {
    File heapdump = fileStore.getCommonFile(String.format("../../profiling/%s", filename));

    if (heapdump.exists()) {
      resumeExistingUpload(heapdump, handle, listener);
      return;
    }

    Logger.getLogger()
        .w(String.format("Heapdump no longer exists, skipping resuming upload; %s", filename));
    listener.onDoneOrUnrecoverable();
  }

  private void resumeExistingUpload(File heapdump, String handle, Listener listener) {
    stream(
        heapdump,
        stream -> {
          TransferOptions options = TransferOptions.newBuilder().build();
          Transfer transfer = uploadClient.resumeTransfer(handle, stream, options);

          transfer.attachListener(
              listener,
              /* progressThresholdBytes= */ 1024 * 1024,
              /* progressThresholdMillis= */ 1000);

          upload = transfer.send();
        });
  }

  private static void stream(File heapdump, Consumer<DataStream> stream) {
    try (DataStream body = new FileDataStream(heapdump)) {
      stream.accept(body);
    } catch (IOException e) {
      Logger.getLogger().e("Couldn't create file stream", e);
    } catch (Exception e) {
      Logger.getLogger().e("Couldn't upload", e);
    }
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
