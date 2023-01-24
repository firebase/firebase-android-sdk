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

package com.google.firebase.common.testutil;

// JUnit 4.13 class lifted from https://github.com/junit-team/junit4 with thanks.

/**
 * This interface facilitates the use of expectThrows from Java 8. It allows method references to
 * void methods (that declare checked exceptions) to be passed directly into expectThrows without
 * wrapping. It is not meant to be implemented directly.
 */
public interface ThrowingRunnable {
  void run() throws Throwable;
}
