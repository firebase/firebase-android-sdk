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
package com.google.firebase.iid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FcmBroadcastProcessor;
import com.google.firebase.messaging.ServiceStarter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Robolectric test for the FirebaseInstanceIdInternalReceiver. The tests require the KITKAT sdk
 * configuration to mimic the behaviour from piper.
 */
@RunWith(RobolectricTestRunner.class)
public class FirebaseInstanceIdWithFcmReceiverRoboTest {

  private static final String ACTION_FCM_MESSAGE = "com.google.android.c2dm.intent.RECEIVE";

  private static final int ERROR_ILLEGAL_STATE_EXCEPTION = 402;

  private Application context;
  @Mock private ServiceStarter serviceStarter;
  private FirebaseInstanceIdReceiver receiver;
  @Captor private ArgumentCaptor<Intent> intentCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    context = ApplicationProvider.getApplicationContext();
    ServiceStarter.setForTesting(serviceStarter);
    receiver = new FirebaseInstanceIdReceiver();
    context.registerReceiver(receiver, new IntentFilter(ACTION_FCM_MESSAGE));
    context.getApplicationInfo().targetSdkVersion = VERSION_CODES.N_MR1;
  }

  @After
  public void resetStaticState() {
    FirebaseApp.clearInstancesForTest();
    ServiceStarter.setForTesting(null);
    FcmBroadcastProcessor.reset();
  }

  private static Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e1) {
      try {
        Method getDeclaredFields0 =
            Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
        getDeclaredFields0.setAccessible(true);
        Field[] fields = (Field[]) getDeclaredFields0.invoke(clazz, false);
        for (Field f : fields) {
          if (f.getName().equals(name)) {
            return f;
          }
        }
        throw new NoSuchFieldException(name);
      } catch (NoSuchMethodException e2) {
        throw new NoSuchFieldException(name);
      } catch (IllegalAccessException e2) {
        throw new NoSuchFieldException(name);
      } catch (InvocationTargetException e2) {
        throw new NoSuchFieldException(name);
      }
    }
  }

  @Test
  public void testNullIntent() throws Exception {
    receiver.onReceive(context, null);

    verifyNoInteractions(serviceStarter);
  }

  @Test
  public void testNoWrappedIntent() throws Exception {
    Intent intent = new Intent(ACTION_FCM_MESSAGE).putExtra("key", "value");

    sendOrderedBroadcastBlocking(intent);
    verify(serviceStarter, atLeastOnce())
        .startMessagingService(any(Context.class), intentCaptor.capture());
    assertThat(intentCaptor.getValue()).isSameInstanceAs(intent);
  }

  @Test
  @Config(maxSdk = VERSION_CODES.N_MR1)
  public void testStartsService_preO() throws Exception {
    Intent intent = new Intent(ACTION_FCM_MESSAGE).putExtra("key", "value");

    sendOrderedBroadcastBlocking(intent);

    verify(serviceStarter, atLeastOnce())
        .startMessagingService(nullable(Context.class), intentCaptor.capture());
    assertThat(intentCaptor.getValue()).isSameInstanceAs(intent);
    assertThat(shadowOf(context).getBoundServiceConnections()).isEmpty();
  }

  @Test
  @Config(maxSdk = VERSION_CODES.N_MR1)
  public void testStartsService_notOButTargetingO() throws Exception {
    context.getApplicationInfo().targetSdkVersion = VERSION_CODES.O;

    Intent intent = new Intent(ACTION_FCM_MESSAGE).putExtra("key", "value");
    sendOrderedBroadcastBlocking(intent);

    verify(serviceStarter, atLeastOnce())
        .startMessagingService(nullable(Context.class), intentCaptor.capture());
    assertThat(intentCaptor.getValue()).isSameInstanceAs(intent);
    assertThat(shadowOf(context).getBoundServiceConnections()).isEmpty();
  }

  private void sendOrderedBroadcastBlocking(Intent intent) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    context.sendOrderedBroadcast(
        intent,
        /* receiverPermission= */ null,
        /* resultReceiver= */ new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            latch.countDown();
          }
        },
        /* scheduler= */ null,
        /* initialCode= */ 0,
        /* initialData= */ null,
        /* initialExtras= */ null);

    ShadowLooper.runMainLooperOneTask();

    ShadowLooper.runMainLooperOneTask();

    latch.await(5, TimeUnit.SECONDS);
  }
}
