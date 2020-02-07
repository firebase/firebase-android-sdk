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

package com.google.firebase.database;

import org.junit.Assert;

public class TestChildEventListener implements ChildEventListener {
  @Override
  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
    Assert.fail("onChildAdded called, but was not expected in Test!");
  }

  @Override
  public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
    Assert.fail("onChildChanged called, but was not expected in Test!");
  }

  @Override
  public void onChildRemoved(DataSnapshot snapshot) {
    Assert.fail("onChildRemoved called, but was not expected in Test!");
  }

  @Override
  public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
    Assert.fail("onChildMoved called, but was not expected in Test!");
  }

  @Override
  public void onCancelled(DatabaseError error) {
    Assert.fail("onCancelled called, but was not expected in Test!");
  }
}
