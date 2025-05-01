// Copyright 2025 Google LLC
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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BsonTypesTest {

  @Test
  public void testBsonObjectIdEquality() {
    BsonObjectId bsonObjectId = new BsonObjectId("507f191e810c19729de860ea");
    BsonObjectId bsonObjectIdDup = new BsonObjectId("507f191e810c19729de860ea");
    BsonObjectId differentObjectId = new BsonObjectId("507f191e810c19729de860eb");

    assertEquals(bsonObjectId, bsonObjectIdDup);
    assertNotEquals(bsonObjectId, differentObjectId);
    assertNotEquals(bsonObjectIdDup, differentObjectId);

    assertEquals(bsonObjectId.hashCode(), bsonObjectIdDup.hashCode());
    assertNotEquals(bsonObjectId.hashCode(), differentObjectId.hashCode());
    assertNotEquals(bsonObjectIdDup.hashCode(), differentObjectId.hashCode());
  }

  @Test
  public void testBsonTimeStampEquality() {
    BsonTimestamp bsonTimestamp = new BsonTimestamp(1, 2);
    BsonTimestamp bsonTimestampDup = new BsonTimestamp(1, 2);
    BsonTimestamp differentSecondsTimestamp = new BsonTimestamp(2, 2);
    BsonTimestamp differentIncrementTimestamp = new BsonTimestamp(1, 3);

    assertEquals(bsonTimestamp, bsonTimestampDup);
    assertNotEquals(bsonTimestamp, differentSecondsTimestamp);
    assertNotEquals(bsonTimestamp, differentIncrementTimestamp);
    assertNotEquals(bsonTimestampDup, differentSecondsTimestamp);
    assertNotEquals(bsonTimestampDup, differentIncrementTimestamp);

    assertEquals(bsonTimestamp.hashCode(), bsonTimestampDup.hashCode());
    assertNotEquals(bsonTimestamp.hashCode(), differentSecondsTimestamp.hashCode());
    assertNotEquals(bsonTimestamp.hashCode(), differentIncrementTimestamp.hashCode());
    assertNotEquals(bsonTimestampDup.hashCode(), differentSecondsTimestamp.hashCode());
    assertNotEquals(bsonTimestampDup.hashCode(), differentIncrementTimestamp.hashCode());
  }

  @Test
  public void testBsonBinaryDataEquality() {
    BsonBinaryData bsonBinaryData = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3});
    BsonBinaryData bsonBinaryDataDup = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3});
    BsonBinaryData differentSubtypeBinaryData = BsonBinaryData.fromBytes(2, new byte[] {1, 2, 3});
    BsonBinaryData differentDataBinaryData = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4});

    assertEquals(bsonBinaryData, bsonBinaryDataDup);
    assertNotEquals(bsonBinaryData, differentSubtypeBinaryData);
    assertNotEquals(bsonBinaryData, differentDataBinaryData);
    assertNotEquals(bsonBinaryDataDup, differentSubtypeBinaryData);
    assertNotEquals(bsonBinaryDataDup, differentDataBinaryData);

    assertEquals(bsonBinaryData.hashCode(), bsonBinaryDataDup.hashCode());
    assertNotEquals(bsonBinaryData.hashCode(), differentSubtypeBinaryData.hashCode());
    assertNotEquals(bsonBinaryData.hashCode(), differentDataBinaryData.hashCode());
    assertNotEquals(bsonBinaryDataDup.hashCode(), differentSubtypeBinaryData.hashCode());
    assertNotEquals(bsonBinaryDataDup.hashCode(), differentDataBinaryData.hashCode());
  }

  @Test
  public void testRegexEquality() {
    RegexValue regex = new RegexValue("^foo", "i");
    RegexValue regexDup = new RegexValue("^foo", "i");
    RegexValue differentPatternRegex = new RegexValue("^bar", "i");
    RegexValue differentOptionsRegex = new RegexValue("^foo", "m");

    assertEquals(regex, regexDup);
    assertNotEquals(regex, differentPatternRegex);
    assertNotEquals(regex, differentOptionsRegex);
    assertNotEquals(regexDup, differentPatternRegex);
    assertNotEquals(regexDup, differentOptionsRegex);

    assertEquals(regex.hashCode(), regexDup.hashCode());
    assertNotEquals(regex.hashCode(), differentPatternRegex.hashCode());
    assertNotEquals(regex.hashCode(), differentOptionsRegex.hashCode());
    assertNotEquals(regexDup.hashCode(), differentPatternRegex.hashCode());
    assertNotEquals(regexDup.hashCode(), differentOptionsRegex.hashCode());
  }

  @Test
  public void testInt32Equality() {
    Int32Value int32 = new Int32Value(1);
    Int32Value int32Dup = new Int32Value(1);
    Int32Value differentInt32 = new Int32Value(2);

    assertEquals(int32, int32Dup);
    assertNotEquals(int32, differentInt32);
    assertNotEquals(int32Dup, differentInt32);

    assertEquals(int32.hashCode(), int32Dup.hashCode());
    assertNotEquals(int32.hashCode(), differentInt32.hashCode());
    assertNotEquals(int32Dup.hashCode(), differentInt32.hashCode());
  }

  @Test
  public void testMaxKeyIsSingleton() {
    MaxKey maxKey = MaxKey.instance();
    MaxKey maxKeyDup = MaxKey.instance();
    assertEquals(maxKey, maxKeyDup);
    assertEquals(maxKey.hashCode(), maxKeyDup.hashCode());
  }

  @Test
  public void testMinKeyIsSingleton() {
    MinKey minKey = MinKey.instance();
    MinKey minKeyDup = MinKey.instance();
    assertEquals(minKey, minKeyDup);
    assertEquals(minKey.hashCode(), minKeyDup.hashCode());
  }

  @Test
  public void testMinKeyMaxKeyNullNotEqual() {
    MinKey minKey = MinKey.instance();
    MaxKey maxKey = MaxKey.instance();
    assertNotEquals(minKey, maxKey);
    assertNotEquals(minKey, null);
    assertNotEquals(maxKey, null);
    assertNotEquals(minKey.hashCode(), maxKey.hashCode());
  }

  @Test
  public void testThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> BsonBinaryData.fromBytes(256, new byte[] {1}));
  }
}
