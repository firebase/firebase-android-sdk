// Copyright 2025 Google LLC
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

package com.google.firebase.appcheck.recaptchaenterprise.internal;

import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import java.util.concurrent.Executor;

/**
 * This class encapsulates a {@link com.google.firebase.annotations.concurrent.Lightweight} executor
 * and a {@link com.google.firebase.annotations.concurrent.Blocking} executor, making them available
 * for various asynchronous operations related to reCAPTCHA Enterprise App Check.
 */
public class FirebaseExecutors {
  private final Executor liteExecutor;
  private final Executor blockingExecutor;

  public FirebaseExecutors(
      @Lightweight Executor liteExecutor, @Blocking Executor blockingExecutor) {
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
  }

  public Executor getLiteExecutor() {
    return liteExecutor;
  }

  public Executor getBlockingExecutor() {
    return blockingExecutor;
  }
}
