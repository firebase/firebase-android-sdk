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

package com.google.firebase.database.core.persistence;

public class TestCachePolicy implements CachePolicy {

  private boolean timeToPrune = false;
  private final float percentToPruneAtOnce;
  private final long maxNumberToKeep;

  public TestCachePolicy(float percentToPruneAtOnce, long maxNumberToKeep) {
    this.percentToPruneAtOnce = percentToPruneAtOnce;
    this.maxNumberToKeep = maxNumberToKeep;
  }

  public void pruneOnNextServerUpdate() {
    timeToPrune = true;
  }

  @Override
  public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries) {
    if (timeToPrune) {
      timeToPrune = false;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck) {
    return true;
  }

  @Override
  public float getPercentOfQueriesToPruneAtOnce() {
    return percentToPruneAtOnce;
  }

  @Override
  public long getMaxNumberOfQueriesToKeep() {
    return maxNumberToKeep;
  }
}
