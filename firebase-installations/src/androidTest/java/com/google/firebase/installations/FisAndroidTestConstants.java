// Copyright 2019 Google LLC
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

package com.google.firebase.installations;

import com.google.firebase.installations.local.PersistedInstallationEntry;

public final class FisAndroidTestConstants {
  public static final String TEST_FID_1 = "cccccccccccccccccccccc";

  public static final String TEST_AUTH_TOKEN = "fis.auth.token";

  public static final String TEST_REFRESH_TOKEN = "1:test-refresh-token";

  public static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  public static final String TEST_APP_ID_2 = "1:987654321:android:abcdef";

  public static final long TEST_TOKEN_EXPIRATION_TIMESTAMP = 4000L;

  public static final long TEST_CREATION_TIMESTAMP_1 = 2000L;
  public static final long TEST_CREATION_TIMESTAMP_2 = 2L;

  public static final PersistedInstallationEntry DEFAULT_PERSISTED_INSTALLATION_ENTRY =
      PersistedInstallationEntry.builder().build();
}
