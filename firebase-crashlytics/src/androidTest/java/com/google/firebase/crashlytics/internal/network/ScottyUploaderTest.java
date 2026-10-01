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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.uploader.client.DataStream;
import com.google.uploader.client.HttpResponse;
import com.google.uploader.client.Transfer;
import com.google.uploader.client.TransferException;
import com.google.uploader.client.TransferExceptionOrHttpResponse;
import com.google.uploader.client.TransferListener;
import com.google.uploader.client.UploadClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class ScottyUploaderTest extends CrashlyticsTestCase {

  private static class FakeTransfer implements Transfer {

    private TransferListener listener;
    private final HttpResponse httpResponse;
    private final TransferException transferException;
    private final String handle;

    FakeTransfer(HttpResponse httpResponse, TransferException transferException, String handle) {
      this.httpResponse = httpResponse;
      this.transferException = transferException;
      this.handle = handle;
    }

    @Override
    public ListenableFuture<TransferExceptionOrHttpResponse> send() {
      if (listener != null) {
        if (handle != null) {
          listener.onTransferHandleReady(this);
        }
        if (httpResponse != null) {
          listener.onResponseReceived(this, httpResponse);
        }
        if (transferException != null) {
          listener.onException(this, transferException);
        }
      }

      if (transferException != null) {
        return Futures.immediateFuture(new TransferExceptionOrHttpResponse(transferException));
      }
      return Futures.immediateFuture(new TransferExceptionOrHttpResponse(httpResponse));
    }

    @Override
    public void attachListener(
        TransferListener listener, int progressThresholdBytes, int progressThresholdMillis) {
      this.listener = listener;
    }

    @Override
    public void detachListener() {
      this.listener = null;
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public DataStream getRequestBody() {
      return null;
    }

    @Override
    public long getBytesUploaded() {
      return 0;
    }

    @Override
    public String getTransferHandle() {
      return handle;
    }

    @Override
    public void cancel() {}
  }

  private static class TestListener extends ScottyUploader.Listener {
    final AtomicBoolean doneOrUnrecoverableCalled = new AtomicBoolean(false);
    final AtomicBoolean handleAvailableCalled = new AtomicBoolean(false);
    String availableHandle = null;

    @Override
    public void onDoneOrUnrecoverable() {
      doneOrUnrecoverableCalled.set(true);
    }

    @Override
    public void onHandleAvailable(String handle) {
      handleAvailableCalled.set(true);
      availableHandle = handle;
    }
  }

  @Test
  public void testTriggeringUpload_Success() throws IOException {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    Path heapdumpPath = Files.createTempFile("heapdump", "perfetto");
    File heapdump = heapdumpPath.toFile();
    heapdump.deleteOnExit();

    when(fileStore.getCommonFile(anyString())).thenReturn(heapdump);

    HttpResponse httpResponse = new HttpResponse(200, null, null);
    FakeTransfer fakeTransfer = new FakeTransfer(httpResponse, null, "handle123");

    when(client.createTransfer(anyString(), anyString(), any(), any(), anyString(), any()))
        .thenReturn(fakeTransfer);

    TestListener listener = new TestListener();
    scottyUploader.triggerUpload("gmpAppId", "sessionId", 1, "heapdump.perfetto", listener);

    assertThat(listener.handleAvailableCalled.get()).isTrue();
    assertThat(listener.availableHandle).isEqualTo("handle123");
    assertThat(listener.doneOrUnrecoverableCalled.get()).isTrue();
  }

  @Test
  public void testTriggeringUpload_RecoverableHttpError() throws IOException {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    Path heapdumpPath = Files.createTempFile("heapdump", "perfetto");
    File heapdump = heapdumpPath.toFile();
    heapdump.deleteOnExit();

    when(fileStore.getCommonFile(anyString())).thenReturn(heapdump);

    HttpResponse httpResponse = new HttpResponse(503, null, null); // Service Unavailable
    FakeTransfer fakeTransfer = new FakeTransfer(httpResponse, null, null);

    when(client.createTransfer(anyString(), anyString(), any(), any(), anyString(), any()))
        .thenReturn(fakeTransfer);

    TestListener listener = new TestListener();
    scottyUploader.triggerUpload("gmpAppId", "sessionId", 1, "heapdump.perfetto", listener);

    assertThat(listener.handleAvailableCalled.get()).isFalse();
    assertThat(listener.doneOrUnrecoverableCalled.get()).isFalse();
  }

  @Test
  public void testTriggeringUpload_UnrecoverableHttpError() throws IOException {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    Path heapdumpPath = Files.createTempFile("heapdump", "perfetto");
    File heapdump = heapdumpPath.toFile();
    heapdump.deleteOnExit();

    when(fileStore.getCommonFile(anyString())).thenReturn(heapdump);

    HttpResponse httpResponse = new HttpResponse(404, null, null); // Not Found
    FakeTransfer fakeTransfer = new FakeTransfer(httpResponse, null, null);

    when(client.createTransfer(anyString(), anyString(), any(), any(), anyString(), any()))
        .thenReturn(fakeTransfer);

    TestListener listener = new TestListener();
    scottyUploader.triggerUpload("gmpAppId", "sessionId", 1, "heapdump.perfetto", listener);

    assertThat(listener.handleAvailableCalled.get()).isFalse();
    assertThat(listener.doneOrUnrecoverableCalled.get()).isTrue();
  }

  @Test
  public void testTriggeringUpload_RecoverableTransferException() throws IOException {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    Path heapdumpPath = Files.createTempFile("heapdump", "perfetto");
    File heapdump = heapdumpPath.toFile();
    heapdump.deleteOnExit();

    when(fileStore.getCommonFile(anyString())).thenReturn(heapdump);

    TransferException transferException =
        new TransferException(TransferException.Type.CONNECTION_ERROR, "Connection error");
    FakeTransfer fakeTransfer = new FakeTransfer(null, transferException, null);

    when(client.createTransfer(anyString(), anyString(), any(), any(), anyString(), any()))
        .thenReturn(fakeTransfer);

    TestListener listener = new TestListener();
    scottyUploader.triggerUpload("gmpAppId", "sessionId", 1, "heapdump.perfetto", listener);

    assertThat(listener.handleAvailableCalled.get()).isFalse();
    assertThat(listener.doneOrUnrecoverableCalled.get()).isFalse();
  }

  @Test
  public void testTriggeringUpload_UnrecoverableTransferException() throws IOException {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    Path heapdumpPath = Files.createTempFile("heapdump", "perfetto");
    File heapdump = heapdumpPath.toFile();
    heapdump.deleteOnExit();

    when(fileStore.getCommonFile(anyString())).thenReturn(heapdump);

    TransferException transferException =
        new TransferException(TransferException.Type.BAD_URL, "Bad URL");
    FakeTransfer fakeTransfer = new FakeTransfer(null, transferException, null);

    when(client.createTransfer(anyString(), anyString(), any(), any(), anyString(), any()))
        .thenReturn(fakeTransfer);

    TestListener listener = new TestListener();
    scottyUploader.triggerUpload("gmpAppId", "sessionId", 1, "heapdump.perfetto", listener);

    assertThat(listener.handleAvailableCalled.get()).isFalse();
    assertThat(listener.doneOrUnrecoverableCalled.get()).isTrue();
  }

  @Test
  public void testTriggeringUpload_FileDoesNotExist() {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    File nonExistentFile = new File("/path/to/non/existent/file");
    when(fileStore.getCommonFile(anyString())).thenReturn(nonExistentFile);

    TestListener listener = new TestListener();
    scottyUploader.triggerUpload("gmpAppId", "sessionId", 1, "heapdump.perfetto", listener);

    assertThat(listener.handleAvailableCalled.get()).isFalse();
    assertThat(listener.doneOrUnrecoverableCalled.get()).isTrue();
  }

  @Test
  public void testResumeUpload_Success() throws IOException {
    UploadClient client = mock(UploadClient.class);
    FileStore fileStore = mock(FileStore.class);

    ScottyUploader scottyUploader = new ScottyUploader("key", fileStore, client);

    Path heapdumpPath = Files.createTempFile("heapdump", "perfetto");
    File heapdump = heapdumpPath.toFile();
    heapdump.deleteOnExit();

    when(fileStore.getCommonFile(anyString())).thenReturn(heapdump);

    HttpResponse httpResponse = new HttpResponse(200, null, null);
    FakeTransfer fakeTransfer = new FakeTransfer(httpResponse, null, "handle123");

    when(client.resumeTransfer(anyString(), any(), any())).thenReturn(fakeTransfer);

    TestListener listener = new TestListener();
    scottyUploader.resumeUpload("heapdump.perfetto", "handle123", listener);

    assertThat(listener.handleAvailableCalled.get()).isTrue();
    assertThat(listener.doneOrUnrecoverableCalled.get()).isTrue();
  }
}
