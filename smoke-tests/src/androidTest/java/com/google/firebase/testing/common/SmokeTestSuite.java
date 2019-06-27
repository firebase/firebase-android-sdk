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

package com.google.firebase.testing.common;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/**
 * A runner for smoke tests.
 *
 * <p>This is a JUnit runner inspired by the {@code Suite} runner from JUnit. That runner uses an
 * annotation to determine the classes in the suite. This runner, on the other hand, uses metadata
 * in the application's Android manifest. Therefore, a single test class can be reused with many
 * different test suites by simply changing the manifest.
 *
 * <p>The application's manifest must include metadata with the name {@code
 * com.google.firebase.testing.classes} or else this run with fail to run. This item is expected to
 * be a comma-separated list of class names. These class names must be valid, loadable JUnit test
 * classes in the application APK.
 */
public class SmokeTestSuite extends ParentRunner<Runner> {

  private static final String TAG = "SmokeTestSuite";
  private static final String TEST_CLASSES_KEY = "com.google.firebase.testing.classes";

  private final List<Runner> runners;

  public SmokeTestSuite(Class<?> clazz, RunnerBuilder builder) throws InitializationError {
    super(clazz);
    this.runners = builder.runners(clazz, getTestClasses());
  }

  @Override
  protected Description describeChild(Runner runner) {
    return runner.getDescription();
  }

  @Override
  protected List<Runner> getChildren() {
    return runners;
  }

  @Override
  protected void runChild(Runner runner, RunNotifier notifier) {
    runner.run(notifier);
  }

  private static List<Class<?>> getTestClasses() throws InitializationError {
    Context ctx = ApplicationProvider.getApplicationContext();
    return getTestClasses(ctx);
  }

  private static List<Class<?>> getTestClasses(Context ctx) throws InitializationError {
    List<String> names = getTestClassNames(ctx);
    ArrayList<Class<?>> classes = new ArrayList<>(names.size());

    try {
      for (String name : names) {
        Class<?> c = Class.forName(name);
        classes.add(c);
      }
    } catch (ClassNotFoundException ex) {
      throw new InitializationError(ex);
    }

    return classes;
  }

  private static List<String> getTestClassNames(Context ctx) throws InitializationError {
    String name = ctx.getPackageName();
    try {
      PackageManager pm = ctx.getPackageManager();
      ApplicationInfo ai = pm.getApplicationInfo(name, PackageManager.GET_META_DATA);
      String names = ai.metaData.getString(TEST_CLASSES_KEY);

      if (names == null) {
        throw new InitializationError("No test classes found in Application Manifest");
      }

      return Arrays.asList(names.split(","));
    } catch (PackageManager.NameNotFoundException ex) {
      throw new InitializationError(ex);
    }
  }
}
