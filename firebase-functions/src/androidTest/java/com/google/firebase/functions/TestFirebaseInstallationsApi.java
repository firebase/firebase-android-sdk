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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.installations.internal.FidListener;
import com.google.firebase.installations.internal.FidListenerHandle;

public class TestFirebaseInstallationsApi implements FirebaseInstallationsApi {
  private final String testToken;

  public TestFirebaseInstallationsApi(String testToken) {
    this.testToken = testToken;
  }

  @Override
  public Task<String> getId() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Task<InstallationTokenResult> getToken(boolean forceRefresh) {
    return Tasks.forResult(new TestInstallationTokenResult(testToken));
  }

  @NonNull
  @Override
  public Task<Void> delete() {
    return null;
  }

  @Override
  public FidListenerHandle registerFidListener(@NonNull FidListener listener) {
    return null;
  }
}
