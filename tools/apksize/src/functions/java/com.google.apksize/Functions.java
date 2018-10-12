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

package com.google.apksize;

import android.content.Context;
import android.support.annotation.NonNull;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import java.util.HashMap;
import java.util.Map;

public class Functions implements SampleCode {
  private static final String TAG = "sizetest";

  @Override
  public void runSample(Context context) {
    FirebaseFunctions mFunctions = FirebaseFunctions.getInstance();
    Map<String, Object> data = new HashMap<>();
    data.put("firstNumber", 5);
    data.put("secondNumber", 6);
    // Call the function and extract the operation from the result
    mFunctions
        .getHttpsCallable("addNumbers")
        .call(data)
        .continueWith(
            new Continuation<HttpsCallableResult, Integer>() {
              @Override
              public Integer then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                // This continuation runs on either success or failure, but if the task
                // has failed then getResult() will throw an Exception which will be
                // propagated down.
                Map<String, Object> result = (Map<String, Object>) task.getResult().getData();
                return (Integer) result.get("operationResult");
              }
            });
  }
}
