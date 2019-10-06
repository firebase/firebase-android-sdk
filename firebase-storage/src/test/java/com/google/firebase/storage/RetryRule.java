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

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Retries tests that fail due to the test harnesses' unpredictable threading behavior. */
public class RetryRule implements TestRule {
  private final int retryCount;

  public RetryRule(int retryCount) {
    this.retryCount = retryCount;
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Throwable caughtThrowable = null;

        for (int i = 0; i < retryCount; i++) {
          try {
            base.evaluate();
            return;
          } catch (Throwable t) {
            caughtThrowable = t;
          }
        }

        throw caughtThrowable;
      }
    };
  }
}
