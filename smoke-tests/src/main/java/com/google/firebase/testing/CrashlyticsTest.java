// Copyright 2021 Google LLC
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

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CrashlyticsTest {

  @Test
  public void analyticsIntegration() {
    // Validates that Firebase Analytics and Crashlytics interoperability is working, by confirming
    // that events sent to Firebase Analytics are received by the Crashlytics breadcrumb handler.
    try {
      // need to use reflection to get the breadcrumb handler.
      FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
      Field coreField = crashlytics.getClass().getDeclaredField("core");
      coreField.setAccessible(true);
      Object core = coreField.get(crashlytics);
      Field breadcrumbSourceField = core.getClass().getDeclaredField("breadcrumbSource");
      breadcrumbSourceField.setAccessible(true);
      BreadcrumbSource breadcrumbSource = (BreadcrumbSource)breadcrumbSourceField.get(core);

      final CountDownLatch eventReceivedLatch = new CountDownLatch(1);
      breadcrumbSource.registerBreadcrumbHandler(breadcrumbHandler -> {
        eventReceivedLatch.countDown();
      });

      Bundle eventBundle = new Bundle();
      eventBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "testName");
      FirebaseAnalytics.getInstance(ApplicationProvider.getApplicationContext()).logEvent(
          FirebaseAnalytics.Event.APP_OPEN, eventBundle);

      // Wait up to 2 seconds, which is plenty of time for the event
      eventReceivedLatch.await(2000, TimeUnit.MILLISECONDS);
      assertThat(eventReceivedLatch.getCount()).isEqualTo(0);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void setCustomKeys() {
    // For now, simply validate that the API does not throw any exception. A more robust functional
    // test could be implemented via reflection or monitoring logcat.
    FirebaseCrashlytics.getInstance().setCustomKey("TestKey", "TestValue");
  }

  @Test
  public void log() {
    // For now, simply validate that the API does not throw any exception. A more robust functional
    // test could be implemented via reflection or monitoring logcat.
    FirebaseCrashlytics.getInstance().log("This is a log message");
  }

  @Test
  public void didCrashOnPreviousExecution() {
    assertThat(FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()).isFalse();
  }
}

