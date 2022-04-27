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
import android.content.Intent;
import android.util.Log;
import androidx.legacy.content.WakefulBroadcastReceiver;
import com.google.android.gms.common.util.concurrent.NamedThreadFactory;
import com.google.firebase.iid.FcmBroadcastProcessor;
import com.google.firebase.iid.ServiceStarter;
import com.google.firebase.messaging.directboot.threads.PoolableExecutors;
import com.google.firebase.messaging.directboot.threads.ThreadPriority;
import java.util.concurrent.ExecutorService;

/**
 * WakefulBroadcastReceiver that receives FirebaseMessaging events and delivers them to the
 * application-specific {@link com.google.firebase.iid.FirebaseInstanceIdService} subclass in direct
 * boot mode.
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
public final class FirebaseMessagingDirectBootReceiver extends WakefulBroadcastReceiver {

  /** TAG for log statements coming from FCM */
  static final String TAG = "FCM";

  /** Action for FCM direct boot message intents */
  private static final String ACTION_DIRECT_BOOT_REMOTE_INTENT =
      "com.google.firebase.messaging.RECEIVE_DIRECT_BOOT";

  /** All broadcasts get processed on this executor. */
  private final ExecutorService processorExecutor =
      PoolableExecutors.factory()
          .newSingleThreadExecutor(
              new NamedThreadFactory("fcm-db-intent-handle"), ThreadPriority.LOW_POWER);

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }
    if (!ACTION_DIRECT_BOOT_REMOTE_INTENT.equals(intent.getAction())) {
      Log.d(TAG, "Unexpected intent: " + intent.getAction());
      return;
    }

    // Just pass the intent to the service mostly unchanged.
    // Clear the component and ensure package name is set so that the standard dispatching
    // mechanism will find the right service in the app.
    intent.setComponent(null);
    intent.setPackage(context.getPackageName());

    // We don't actually want to process this broadcast on the main thread, so we're going to use
    // goAsync to deal with this in the background. Unfortunately, we need to check whether the
    // broadcast was ordered (and thus needs a result) before calling goAsync, because once we've
    // called goAsync then isOrderedBroadcast will always return false.
    boolean needsResult = isOrderedBroadcast();
    PendingResult pendingBroadcastResult = goAsync();

    new FcmBroadcastProcessor(context, processorExecutor)
        .process(intent)
        .addOnCompleteListener(
            processorExecutor,
            resultCodeTask -> {
              // If we call setResultCode on a non-ordered broadcast it'll throw, so only set the
              // result if the broadcast was ordered
              if (needsResult) {
                pendingBroadcastResult.setResultCode(
                    resultCodeTask.isSuccessful()
                        ? resultCodeTask.getResult()
                        : ServiceStarter.ERROR_UNKNOWN);
              }
              pendingBroadcastResult.finish();
            });
  }
}
