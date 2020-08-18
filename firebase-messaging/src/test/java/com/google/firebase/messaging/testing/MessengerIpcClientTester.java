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
package com.google.firebase.messaging.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import com.google.firebase.iid.FirebaseIidMessengerCompat;
import com.google.firebase.iid.MessengerIpcClient;
import com.google.firebase.iid.MessengerIpcClient.What;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPausedLooper;

/**
 * Utility for testing code that uses {@link MessengerIpcClient} in robolectric tests.
 *
 * <p>This returns a mock binder when {@link MessengerIpcClient} tries to bind to GmsCore. Messages
 * sent from the client can be retrieved or asserted on, and "canned" responses can be set for
 * specific what integers.
 */
public final class MessengerIpcClientTester {

  private static final String TAG = "MessengerIpcClientTr";

  public static final String ACTION_REGISTER = "com.google.android.c2dm.intent.REGISTER";
  public static final String PACKAGE_GMS = "com.google.android.gms";

  // Keys in the data bundle of the message
  public static final String KEY_ONE_WAY = "oneWay";
  public static final String KEY_PACKAGE = "pkg";
  public static final String KEY_DATA = "data";

  private static final int TIMEOUT_S = 10;

  /** Request sent to GmsCore via {@link MessengerIpcClient}. */
  public static class Request {

    public final int what;
    public final boolean oneWay;
    public final String packageName;
    public final int requestId;
    public final Bundle data;
    public final Messenger messenger;

    private Request(Message msg) {
      Bundle messageData = msg.getData();
      this.what = msg.what;
      this.oneWay = messageData.getBoolean(KEY_ONE_WAY, false);
      this.packageName = messageData.getString(KEY_PACKAGE);
      this.requestId = msg.arg1;
      this.data = messageData.getBundle(KEY_DATA);
      this.messenger = msg.replyTo;
    }

    /** Simulate GmsCore responding to this request with {@code responseData}. */
    public void respond(Bundle responseData) {
      Bundle messageData = new Bundle();
      messageData.putBundle(KEY_DATA, responseData);
      respondRaw(messageData);
    }

    /** Simulate GmsCore responding to this request that the {@code what} integer is unsupported. */
    public void respondAsUnsupported() {
      respondRaw(Bundles.of("unsupported", true));
    }

    /** Simulate GmsCore acking the request. */
    public void sendAck() {
      assertThat(oneWay).isTrue(); // Acks are only sent to one way requests
      respondRaw(Bundles.of("ack", true));
    }

    private void respondRaw(Bundle messageData) {
      Log.i(TAG, "Responding to request what=" + what + " id=" + requestId + ": " + messageData);
      try {
        Message responseMessage = Message.obtain();
        responseMessage.what = what;
        responseMessage.arg1 = requestId;
        responseMessage.setData(messageData);

        messenger.send(responseMessage);
        ShadowPausedLooper.idleMainLooper();
      } catch (RemoteException e) {
        // Shouldn't happen
        Log.e(TAG, "Responding to request failed", e);
        fail("Error responding to request: " + e.getMessage());
      }
    }
  }

  private final IBinder gmsCoreMessengerBinder;

  /**
   * Queue of messages sent by the client.
   *
   * <p>Messages that had a canned response are not added to this queue.
   */
  private final BlockingQueue<Request> sentRequests = new LinkedBlockingQueue<>();

  private boolean ackOneWayRequests = true;

  /** When requests comes for these whats, an unsupported response is sent. */
  private final Set<Integer> unsupportedWhats = new HashSet<>();

  /**
   * Map from what -> response data for "canned" responses that are automatically returned each time
   * a message with the corresponding what is sent.
   */
  private final Map<Integer, Bundle> cannedResponses = new HashMap<>();

  // This always uses MessengerCompat Robolectric messes with the regular Messenger class
  public static MessengerIpcClientTester createWithFirebaseIidMessengerCompat() {
    return new MessengerIpcClientTester(
        handler -> new FirebaseIidMessengerCompat(handler).getBinder());
  }

  public MessengerIpcClientTester(Function<Handler, IBinder> messengerCompatBinderFactory) {
    gmsCoreMessengerBinder = messengerCompatBinderFactory.apply(new Handler(this::handleMessage));
    setRegisterBinder(gmsCoreMessengerBinder);
  }

