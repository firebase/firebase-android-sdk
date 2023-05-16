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
import com.google.firebase.FirebaseException;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;

public class TestInteropAppCheckTokenProvider implements InteropAppCheckTokenProvider {
  private final AppCheckTokenResult testToken;
  private final AppCheckTokenResult testLimitedUseToken;

  public TestInteropAppCheckTokenProvider(String testToken, String testLimitedUseToken) {
    this.testToken =
        new AppCheckTokenResult() {
          @NonNull
          @Override
          public String getToken() {
            return testToken;
          }

          @Nullable
          @Override
          public Exception getError() {
            return null;
          }
        };

    this.testLimitedUseToken =
        new AppCheckTokenResult() {
          @NonNull
          @Override
          public String getToken() {
            return testLimitedUseToken;
          }

          @Nullable
          @Override
          public Exception getError() {
            return null;
          }
        };
  }

  public TestInteropAppCheckTokenProvider(
      String testToken, String testLimitedUseToken, String error) {
    this.testToken =
        new AppCheckTokenResult() {
          @NonNull
          @Override
          public String getToken() {
            return testToken;
          }

          @Nullable
          @Override
          public Exception getError() {
            return new FirebaseException(error);
          }
        };
    this.testLimitedUseToken =
        new AppCheckTokenResult() {
          @NonNull
          @Override
          public String getToken() {
            return testLimitedUseToken;
          }

          @Nullable
          @Override
          public Exception getError() {
            return new FirebaseException(error);
          }
        };
  }

  @NonNull
  @Override
  public Task<AppCheckTokenResult> getToken(boolean forceRefresh) {
    return Tasks.forResult(testToken);
  }

  @NonNull
  @Override
  public Task<AppCheckTokenResult> getLimitedUseToken() {
    return Tasks.forResult(testLimitedUseToken);
  }

  @Override
  public void addAppCheckTokenListener(@NonNull AppCheckTokenListener listener) {}

  @Override
  public void removeAppCheckTokenListener(@NonNull AppCheckTokenListener listener) {}
}
