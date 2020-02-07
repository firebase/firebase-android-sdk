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

package com.google.firebase.inappmessaging.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Application;
import android.content.Context;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import io.reactivex.observers.TestObserver;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ProtoStorageClientTest {
  private static final String FILE_NAME = "file";
  private final FetchEligibleCampaignsResponse response =
      FetchEligibleCampaignsResponse.getDefaultInstance();
  private ProtoStorageClient protoStorageClient;
  @Mock private Application application;
  @Mock private FileOutputStream fileOutputStream;
  @Mock private FileInputStream fileInputStream;
  @Mock private Parser<FetchEligibleCampaignsResponse> parser;

  @Before
  public void setup() throws IOException {
    initMocks(this);
    protoStorageClient = new ProtoStorageClient(application, FILE_NAME);
    when(application.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)).thenReturn(fileOutputStream);
    when(application.openFileInput(FILE_NAME)).thenReturn(fileInputStream);
    when(parser.parseFrom(fileInputStream)).thenReturn(response);
  }

  @Test
  public void write_noFailure_writesBytes() throws IOException {
    protoStorageClient.write(response).subscribe();

    verify(fileOutputStream).write(response.toByteArray());
  }

  @Test
  public void write_noFailure_completes() {
    TestObserver<Void> subscriber = protoStorageClient.write(response).test();

    subscriber.assertComplete();
  }

  @Test
  public void write_onFailure_notfiedError() throws IOException {
    when(application.openFileOutput(FILE_NAME, Context.MODE_PRIVATE))
        .thenThrow(new FileNotFoundException());

    TestObserver<Void> subscriber = protoStorageClient.write(response).test();

    subscriber.assertError(FileNotFoundException.class);
  }

  @Test
  public void write_noFailure_closesStream() throws IOException {
    protoStorageClient.write(response).subscribe();

    verify(fileOutputStream).close();
  }

  @Test
  public void write_withFailureOpeningFile_doesNotCloseStream() throws IOException {
    when(application.openFileOutput(FILE_NAME, Context.MODE_PRIVATE))
        .thenThrow(new FileNotFoundException());

    protoStorageClient.write(response).subscribe();

    verify(fileOutputStream, times(0)).close();
  }

  @Test
  public void write_withFailureWriting_closesStream() throws IOException {
    doThrow(new IOException()).when(fileOutputStream).write(response.toByteArray());

    protoStorageClient.write(response).subscribe();

    verify(fileOutputStream).close();
  }

  @Test
  public void read_noFailure_readsBytes() {
    TestObserver<FetchEligibleCampaignsResponse> subscriber =
        protoStorageClient.read(parser).test();

    assertThat(subscriber.getEvents().get(0)).containsExactly(response);
  }

  @Test
  public void read_onFailure_propagatesException() throws IOException {
    when(application.openFileInput(FILE_NAME)).thenThrow(new NullPointerException());

    TestObserver<FetchEligibleCampaignsResponse> subscriber =
        protoStorageClient.read(parser).test();

    subscriber.assertError(NullPointerException.class);
  }

  @Test
  public void read_onFileNotFound_absorbsError() throws IOException {
    when(application.openFileInput(FILE_NAME)).thenThrow(new FileNotFoundException());

    TestObserver<FetchEligibleCampaignsResponse> subscriber =
        protoStorageClient.read(parser).test();

    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  @Test
  public void read_onParsingException_absorbsError() throws IOException {
    when(parser.parseFrom(fileInputStream)).thenThrow(new InvalidProtocolBufferException(""));

    TestObserver<FetchEligibleCampaignsResponse> subscriber =
        protoStorageClient.read(parser).test();

    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  @Test
  public void read_noFailure_closesStream() throws IOException {
    protoStorageClient.read(parser).subscribe();

    verify(fileInputStream).close();
  }

  @Test
  public void read_withFailureOpeningFile_doesNotCloseStream() throws IOException {
    when(application.openFileInput(FILE_NAME)).thenThrow(new FileNotFoundException());

    protoStorageClient.read(parser).subscribe();

    verify(fileInputStream, times(0)).close();
  }

  @Test
  public void read_withFailureReading_closesStream() throws IOException {
    doThrow(new InvalidProtocolBufferException("")).when(parser).parseFrom(fileInputStream);

    protoStorageClient.read(parser).subscribe();

    verify(fileInputStream).close();
  }
}
