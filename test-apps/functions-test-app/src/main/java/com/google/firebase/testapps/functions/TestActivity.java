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

package com.google.firebase.testapps.functions;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.widget.TextView;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import java.util.HashMap;
import java.util.Map;

public class TestActivity extends Activity {
  // Add message views
  private TextView sum;

  private FirebaseFunctions mFunctions;
  private CountingIdlingResource idlingResource =
      new CountingIdlingResource("Functions invocation");

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.test_activity);

    idlingResource.increment();

    sum = findViewById(R.id.sum);

    mFunctions = FirebaseFunctions.getInstance();

    Map<String, Object> data = new HashMap<>();
    data.put("firstNumber", 10);
    data.put("secondNumber", 11);

    // Call the function and extract the operation from the result
    mFunctions
        .getHttpsCallable("addNumbers")
        .call(data)
        .continueWith(
            new Continuation<HttpsCallableResult, Integer>() {
              @Override
              public Integer then(@NonNull Task<HttpsCallableResult> task) {
                Map<String, Object> result = (Map<String, Object>) task.getResult().getData();
                return (Integer) result.get("operationResult");
              }
            })
        .addOnSuccessListener(
            new OnSuccessListener<Integer>() {
              @Override
              public void onSuccess(Integer integer) {
                sum.setText(String.valueOf(integer));
              }
            })
        .addOnCompleteListener(
            new OnCompleteListener<Integer>() {
              @Override
              public void onComplete(@NonNull Task<Integer> task) {
                idlingResource.decrement();
              }
            });
  }

  @VisibleForTesting
  @NonNull
  @Keep
  public IdlingResource getIdlingResource() {
    return idlingResource;
  }
}
