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

import static org.junit.Assert.assertEquals;

import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.utilities.Utilities;
import org.junit.Assert;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class UtilitiesTest {
  @Test
  public void tryParseInt() {
    Assert.assertEquals(
        Utilities.tryParseInt("" + Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE));
    Assert.assertEquals(
        Utilities.tryParseInt("" + Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE));
    Assert.assertEquals(Utilities.tryParseInt("0"), Integer.valueOf(0));
    Assert.assertEquals(Utilities.tryParseInt("-0"), Integer.valueOf(0));
    Assert.assertEquals(Utilities.tryParseInt("-1"), Integer.valueOf(-1));
    Assert.assertEquals(Utilities.tryParseInt("1"), Integer.valueOf(1));
    Assert.assertNull(Utilities.tryParseInt("a"));
    Assert.assertNull(Utilities.tryParseInt("-0a"));
    Assert.assertNull(Utilities.tryParseInt("-"));
    Assert.assertNull(Utilities.tryParseInt("" + (Integer.MAX_VALUE + 1L)));
    Assert.assertNull(Utilities.tryParseInt("" + (Integer.MIN_VALUE - 1L)));
  }

  @Test
  public void defaultCacheSizeIs10MB() {
    assertEquals(10 * 1024 * 1024, new DatabaseConfig().getPersistenceCacheSizeBytes());
  }

  @Test
  public void settingValidCacheSizeSucceeds() {
    new DatabaseConfig().setPersistenceCacheSizeBytes(5 * 1024 * 1024); // works fine.
  }

  @Test(expected = DatabaseException.class)
  public void settingCacheSizeTooLowFails() {
    new DatabaseConfig().setPersistenceCacheSizeBytes(1024 * 1024 - 1);
  }

  @Test(expected = DatabaseException.class)
  public void settingCacheSizeTooHighFails() {
    new DatabaseConfig().setPersistenceCacheSizeBytes(100 * 1024 * 1024 + 1);
  }
}
