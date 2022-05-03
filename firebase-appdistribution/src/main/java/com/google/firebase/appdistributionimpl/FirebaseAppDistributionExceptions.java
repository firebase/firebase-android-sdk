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

package com.google.firebase.appdistributionimpl;

import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;

/** Utilities for interacting with {@link FirebaseAppDistributionException}. */
class FirebaseAppDistributionExceptions {

  static FirebaseAppDistributionException wrap(Throwable t) {
    // We never want to wrap a FirebaseAppDistributionException
    if (t instanceof FirebaseAppDistributionException) {
      return (FirebaseAppDistributionException) t;
    }
    return new FirebaseAppDistributionException(
        String.format("%s: %s", ErrorMessages.UNKNOWN_ERROR, t.getMessage()),
        Status.UNKNOWN,
        null,
        t);
  }
}
