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
package com.google.firebase.messaging;

import com.google.android.gms.cloudmessaging.internal.INotificationCallback;
import java.util.ArrayList;
import java.util.List;

/**
 * Records what Google Play services would have been told about a notification.
 *
 * <p>Events are recorded as {@code tag/id} strings, the pair the callbacks are keyed on. The
 * timestamp is not recorded: it comes from {@code SystemClock}.
 */
final class FakeNotificationCallback extends INotificationCallback.Stub {

  private final List<String> posted = new ArrayList<>();
  private final List<String> cancelled = new ArrayList<>();
  private final List<Integer> errorCodes = new ArrayList<>();
  private int successCount;

  @Override
  public void onNotificationPosted(String tag, int id, long elapsedRealtimeMs) {
    posted.add(key(tag, id));
  }

  @Override
  public void onNotificationCancelled(String tag, int id, long elapsedRealtimeMs) {
    cancelled.add(key(tag, id));
  }

  @Override
  public void onSuccess() {
    successCount++;
  }

  @Override
  public void onError(int errorCode) {
    errorCodes.add(errorCode);
  }

  /** Notifications reported as posted, as {@code tag/id}, in the order they were reported. */
  List<String> getPosted() {
    return posted;
  }

  /** Notifications reported as cancelled, as {@code tag/id}, in the order they were reported. */
  List<String> getCancelled() {
    return cancelled;
  }

  List<Integer> getErrorCodes() {
    return errorCodes;
  }

  int getSuccessCount() {
    return successCount;
  }

  private static String key(String tag, int id) {
    return tag + "/" + id;
  }
}
