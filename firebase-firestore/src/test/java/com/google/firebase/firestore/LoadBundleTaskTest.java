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

package com.google.firebase.firestore;

import static org.junit.Assert.fail;

import com.google.android.gms.tasks.Task;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class LoadBundleTaskTest {
  @Test
  public void testImplementsAllTaskInterface() {
    for (Method method : Task.class.getDeclaredMethods()) {
      try {
        LoadBundleTask.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException e) {
        fail(
            "'LoadBundleTask' is expected to override all methods in 'Task', but it is missing "
                + method.toGenericString());
      }
    }
  }
}
