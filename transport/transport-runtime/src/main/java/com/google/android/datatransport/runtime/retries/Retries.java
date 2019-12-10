// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.retries;

/** Provides utilities to retry function calls. */
public final class Retries {
  private Retries() {}

  /**
   * Retries given {@link Function} upto {@code maxAttempts} times.
   *
   * <p>It takes an {@code input} parameter that is passed to the first {@link Function} call. The
   * rest of the retries are called with the value produced by the {@code retryStrategy}. If the
   * {@code retryStrategy} returns null, the retries are stopped and the result of the last retry is
   * returned.
   *
   * <p>Example
   *
   * <pre>{@code
   * int initialParameter = 10;
   *
   * // finalResult is 12.
   * int finalResult = retry(5, initialParameter, Integer::valueOf, (input, result) -> {
   *   if ( result.equals(12)) {
   *     return null;
   *   }
   *   return input + 1;
   * });
   * }</pre>
   */
  public static <TInput, TResult, TException extends Throwable> TResult retry(
      int maxAttempts,
      TInput input,
      Function<TInput, TResult, TException> function,
      RetryStrategy<TInput, TResult> retryStrategy)
      throws TException {
    if (maxAttempts < 1) {
      return function.apply(input);
    }

    while (true) {
      TResult result = function.apply(input);
      input = retryStrategy.shouldRetry(input, result);

      if (input == null || --maxAttempts < 1) {
        return result;
      }
    }
  }
}