  public void setAckOneWayRequests(boolean ackOneWayRequests) {
    this.ackOneWayRequests = ackOneWayRequests;
  }

  private boolean handleMessage(Message message) {
    Log.i(TAG, "Handling message: " + message);
    handleRequest(new Request(message));
    return true; // Finished handling message
  }

  private void handleRequest(Request request) {
    sentRequests.add(request);

    if (unsupportedWhats.contains(request.what)) {
      request.respondAsUnsupported();
      return;
    }
    if (request.oneWay) {
      if (ackOneWayRequests) {
        request.sendAck();
      }
    } else {
      // Not one way
      Bundle cannedResponse = cannedResponses.get(request.what);
      if (cannedResponse != null) {
        request.respond(cannedResponse);
      }
    }
  }

  /** Gets the most recently sent request. */
  public Request getSentRequest() {
    return sentRequests.poll();
  }

  /**
   * Polls the most recently sent request over a real {@link MessengerIpcClient} instance with a
   * default timeout.
   *
   * <p>The real instance uses an internal executor for running the send so a timeout is needed to
   * ensure it has a chance to run.
   */
  public Request pollSentRequestWithTimeout() throws InterruptedException {
    return pollSentRequestWithTimeout(TIMEOUT_S, TimeUnit.SECONDS);
  }

  /**
   * Polls the most recently sent request over a real {@link MessengerIpcClient} instance with the
   * provided timeout.
   *
   * <p>The real instance uses an internal executor for running the send so a timeout is needed to
   * ensure it has a chance to run.
   */
  public Request pollSentRequestWithTimeout(int time, TimeUnit unit) throws InterruptedException {
    // This is needed for the bind to succeed in FirebaseMessagingServiceRoboTest
    shadowOf(Looper.getMainLooper()).idleIfPaused();

    return sentRequests.poll(time, unit);
  }

  /** Sets a canned or automatic response each time a message is sent with this {@code what}. */
  public void setCannedResponse(@What int what, Bundle response) {
    cannedResponses.put(what, response);
  }

  /**
   * Sets the given {@code what} as unsupported.
   *
   * <p>When a request is made with this {@code what} an unsupported error will be returned to the
   * caller.
   */
  public void setUnsupported(@What int what) {
    unsupportedWhats.add(what);
  }

  /**
   * Sets the binder that will be returned when binding to the register intent action on GmsCore.
   */
  public static void setRegisterBinder(IBinder binder) {
    Application application = RuntimeEnvironment.application;
    Intent expectedBindingIntent = new Intent(ACTION_REGISTER);
    expectedBindingIntent.setPackage(PACKAGE_GMS);
    shadowOf(application)
        .setComponentNameAndServiceForBindServiceForIntent(
            expectedBindingIntent, new ComponentName(application, "TestRegistrarService"), binder);
  }

  /**
   * Set up {@code mockContext} so that an outgoing bind from {@link MessengerIpcClient} will get
   * the binder of this tester instance.
   *
   * <p>This is useful for tests that are executing the request in the background, so can't easily
   * run the main looper once the bind has been initiated.
   */
  public void autoConnectUsingMockContext(Context mockContext) {
    autoConnectUsingMockContext(mockContext, gmsCoreMessengerBinder);
  }

  /**
   * Set up {@code mockContext} so that an outgoing bind from {@link MessengerIpcClient} will get
   * the given {@code binder}.
   *
   * <p>This is useful for tests that are executing the request in the background, so can't easily
   * run the main looper once the bind has been initiated.
   */
  public static void autoConnectUsingMockContext(Context mockContext, IBinder binder) {
    Intent expectedBindingIntent = new Intent(ACTION_REGISTER);
    expectedBindingIntent.setPackage(PACKAGE_GMS);

    doAnswer(
            invocation -> {
              // We don't want to connect the service connection before onBind has returned, so do
              // it after a short delay.
              ServiceConnection serviceConnection =
                  (ServiceConnection) invocation.getArguments()[1];
              Future<?> unused =
                  Executors.newSingleThreadScheduledExecutor()
                      .schedule(
                          () -> {
                            serviceConnection.onServiceConnected(
                                new ComponentName(mockContext, "TestRegistrarService"), binder);
                          },
                          10,
                          TimeUnit.MILLISECONDS);

              return true;
            })
        .when(mockContext)
        .bindService(any(Intent.class), any(ServiceConnection.class), /* flags= */ anyInt());
  }
}
