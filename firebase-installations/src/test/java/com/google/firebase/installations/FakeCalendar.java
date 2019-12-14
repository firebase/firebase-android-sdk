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

package com.google.firebase.installations;

import java.util.Calendar;

public class FakeCalendar extends Calendar {
  private long timeInMillis;

  public FakeCalendar(long initialTimeInMillis) {
    timeInMillis = initialTimeInMillis;
  }

  @Override
  public long getTimeInMillis() {
    return timeInMillis;
  }

  @Override
  public void setTimeInMillis(long timeInMillis) {
    this.timeInMillis = timeInMillis;
  }

  public void advanceTimeBySeconds(long deltaSeconds) {
    timeInMillis += (deltaSeconds * 1000L);
  }

  @Override
  protected void computeTime() {}

  @Override
  protected void computeFields() {}

  @Override
  public void add(int i, int i1) {}

  @Override
  public void roll(int i, boolean b) {}

  @Override
  public int getMinimum(int i) {
    return 0;
  }

  @Override
  public int getMaximum(int i) {
    return 0;
  }

  @Override
  public int getGreatestMinimum(int i) {
    return 0;
  }

  @Override
  public int getLeastMaximum(int i) {
    return 0;
  }
}
