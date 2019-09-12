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

package com.google.firebase.storage;

import android.os.Build;
import com.google.android.gms.tasks.Task;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link FirebaseStorage}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class DependencyTest {

  @Rule public RetryRule retryRule = new RetryRule(3);

  String expected =
      "addOnCanceledListener\n"
          + "addOnCanceledListener\n"
          + "addOnCanceledListener\n"
          + "addOnCompleteListener\n"
          + "addOnCompleteListener\n"
          + "addOnCompleteListener\n"
          + "addOnFailureListener\n"
          + "addOnFailureListener\n"
          + "addOnFailureListener\n"
          + "addOnSuccessListener\n"
          + "addOnSuccessListener\n"
          + "addOnSuccessListener\n"
          + "continueWith\n"
          + "continueWith\n"
          + "continueWithTask\n"
          + "continueWithTask\n"
          + "equals\n"
          + "getClass\n"
          + "getException\n"
          + "getResult\n"
          + "getResult\n"
          + "hashCode\n"
          + "isCanceled\n"
          + "isComplete\n"
          + "isSuccessful\n"
          + "notify\n"
          + "notifyAll\n"
          + "onSuccessTask\n"
          + "onSuccessTask\n"
          + "toString\n"
          + "wait\n"
          + "wait\n"
          + "wait\n";

  /**
   * If this test fails, its because you added a new method/overload to Task and you need to let
   * someone in Firebase Storage know. Otherwise users will see NotImplementedException on these new
   * methods for Storage Tasks. Please contact benwu@ for more info.
   */
  @Test
  public void catchNewTaskMethods() {
    StringBuilder builder = new StringBuilder();

    try {
      Class<?> c = Task.class;
      Method[] m = c.getMethods();
      List<String> strings = new ArrayList<>();
      for (Method method : m) {
        strings.add(method.getName());
      }
      Collections.sort(strings);
      for (String s : strings) {
        builder.append(s).append("\n");
      }
    } catch (Throwable e) {
      System.err.println(e);
    }
    String newValue = builder.toString();
    if (!expected.equals(newValue)) {
      System.err.println("Expected:\n" + expected + "\nBut got:\n" + newValue);
    }
    Assert.assertEquals(expected, newValue);
  }
}
