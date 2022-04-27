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
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.IdTokenListener;
import com.google.firebase.auth.internal.InternalAuthProvider;
import java.util.Collections;

public class TestInternalAuthProvider implements InternalAuthProvider {
  private final MaybeThrowingSupplier<String, FirebaseException> testToken;

  public TestInternalAuthProvider(MaybeThrowingSupplier<String, FirebaseException> testToken) {
    this.testToken = testToken;
  }

  @Override
  public Task<GetTokenResult> getAccessToken(boolean b) {
    try {
      return Tasks.forResult(new GetTokenResult(testToken.supply(), Collections.emptyMap()));
    } catch (FirebaseException e) {
      return Tasks.forException(e);
    }
  }

  @Nullable
  @Override
  public String getUid() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addIdTokenListener(@NonNull IdTokenListener idTokenListener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeIdTokenListener(@NonNull IdTokenListener idTokenListener) {
    throw new UnsupportedOperationException();
  }
}
