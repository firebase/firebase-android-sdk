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

package com.google.firebase.appcheck.debug.testing;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactoryHelper;
import com.google.firebase.appcheck.internal.DefaultFirebaseAppCheck;

/**
 * Helper class for using {@link DebugAppCheckProviderFactory} in integration tests.
 *
 * <p>Example Usage:
 *
 * <pre>{@code
 * @RunWith(AndroidJunit4.class)
 * public class MyTests {
 *   private final DebugAppCheckTestHelper debugAppCheckTestHelper =
 *       DebugAppCheckTestHelper.fromInstrumentationArgs();
 *
 *   @Test
 *   public testWithDefaultApp() {
 *     debugAppCheckTestHelper.withDebugProvider(() -> {
 *       // Test code that requires a debug AppCheckToken
 *     });
 *   }
 *
 *   @Test
 *   public testWithNonDefaultApp() {
 *     debugAppCheckTestHelper.withDebugProvider(
 *         FirebaseApp.getInstance("nonDefaultApp"),
 *         () -> {
 *           // Test code that requires a debug AppCheckToken
 *         });
 *   }
 * }
 * }</pre>
 *
 * <pre>{@code
 * // In build.gradle.kts
 * android {
 *   defaultConfig {
 *     System.getenv("FIREBASE_APP_CHECK_DEBUG_SECRET")?.let { token ->
 *       testInstrumentationRunnerArguments(
 *           mapOf("firebaseAppCheckDebugSecret" to token))
 *     }
 *   }
 * }
 * }</pre>
 */
public final class DebugAppCheckTestHelper {
  private static final String DEBUG_SECRET_KEY = "firebaseAppCheckDebugSecret";

  private final String debugSecret;

  /**
   * Create a {@link DebugAppCheckTestHelper} instance with the debug secret obtained from {@link
   * InstrumentationRegistry} arguments.
   */
  @NonNull
  public static DebugAppCheckTestHelper fromInstrumentationArgs() {
    String debugSecret = InstrumentationRegistry.getArguments().getString(DEBUG_SECRET_KEY);
    return new DebugAppCheckTestHelper(debugSecret);
  }

  @VisibleForTesting
  static DebugAppCheckTestHelper fromString(String debugSecret) {
    return new DebugAppCheckTestHelper(debugSecret);
  }

  private DebugAppCheckTestHelper(String debugSecret) {
    this.debugSecret = debugSecret;
  }

  /**
   * Installs a {@link DebugAppCheckProviderFactory} to the default {@link FirebaseApp} and runs the
   * test code in {@code runnable}.
   */
  public <E extends Throwable> void withDebugProvider(@NonNull MaybeThrowingRunnable<E> runnable)
      throws E {
    FirebaseApp firebaseApp = FirebaseApp.getInstance();
    withDebugProvider(firebaseApp, runnable);
  }

  /**
   * Installs a {@link DebugAppCheckProviderFactory} to the provided {@link FirebaseApp} and runs
   * the test code in {@code runnable}.
   */
  public <E extends Throwable> void withDebugProvider(
      @NonNull FirebaseApp firebaseApp, @NonNull MaybeThrowingRunnable<E> runnable) throws E {
    DefaultFirebaseAppCheck firebaseAppCheck =
        (DefaultFirebaseAppCheck) FirebaseAppCheck.getInstance(firebaseApp);
    AppCheckProviderFactory currentAppCheckProviderFactory =
        firebaseAppCheck.getInstalledAppCheckProviderFactory();
    firebaseAppCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactoryHelper.createDebugAppCheckProviderFactory(debugSecret));
    try {
      runnable.run();
    } catch (Throwable throwable) {
      E e = (E) throwable;
      throw e;
    } finally {
      firebaseAppCheck.resetAppCheckState();
      // Restore the previous AppCheckProviderFactory
      if (currentAppCheckProviderFactory != null) {
        firebaseAppCheck.installAppCheckProviderFactory(currentAppCheckProviderFactory);
      }
    }
  }

  public interface MaybeThrowingRunnable<E extends Throwable> {
    void run() throws E;
  }
}
