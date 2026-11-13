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

package com.google.firebase.firestore.remote;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.PipelineResultObserver;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.testutil.EmptyAppCheckTokenProvider;
import com.google.firebase.firestore.testutil.EmptyCredentialsProvider;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firestore.v1.ExecutePipelineRequest;
import com.google.firestore.v1.TransactionOptions;
import com.google.protobuf.ByteString;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DatastoreTest {

  private static final Metadata.Key<String> X_GOOG_IS_TRANSACTION_HEADER =
      Metadata.Key.of("x-goog-is-transaction", Metadata.ASCII_STRING_MARSHALLER);

  private AsyncQueue asyncQueue;
  private ClientCall<Object, Object> mockClientCall;
  private Datastore datastore;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    asyncQueue = new AsyncQueue();
    DatabaseId databaseId = DatabaseId.forDatabase("project", "(default)");
    RemoteSerializer serializer = new RemoteSerializer(databaseId);
    GrpcCallProvider mockGrpcCallProvider = mock(GrpcCallProvider.class);
    mockClientCall = mock(ClientCall.class);
    when(mockGrpcCallProvider.createClientCall(any())).thenReturn(Tasks.forResult(mockClientCall));

    FirestoreChannel channel =
        new FirestoreChannel(
            asyncQueue,
            new EmptyCredentialsProvider(),
            new EmptyAppCheckTokenProvider(),
            databaseId,
            /* metadataProvider= */ null,
            mockGrpcCallProvider);
    datastore = new Datastore(asyncQueue, serializer, channel);
  }

  @After
  public void tearDown() {
    asyncQueue.shutdown();
  }

  private Metadata captureStartedCallHeaders() throws InterruptedException {
    asyncQueue.runSync(() -> {});
    ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(mockClientCall).start(any(), headersCaptor.capture());
    return headersCaptor.getValue();
  }

  @Test
  public void commitSetsIsTransactionHeaderToTrue() throws Exception {
    datastore.commit(Collections.emptyList());

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("true");
  }

  @Test
  public void lookupSetsIsTransactionHeaderToTrue() throws Exception {
    datastore.lookup(Collections.singletonList(key("foo/bar")));

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("true");
  }

  @Test
  public void runAggregateQuerySetsIsTransactionHeaderToFalse() throws Exception {
    datastore.runAggregateQuery(query("foo"), Collections.singletonList(AggregateField.count()));

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("false");
  }

  @Test
  public void executePipelineWithoutTransactionSetsIsTransactionHeaderToFalse() throws Exception {
    datastore.executePipeline(
        ExecutePipelineRequest.getDefaultInstance(), mock(PipelineResultObserver.class));

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("false");
  }

  @Test
  public void executePipelineWithTransactionSetsIsTransactionHeaderToTrue() throws Exception {
    ExecutePipelineRequest request =
        ExecutePipelineRequest.newBuilder()
            .setTransaction(ByteString.copyFromUtf8("tx-id"))
            .build();
    datastore.executePipeline(request, mock(PipelineResultObserver.class));

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("true");
  }

  @Test
  public void executePipelineWithNewTransactionSetsIsTransactionHeaderToTrue() throws Exception {
    ExecutePipelineRequest request =
        ExecutePipelineRequest.newBuilder()
            .setNewTransaction(TransactionOptions.getDefaultInstance())
            .build();
    datastore.executePipeline(request, mock(PipelineResultObserver.class));

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("true");
  }

  @Test
  public void watchStreamSetsIsTransactionHeaderToFalse() throws Exception {
    WatchStream watchStream = datastore.createWatchStream(mock(WatchStream.Callback.class));
    asyncQueue.runSync(watchStream::start);

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("false");
  }

  @Test
  public void writeStreamSetsIsTransactionHeaderToFalse() throws Exception {
    WriteStream writeStream = datastore.createWriteStream(mock(WriteStream.Callback.class));
    asyncQueue.runSync(writeStream::start);

    Metadata headers = captureStartedCallHeaders();
    assertThat(headers.get(X_GOOG_IS_TRANSACTION_HEADER)).isEqualTo("false");
  }
}
