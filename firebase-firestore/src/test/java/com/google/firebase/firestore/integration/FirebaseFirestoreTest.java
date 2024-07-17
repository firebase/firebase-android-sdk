// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.util.Executors.BACKGROUND_EXECUTOR;
import static org.mockito.Mockito.verify;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreIntegrationTestFactory;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.core.UserData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firestore.v1.Write;
import com.google.firestore.v1.WriteRequest;
import com.google.firestore.v1.WriteResponse;
import com.google.firestore.v1.WriteResult;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseFirestoreTest {

  private DatabaseId databaseId;
  private FirebaseFirestore firestore;
  private FirebaseFirestoreIntegrationTestFactory factory;

  private static void waitForSuccess(Task<?> task) throws InterruptedException {
    waitFor(task).getResult();
  }

  private static <T> T waitForResult(Task<T> task) throws InterruptedException {
    return waitFor(task).getResult();
  }

  @NonNull
  public static String getResourcePrefixValue(DatabaseId databaseId) {
    return String.format(
        "projects/%s/databases/%s", databaseId.getProjectId(), databaseId.getDatabaseId());
  }

  private static <T> Task<T> waitFor(Task<T> task) throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    task.addOnSuccessListener(BACKGROUND_EXECUTOR, t -> countDownLatch.countDown());
    task.addOnFailureListener(BACKGROUND_EXECUTOR, e -> countDownLatch.countDown());
    task.addOnCanceledListener(BACKGROUND_EXECUTOR, () -> countDownLatch.countDown());
    countDownLatch.await(15, TimeUnit.SECONDS);
    return task;
  }

  @Before
  public void before() {
    databaseId = DatabaseId.forDatabase("p", "d");
    factory = new FirebaseFirestoreIntegrationTestFactory(databaseId);
    factory.useMemoryCache();
    firestore = factory.firestore;
  }

  @After
  public void after() throws Exception {
    waitForSuccess(firestore.terminate());
    verify(factory.instanceRegistry, Mockito.atLeastOnce()).remove(databaseId.getDatabaseId());
    Mockito.verifyNoMoreInteractions(factory.instanceRegistry);

    factory = null;
    firestore = null;
  }

  @Test
  public void preserveWritesWhenDisconnectedWithInternalError() throws Exception {
    CollectionReference col = firestore.collection("col");
    DocumentReference doc1 = col.document();
    DocumentReference doc2 = col.document();
    DocumentReference doc3 = col.document();
    doc1.set(map("foo", "A"));
    doc2.set(map("foo", "B"));
    doc3.set(map("foo", "C"));

    // Wait for first FirestoreClient to instantiate
    FirebaseFirestoreIntegrationTestFactory.Instance instance =
        waitForResult(factory.instances.get(0));
    RemoteSerializer serializer = instance.componentProvider.getRemoteSerializer();

    // First Write stream connection
    {
      // Wait for Write CallClient to be created.
      TestClientCall<WriteRequest, WriteResponse> callback =
          waitForResult(instance.getWriteClient(0));
      Iterator<Task<WriteRequest>> requests = callback.requestIterator();

      // Wait for WriteRequest handshake.
      // We expect an empty init request because the database is fresh.
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequestHandshake());

      // Simulate a successful InitResponse from server.
      waitForSuccess(instance.enqueue(() -> callback.listener.onMessage(writeResponse())));

      // Expect first write request.
      Write write1 = serializer.encodeMutation(setMutation(doc1, map("foo", "A")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write1));

      // Simulate write acknowledgement.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));

      // Expect second write request.
      Write write2 = serializer.encodeMutation(setMutation(doc2, map("foo", "B")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write2));

      // Simulate INTERNAL error that is retryable. (
      waitForSuccess(
          instance.enqueue(() -> callback.listener.onClose(Status.INTERNAL, new Metadata())));
    }

    // Second Write Stream connection
    // Previous connection was closed by server with NOT_FOUND error.
    {
      // Wait for Write CallClient to be created.
      TestClientCall<WriteRequest, WriteResponse> callback =
          waitForResult(instance.getWriteClient(1));
      Iterator<Task<WriteRequest>> requests = callback.requestIterator();

      // Wait for WriteRequest handshake.
      // We expect FirestoreClient to send InitRequest with previous token.
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequestHandshake());

      // Simulate a successful InitResponse from server.
      waitForSuccess(instance.enqueue(() -> callback.listener.onMessage(writeResponse())));

      // Expect second write to be retried.
      Write write2 = serializer.encodeMutation(setMutation(doc2, map("foo", "B")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write2));

      // Simulate write acknowledgement.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));

      // Expect second write request.
      Write write3 = serializer.encodeMutation(setMutation(doc3, map("foo", "C")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write3));
    }
  }

  @NonNull
  private DocumentKey key(DocumentReference doc) {
    return DocumentKey.fromPathString(doc.getPath());
  }

  @NonNull
  public SetMutation setMutation(DocumentReference doc, Map<String, Object> values) {
    UserDataReader dataReader = new UserDataReader(databaseId);
    UserData.ParsedSetData parsed = dataReader.parseSetData(values);

    // The order of the transforms doesn't matter, but we sort them so tests can assume a particular
    // order.
    ArrayList<FieldTransform> fieldTransforms = new ArrayList<>(parsed.getFieldTransforms());
    Collections.sort(fieldTransforms, Comparator.comparing(FieldTransform::getFieldPath));

    return new SetMutation(key(doc), parsed.getData(), Precondition.NONE, fieldTransforms);
  }

  @NonNull
  private WriteRequest writeRequestHandshake() {
    return WriteRequest.newBuilder().setDatabase(getResourcePrefixValue(databaseId)).build();
  }

  @NonNull
  private WriteRequest writeRequest(Write... writes) {
    WriteRequest.Builder builder = WriteRequest.newBuilder();
    for (Write write : writes) {
      builder.addWrites(write);
    }
    return builder.build();
  }

  @NonNull
  private static WriteResponse writeResponse(WriteResult... writeResults) {
    WriteResponse.Builder builder = WriteResponse.newBuilder();
    for (WriteResult writeResult : writeResults) {
      builder.addWriteResults(writeResult);
    }
    return builder.build();
  }
}
