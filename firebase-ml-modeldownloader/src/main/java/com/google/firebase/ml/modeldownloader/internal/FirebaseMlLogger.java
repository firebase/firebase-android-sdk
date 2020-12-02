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

package com.google.firebase.ml.modeldownloader.internal;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.FirebaseApp;

public class FirebaseMlLogger {

  private final SharedPreferencesUtil sharedPreferencesUtil;

  public FirebaseMlLogger(@NonNull FirebaseApp firebaseApp) {
    this.sharedPreferencesUtil = new SharedPreferencesUtil(firebaseApp);
  }

  @VisibleForTesting
  FirebaseMlLogger(SharedPreferencesUtil sharedPreferencesUtil) {
    this.sharedPreferencesUtil = sharedPreferencesUtil;
  }

  //  /** Log that a notification was received by the client app. */
  //  public static void logNotificationReceived(Intent intent) {
  //
  //    if (!sharedPreferencesUtil.getCustomModelStatsCollectionFlag()) {
  //      Log.d("Logging is disabled.")
  //    }
  //
  //    if (MessagingAnalytics.shouldUploadFirelogAnalytics(intent)) {
  //      TransportFactory transportFactory = FirebaseMessaging.getTransportFactory();
  //
  //      if (transportFactory != null) {
  //        Transport<String> transport =
  //            transportFactory.getTransport(
  //                FirelogAnalytics.FCM_LOG_SOURCE,
  //                String.class,
  //                Encoding.of("json"),
  //                String::getBytes);
  //        logToFirelog(EventType.MESSAGE_DELIVERED, intent, transport);
  //      } else {
  //        Log.e(
  //            TAG, "TransportFactory is null. Skip exporting message delivery metrics to Big
  // Query");
  //      }
  //    }
  //  }

}
