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
package com.google.firebase.messaging.directboot;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.google.android.gms.cloudmessaging.CloudMessage;
import com.google.android.gms.cloudmessaging.CloudMessagingReceiver;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FcmBroadcastProcessor;
import com.google.firebase.messaging.ServiceStarter;
import java.util.concurrent.ExecutionException;

/**
 * BroadcastReceiver that receives FirebaseMessaging events and delivers them to the
 * application-specific {@link com.google.firebase.messaging.FirebaseMessagingService} subclass in
 * direct boot mode.
 *
 * <p>This receiver is automatically added to your application's manifest file via manifest merge.
 * If necessary it can be manually declared via:
 *
 * <pre>
 * {@literal
 * <receiver
 *     android:name="com.google.firebase.messaging.directboot.FirebaseMessagingDirectBootReceiver"
 *     android:directBootAware="true"
 *     android:exported="true"
 *     android:permission="com.google.android.c2dm.permission.SEND" >
 *     <intent-filter>
 *         <action android:name="com.google.firebase.messaging.RECEIVE_DIRECT_BOOT" />
 *     </intent-filter>
 * </receiver>}</pre>
 *
 * <p>The {@code com.google.android.c2dm.permission.SEND} permission is held by Google Play
 * services. This prevents other apps from invoking the broadcast receiver.
 *
 * @hide
 */
public final class FirebaseMessagingDirectBootReceiver extends CloudMessagingReceiver {

  /** TAG for log statements coming from FCM */
  static final String TAG = "FCM";

  /** @hide */
  @Override
  @WorkerThread
  protected int onMessageReceive(@NonNull Context context, @NonNull CloudMessage message) {
    try {
      return Tasks.await(new FcmBroadcastProcessor(context).process(message.getIntent()));
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to send message to service.", e);
      return ServiceStarter.ERROR_UNKNOWN;
    }
  }
}
