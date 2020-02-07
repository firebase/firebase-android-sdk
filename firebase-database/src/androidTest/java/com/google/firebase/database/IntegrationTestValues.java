// Copyright 2018 Google LLC
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

package com.google.firebase.database;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;

public class IntegrationTestValues {
  private static final String TEST_ALT_NAMESPACE = "https://test.firebaseio.com";
  private static final String TEST_SERVER = "firebaseio.com";
  private static final long TEST_TIMEOUT = 70 * 1000;

  private IntegrationTestValues() {}

  public static String getNamespace() {
    Context c = InstrumentationRegistry.getInstrumentation().getContext();
    return c.getResources().getString(R.string.firebase_database_url);
  }

  public static String getAltNamespace() {
    return TEST_ALT_NAMESPACE;
  }

  public static String getProjectId() {
    Context c = InstrumentationRegistry.getInstrumentation().getContext();
    return c.getResources().getString(R.string.project_id);
  }

  public static long getTimeout() {
    return TEST_TIMEOUT;
  }

  public static String getServer() {
    return TEST_SERVER;
  }
}
