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

package com.google.firebase.storage.internal;

/** Helper for mocking sleep for exponential backoff. */
public class MockSleeperHelper implements Sleeper {
  private final MockClockHelper clock;

  public MockSleeperHelper(MockClockHelper clock) {
    this.clock = clock;
  }

  @Override
  public void sleep(int milliseconds) throws InterruptedException {
    clock.advance(milliseconds);
  }
}
