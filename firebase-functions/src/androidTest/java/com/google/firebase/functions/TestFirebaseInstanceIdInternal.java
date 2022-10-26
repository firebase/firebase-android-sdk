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

package com.google.firebase.functions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import java.io.IOException;

public class TestFirebaseInstanceIdInternal implements FirebaseInstanceIdInternal {
  private final String testToken;

  public TestFirebaseInstanceIdInternal(String testToken) {
    this.testToken = testToken;
  }

  @Override
  public String getId() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getToken() {
    return testToken;
  }

  @NonNull
  @Override
  public Task<String> getTokenTask() {
    return Tasks.forResult(testToken);
  }

  @Override
  public void deleteToken(@NonNull String s, @NonNull String s1) throws IOException {
    // No-op: We're not using this method in our tests
  }

  @Override
  public void addNewTokenListener(NewTokenListener newTokenListener) {
    // No-op: We're not using this method in our tests
  }
}
