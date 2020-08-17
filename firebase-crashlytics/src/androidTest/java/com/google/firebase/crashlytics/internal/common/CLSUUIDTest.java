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

package com.google.firebase.crashlytics.internal.common;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.installations.FirebaseInstallationsApi;

public class CLSUUIDTest extends CrashlyticsTestCase {

  private IdManager idManager;
  private CLSUUID uuid;

  protected void setUp() throws Exception {
    super.setUp();
    FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
    when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
    idManager = new IdManager(getContext(), getContext().getPackageName(), installationsApiMock);
    uuid = new CLSUUID(idManager);
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    uuid = null;
  }

  /** Basic test of the CLSUUID string value. */
  public void testToString() {
    final String s = uuid.toString();
    assertNotNull("The uuid string value should not be null", s);
    assertEquals("The uuid string value should be 35 chars long", 35, s.length());
  }

  /** Test that we don't get duplicate CLSUUID string values in a set of 100 uuid generated. */
  public void testOrder() {
    final String[] uuids = new String[100];
    CLSUUID uuid = null;

    // Put 100 CLSUUID string values into an array.
    for (int i = 0; i < 100; i++) {
      uuid = new CLSUUID(idManager);
      uuids[i] = uuid.toString();
    }

    // Assert that the other 99 CLSUUIDs don't match the current index in the loop.
    for (int i = 0; i < uuids.length; i++) {
      final String uuidAtIndex = uuids[i];
      for (int j = 0; j < uuids.length; j++) {
        if (i != j) {
          assertFalse(
              "We shouldn't have the same uuid at another index.", uuidAtIndex.equals(uuids[j]));
        }
      }
    }
  }
}
