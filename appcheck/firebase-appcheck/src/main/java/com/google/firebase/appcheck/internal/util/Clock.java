// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal.util;

/** Interface to make calls to System.currentTimeMillis() easier to mock our for testing. */
public interface Clock {

  long currentTimeMillis();

  /**
   * Default implementation of this interface that uses System.currentTimeMillis() to get the time.
   */
  public static class DefaultClock implements Clock {

    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  }
}
