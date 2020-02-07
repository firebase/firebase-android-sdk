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

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.testing.common.Tasks2;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Functions smoke tests. */
@RunWith(AndroidJUnit4.class)
public final class FunctionsTest {

  @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

  @Test
  public void callFakeFunctionShouldFail() throws Exception {
    FirebaseFunctions functions = FirebaseFunctions.getInstance();
    Task<HttpsCallableResult> task = functions.getHttpsCallable("clearlyFake31").call();
    Tasks2.waitForFailure(task);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void callAddNumbersShouldReturnResult() throws Exception {
    FirebaseFunctions functions = FirebaseFunctions.getInstance();
    HashMap<String, Object> data = new HashMap<>();
    data.put("firstNumber", 13);
    data.put("secondNumber", 17);

    Task<HttpsCallableResult> task = functions.getHttpsCallable("addNumbers").call(data);
    HttpsCallableResult result = Tasks2.waitForSuccess(task);
    HashMap<String, Object> map = (HashMap<String, Object>) result.getData();

    assertThat(map.get("operationResult")).isEqualTo(30);
  }
}
