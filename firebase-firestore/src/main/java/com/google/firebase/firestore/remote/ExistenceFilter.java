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

package com.google.firebase.firestore.remote;

import androidx.annotation.Nullable;
import com.google.firestore.v1.BloomFilter;

/** Simplest form of existence filter */
public final class ExistenceFilter {
  private final int count;
  private BloomFilter unchangedNames;

  public ExistenceFilter(int count) {
    this.count = count;
  }

  public ExistenceFilter(int count, @Nullable BloomFilter unchangedNames) {
    this.count = count;
    this.unchangedNames = unchangedNames;
  }

  public int getCount() {
    return count;
  }

  @Nullable
  public BloomFilter getUnchangedNames() {
    return unchangedNames;
  }

  @Override
  public String toString() {
    return "ExistenceFilter{count=" + count + ", unchangedNames=" + unchangedNames + '}';
  }
}
