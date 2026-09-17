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

import static org.junit.Assert.assertThrows;

import com.google.firebase.firestore.remote.Stream.StreamCallback;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AbstractStreamTest {

  private static class TestStream extends AbstractStream<String, String, StreamCallback> {
    TestStream(FirestoreChannel channel, AsyncQueue workerQueue, StreamCallback listener) {
      super(
          channel,
          Mockito.mock(MethodDescriptor.class),
          workerQueue,
          TimerId.LISTEN_STREAM_CONNECTION_BACKOFF,
          TimerId.LISTEN_STREAM_IDLE,
          TimerId.HEALTH_CHECK_TIMEOUT,
          listener);
    }

    @Override
    public void onFirst(String change) {}

    @Override
    public void onNext(String change) {}

    public void write(String message) {
      writeRequest(message);
    }
  }

  @Test
  public void writeRequest_whenCallAlreadyCancelled_doesNotThrow() throws Exception {
    AsyncQueue asyncQueue = new AsyncQueue();
    FirestoreChannel channel = Mockito.mock(FirestoreChannel.class);
    StreamCallback callback = Mockito.mock(StreamCallback.class);

    TestStream stream = new TestStream(channel, asyncQueue, callback);

    @SuppressWarnings("unchecked")
    ClientCall<String, String> mockCall = Mockito.mock(ClientCall.class);

    // Simulate gRPC ClientCallImpl.sendMessageInternal() throwing on cancelled call
    Mockito.doThrow(new IllegalStateException("call was cancelled"))
        .when(mockCall)
        .sendMessage(Mockito.any());

    Field callField = AbstractStream.class.getDeclaredField("call");
    callField.setAccessible(true);
    callField.set(stream, mockCall);

    Field stateField = AbstractStream.class.getDeclaredField("state");
    stateField.setAccessible(true);
    stateField.set(stream, AbstractStream.State.Open);

    // writeRequest() catches the IllegalStateException, logs it, and does not throw.
    asyncQueue.runSync(() -> stream.write("test-message"));
  }

  @Test
  public void writeRequest_whenOtherIllegalStateException_rethrows() throws Exception {
    AsyncQueue asyncQueue = new AsyncQueue();
    FirestoreChannel channel = Mockito.mock(FirestoreChannel.class);
    StreamCallback callback = Mockito.mock(StreamCallback.class);

    TestStream stream = new TestStream(channel, asyncQueue, callback);

    @SuppressWarnings("unchecked")
    ClientCall<String, String> mockCall = Mockito.mock(ClientCall.class);

    // Simulate unexpected IllegalStateException (for example, invalid internal state)
    Mockito.doThrow(new IllegalStateException("unrelated stream state error"))
        .when(mockCall)
        .sendMessage(Mockito.any());

    Field callField = AbstractStream.class.getDeclaredField("call");
    callField.setAccessible(true);
    callField.set(stream, mockCall);

    Field stateField = AbstractStream.class.getDeclaredField("state");
    stateField.setAccessible(true);
    stateField.set(stream, AbstractStream.State.Open);

    // Non-cancellation IllegalStateException must be re-thrown by writeRequest().
    assertThrows(
        RuntimeException.class, () -> asyncQueue.runSync(() -> stream.write("test-message")));
  }
}
