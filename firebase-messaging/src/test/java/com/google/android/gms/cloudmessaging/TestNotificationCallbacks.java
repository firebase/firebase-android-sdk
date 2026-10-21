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
package com.google.android.gms.cloudmessaging;

/**
 * Exposes {@link NotificationCallback}'s package-private constructor to tests outside this package
 * without referencing internal AIDL classes that are obfuscated in the published AAR.
 *
 * <p>This class is test-only and deliberately sits in the {@code cloudmessaging} package so that it
 * can reach the constructor. It is not part of the shipping SDK.
 */
public abstract class TestNotificationCallbacks extends NotificationCallback {

  protected TestNotificationCallbacks(String messageId) {
    super(messageId, null);
  }
}
