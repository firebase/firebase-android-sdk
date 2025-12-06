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

package com.google.firebase.firestore.util;

import static android.os.Build.VERSION_CODES.O;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.TestUtil;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link RecordMapper} using non-desugared java records.
 *
 * @author Eran Leshem
 */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(manifest = Config.NONE, minSdk = O)
@SuppressWarnings({"unused", "WeakerAccess"})
public class RecordMapperTest extends BaseRecordMapperTest {
  @Test
  public void documentIdsDeserialize() {
    DocumentReference ref = TestUtil.documentReference("coll/doc123");

    assertEquals("doc123", deserialize("{}", DocumentIdOnStringField.class, ref).docId());

    assertEquals(
        "doc123",
        deserialize(Collections.singletonMap("property", 100), DocumentIdOnStringField.class, ref)
            .docId());

    var target =
        deserialize("{'anotherProperty': 100}", DocumentIdOnStringFieldAsProperty.class, ref);
    assertEquals("doc123", target.docId());
    assertEquals(100, target.someOtherProperty());

    assertEquals(
        "doc123",
        deserialize("{'nestedDocIdHolder': {}}", DocumentIdOnNestedObjects.class, ref)
            .nestedDocIdHolder()
            .docId());
  }

  @Test
  public void documentIdsRoundTrip() {
    // Implicitly verifies @DocumentId is ignored during serialization.

    DocumentReference ref = TestUtil.documentReference("coll/doc123");

    assertEquals(
        Collections.emptyMap(), serialize(deserialize("{}", DocumentIdOnStringField.class, ref)));

    assertEquals(
        Collections.singletonMap("anotherProperty", 100),
        serialize(
            deserialize("{'anotherProperty': 100}", DocumentIdOnStringFieldAsProperty.class, ref)));

    assertEquals(
        Collections.singletonMap("nestedDocIdHolder", Collections.emptyMap()),
        serialize(deserialize("{'nestedDocIdHolder': {}}", DocumentIdOnNestedObjects.class, ref)));
  }

  @Test
  public void documentIdsDeserializeConflictThrows() {
    final String expectedErrorMessage = "cannot apply @DocumentId on this property";
    DocumentReference ref = TestUtil.documentReference("coll/doc123");

    assertExceptionContains(
        expectedErrorMessage,
        () -> deserialize("{'docId': 'toBeOverwritten'}", DocumentIdOnStringField.class, ref));

    assertExceptionContains(
        expectedErrorMessage,
        () ->
            deserialize(
                "{'docIdProperty': 'toBeOverwritten', 'anotherProperty': 100}",
                DocumentIdOnStringFieldAsProperty.class,
                ref));

    assertExceptionContains(
        expectedErrorMessage,
        () ->
            deserialize(
                "{'nestedDocIdHolder': {'docId': 'toBeOverwritten'}}",
                DocumentIdOnNestedObjects.class,
                ref));
  }
}
