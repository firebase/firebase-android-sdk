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

package com.google.firebase.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldValueTest {

  @Test
  public void testEquals() {
    FieldValue delete = FieldValue.delete();
    FieldValue deleteDup = FieldValue.delete();
    FieldValue serverTimestamp = FieldValue.serverTimestamp();
    FieldValue serverTimestampDup = FieldValue.serverTimestamp();
    RegexValue regex = FieldValue.regex("pattern", "options");
    RegexValue regexDup = FieldValue.regex("pattern", "options");
    Int32Value int32 = FieldValue.int32(1);
    Int32Value int32Dup = FieldValue.int32(1);
    BsonTimestamp bsonTimestamp = FieldValue.bsonTimestamp(1, 2);
    BsonTimestamp bsonTimestampDup = FieldValue.bsonTimestamp(1, 2);
    BsonObjectId bsonObjectId = FieldValue.bsonObjectId("507f191e810c19729de860ea");
    BsonObjectId bsonObjectIdDup = FieldValue.bsonObjectId("507f191e810c19729de860ea");
    BsonBinaryData bsonBinary = FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3});
    BsonBinaryData bsonBinaryDup = FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3});
    MinKey minKey = FieldValue.minKey();
    MinKey minKeyDup = FieldValue.minKey();
    MaxKey maxKey = FieldValue.maxKey();
    MaxKey maxKeyDup = FieldValue.maxKey();

    assertEquals(delete, deleteDup);
    assertEquals(serverTimestamp, serverTimestampDup);
    assertNotEquals(delete, serverTimestamp);

    assertEquals(delete.hashCode(), deleteDup.hashCode());
    assertEquals(serverTimestamp.hashCode(), serverTimestampDup.hashCode());
    assertNotEquals(delete.hashCode(), serverTimestamp.hashCode());

    // BSON types
    assertEquals(regex, regexDup);
    assertEquals(int32, int32Dup);
    assertEquals(bsonTimestamp, bsonTimestampDup);
    assertEquals(bsonObjectId, bsonObjectIdDup);
    assertEquals(bsonBinary, bsonBinaryDup);
    assertEquals(minKey, minKeyDup);
    assertEquals(maxKey, maxKeyDup);
    assertNotEquals(delete, serverTimestamp);

    // BSON types are not equal to each other
    assertNotEquals(regex, int32);
    assertNotEquals(regex, bsonTimestamp);
    assertNotEquals(regex, bsonObjectId);
    assertNotEquals(regex, bsonBinary);
    assertNotEquals(regex, minKey);
    assertNotEquals(regex, maxKey);

    assertNotEquals(int32, bsonTimestamp);
    assertNotEquals(int32, bsonObjectId);
    assertNotEquals(int32, bsonBinary);
    assertNotEquals(int32, minKey);
    assertNotEquals(int32, maxKey);

    assertNotEquals(bsonTimestamp, bsonObjectId);
    assertNotEquals(bsonTimestamp, bsonBinary);
    assertNotEquals(bsonTimestamp, minKey);
    assertNotEquals(bsonTimestamp, maxKey);

    assertNotEquals(bsonObjectId, bsonBinary);
    assertNotEquals(bsonObjectId, minKey);
    assertNotEquals(bsonObjectId, maxKey);

    assertNotEquals(minKey, maxKey);

    // BSON types hash codes
    assertEquals(regex.hashCode(), regexDup.hashCode());
    assertEquals(int32.hashCode(), int32Dup.hashCode());
    assertEquals(bsonTimestamp.hashCode(), bsonTimestampDup.hashCode());
    assertEquals(bsonObjectId.hashCode(), bsonObjectIdDup.hashCode());
    assertEquals(bsonBinary.hashCode(), bsonBinaryDup.hashCode());
    assertEquals(minKey.hashCode(), minKeyDup.hashCode());
    assertEquals(maxKey.hashCode(), maxKeyDup.hashCode());

    // BSON types hash codes are not equal to each other
    assertNotEquals(regex.hashCode(), int32.hashCode());
    assertNotEquals(regex.hashCode(), bsonTimestamp.hashCode());
    assertNotEquals(regex.hashCode(), bsonObjectId.hashCode());
    assertNotEquals(regex.hashCode(), bsonBinary.hashCode());
    assertNotEquals(regex.hashCode(), minKey.hashCode());
    assertNotEquals(regex.hashCode(), maxKey.hashCode());

    assertNotEquals(int32.hashCode(), bsonTimestamp.hashCode());
    assertNotEquals(int32.hashCode(), bsonObjectId.hashCode());
    assertNotEquals(int32.hashCode(), bsonBinary.hashCode());
    assertNotEquals(int32.hashCode(), minKey.hashCode());
    assertNotEquals(int32.hashCode(), maxKey.hashCode());

    assertNotEquals(bsonTimestamp.hashCode(), bsonObjectId.hashCode());
    assertNotEquals(bsonTimestamp.hashCode(), bsonBinary.hashCode());
    assertNotEquals(bsonTimestamp.hashCode(), minKey.hashCode());
    assertNotEquals(bsonTimestamp.hashCode(), maxKey.hashCode());

    assertNotEquals(bsonObjectId.hashCode(), bsonBinary.hashCode());
    assertNotEquals(bsonObjectId.hashCode(), minKey.hashCode());
    assertNotEquals(bsonObjectId.hashCode(), maxKey.hashCode());

    assertNotEquals(bsonBinary.hashCode(), minKey.hashCode());
    assertNotEquals(bsonBinary.hashCode(), maxKey.hashCode());

    assertNotEquals(minKey.hashCode(), maxKey.hashCode());
  }
}
