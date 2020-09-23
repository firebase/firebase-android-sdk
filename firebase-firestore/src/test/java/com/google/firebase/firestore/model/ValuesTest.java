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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.dbId;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.wrapRef;
import static org.junit.Assert.assertEquals;

import com.google.common.testing.EqualsTester;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.testutil.ComparatorTester;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firestore.v1.Value;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ValuesTest {
  private final Date date1;
  private final Date date2;

  public ValuesTest() {
    // Create a couple date objects for use in tests.
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.set(2016, 5, 20, 10, 20, 0);
    date1 = calendar.getTime();

    calendar.set(2016, 10, 21, 15, 32, 0);
    date2 = calendar.getTime();
  }

  @Test
  public void testValueEquality() {
    GeoPoint geoPoint1 = new GeoPoint(1, 0);
    GeoPoint geoPoint2 = new GeoPoint(0, 2);
    Timestamp timestamp1 = new Timestamp(date1);
    Timestamp timestamp2 = new Timestamp(date2);
    new EqualsTester()
        .addEqualityGroup(wrap(true), wrap(true))
        .addEqualityGroup(wrap(false), wrap(false))
        .addEqualityGroup(wrap(null), new EqualsWrapper(Values.NULL_VALUE))
        .addEqualityGroup(
            wrap(0.0 / 0.0),
            wrap(Double.longBitsToDouble(0x7ff8000000000000L)),
            new EqualsWrapper(Values.NAN_VALUE))
        // -0.0 and 0.0 compareTo the same but are not equal.
        .addEqualityGroup(wrap(-0.0))
        .addEqualityGroup(wrap(0.0))
        .addEqualityGroup(wrap(1), wrap(1))
        // Doubles and Longs aren't equal.
        .addEqualityGroup(wrap(1.0), wrap(1.0))
        .addEqualityGroup(wrap(1.1), wrap(1.1))
        .addEqualityGroup(wrap(blob(0, 1, 2)), wrap(blob(0, 1, 2)))
        .addEqualityGroup(wrap(blob(0, 1)))
        .addEqualityGroup(wrap("string"), wrap("string"))
        .addEqualityGroup(wrap("strin"))
        // latin small letter e + combining acute accent
        .addEqualityGroup(wrap("e\u0301b"))
        // latin small letter e with acute accent
        .addEqualityGroup(wrap("\u00e9a"))
        .addEqualityGroup(wrap(date1), wrap(timestamp1))
        .addEqualityGroup(wrap(timestamp2))
        // NOTE: ServerTimestamps can't be parsed via wrap().
        .addEqualityGroup(
            new EqualsWrapper(ServerTimestamps.valueOf(new Timestamp(date1), null)),
            new EqualsWrapper(ServerTimestamps.valueOf(new Timestamp(date1), null)))
        .addEqualityGroup(new EqualsWrapper(ServerTimestamps.valueOf(new Timestamp(date2), null)))
        .addEqualityGroup(wrap(geoPoint1), wrap(new GeoPoint(1, 0)))
        .addEqualityGroup(wrap(geoPoint2))
        .addEqualityGroup(
            wrap(ref("coll/doc1")), new EqualsWrapper(wrapRef(dbId("project"), key("coll/doc1"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("project", "bar"), key("coll/doc2"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("project", "baz"), key("coll/doc2"))))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar")), wrap(Arrays.asList("foo", "bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar", "baz")))
        .addEqualityGroup(wrap(Arrays.asList("foo")))
        .addEqualityGroup(wrap(map("bar", 1, "foo", 2)), wrap(map("foo", 2, "bar", 1)))
        .addEqualityGroup(wrap(map("bar", 2, "foo", 1)))
        .addEqualityGroup(wrap(map("bar", 1)))
        .addEqualityGroup(wrap(map("foo", 1)))
        .testEquals();
  }

  @Test
  public void testValueOrdering() {
    new ComparatorTester()
        // do not test for compatibility with equals(): +0/-0 break it.
        .permitInconsistencyWithEquals()

        // null first
        .addEqualityGroup(wrap(null))

        // booleans
        .addEqualityGroup(wrap(false))
        .addEqualityGroup(wrap(true))

        // numbers
        .addEqualityGroup(wrap(Double.NaN))
        .addEqualityGroup(wrap(Double.NEGATIVE_INFINITY))
        .addEqualityGroup(wrap(-Double.MAX_VALUE))
        .addEqualityGroup(wrap(Long.MIN_VALUE))
        .addEqualityGroup(wrap(-1.1))
        .addEqualityGroup(wrap(-1.0))
        .addEqualityGroup(wrap(-Double.MIN_NORMAL))
        .addEqualityGroup(wrap(-Double.MIN_VALUE))
        // Zeros all compare the same.
        .addEqualityGroup(wrap(-0.0), wrap(0.0), wrap(0L))
        .addEqualityGroup(wrap(Double.MIN_VALUE))
        .addEqualityGroup(wrap(Double.MIN_NORMAL))
        .addEqualityGroup(wrap(0.1))
        // Doubles and Longs compareTo() the same.
        .addEqualityGroup(wrap(1.0), wrap(1L))
        .addEqualityGroup(wrap(1.1))
        .addEqualityGroup(wrap(Long.MAX_VALUE))
        .addEqualityGroup(wrap(Double.MAX_VALUE))
        .addEqualityGroup(wrap(Double.POSITIVE_INFINITY))

        // dates
        .addEqualityGroup(wrap(date1))
        .addEqualityGroup(wrap(date2))

        // server timestamps come after all concrete timestamps.
        // NOTE: server timestamps can't be parsed with wrap().
        .addEqualityGroup(new EqualsWrapper(ServerTimestamps.valueOf(new Timestamp(date1), null)))
        .addEqualityGroup(new EqualsWrapper(ServerTimestamps.valueOf(new Timestamp(date2), null)))

        // strings
        .addEqualityGroup(wrap(""))
        .addEqualityGroup(wrap("\000\ud7ff\ue000\uffff"))
        .addEqualityGroup(wrap("(╯°□°）╯︵ ┻━┻"))
        .addEqualityGroup(wrap("a"))
        .addEqualityGroup(wrap("abc def"))
        // latin small letter e + combining acute accent + latin small letter b
        .addEqualityGroup(wrap("e\u0301b"))
        .addEqualityGroup(wrap("æ"))
        // latin small letter e with acute accent + latin small letter a
        .addEqualityGroup(wrap("\u00e9a"))

        // blobs
        .addEqualityGroup(wrap(blob()))
        .addEqualityGroup(wrap(blob(0)))
        .addEqualityGroup(wrap(blob(0, 1, 2, 3, 4)))
        .addEqualityGroup(wrap(blob(0, 1, 2, 4, 3)))
        .addEqualityGroup(wrap(blob(255)))

        // resource names
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("p1", "d1"), key("c1/doc1"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("p1", "d1"), key("c1/doc2"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("p1", "d1"), key("c10/doc1"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("p1", "d1"), key("c2/doc1"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("p1", "d2"), key("c1/doc1"))))
        .addEqualityGroup(new EqualsWrapper(wrapRef(dbId("p2", "d1"), key("c1/doc1"))))

        // geo points
        .addEqualityGroup(wrap(new GeoPoint(-90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 0)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 180)))
        .addEqualityGroup(wrap(new GeoPoint(0, -180)))
        .addEqualityGroup(wrap(new GeoPoint(0, 0)))
        .addEqualityGroup(wrap(new GeoPoint(0, 180)))
        .addEqualityGroup(wrap(new GeoPoint(1, -180)))
        .addEqualityGroup(wrap(new GeoPoint(1, 0)))
        .addEqualityGroup(wrap(new GeoPoint(1, 180)))
        .addEqualityGroup(wrap(new GeoPoint(90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(90, 0)))
        .addEqualityGroup(wrap(new GeoPoint(90, 180)))

        // arrays
        .addEqualityGroup(wrap(Arrays.asList("bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", 1)))
        .addEqualityGroup(wrap(Arrays.asList("foo", 2)))
        .addEqualityGroup(wrap(Arrays.asList("foo", "0")))

        // objects
        .addEqualityGroup(wrap(map("bar", 0)))
        .addEqualityGroup(wrap(map("bar", 0, "foo", 1)))
        .addEqualityGroup(wrap(map("foo", 1)))
        .addEqualityGroup(wrap(map("foo", 2)))
        .addEqualityGroup(wrap(map("foo", "0")))
        .testCompare();
  }

  @Test
  public void testCanonicalIds() {
    assertCanonicalId(TestUtil.wrap(null), "null");
    assertCanonicalId(TestUtil.wrap(true), "true");
    assertCanonicalId(TestUtil.wrap(false), "false");
    assertCanonicalId(TestUtil.wrap(1), "1");
    assertCanonicalId(TestUtil.wrap(1.0), "1.0");
    assertCanonicalId(TestUtil.wrap(new Timestamp(30, 1000)), "time(30,1000)");
    assertCanonicalId(TestUtil.wrap("a"), "a");
    assertCanonicalId(TestUtil.wrap(blob(1, 2, 3)), "010203");
    assertCanonicalId(wrapRef(dbId("p1", "d1"), key("c1/doc1")), "c1/doc1");
    assertCanonicalId(TestUtil.wrap(new GeoPoint(30, 60)), "geo(30.0,60.0)");
    assertCanonicalId(TestUtil.wrap(Arrays.asList(1, 2, 3)), "[1,2,3]");
    assertCanonicalId(TestUtil.wrap(map("a", 1, "b", 2, "c", "3")), "{a:1,b:2,c:3}");
    assertCanonicalId(
        TestUtil.wrap(map("a", Arrays.asList("b", map("c", new GeoPoint(30, 60))))),
        "{a:[b,{c:geo(30.0,60.0)}]}");
  }

  @Test
  public void testObjectCanonicalIdsIgnoreSortOrder() {
    assertCanonicalId(TestUtil.wrap(map("a", 1, "b", 2, "c", "3")), "{a:1,b:2,c:3}");
    assertCanonicalId(TestUtil.wrap(map("c", 3, "b", 2, "a", "1")), "{a:1,b:2,c:3}");
  }

  private void assertCanonicalId(Value proto, String expectedCanonicalId) {
    assertEquals(expectedCanonicalId, Values.canonicalId(proto));
  }

  /** Small helper class that uses ProtoValues for equals() and compareTo(). */
  static class EqualsWrapper implements Comparable<EqualsWrapper> {
    final Value proto;

    EqualsWrapper(Value proto) {
      this.proto = proto;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof EqualsWrapper && Values.equals(proto, ((EqualsWrapper) o).proto);
    }

    @Override
    public int hashCode() {
      return proto.hashCode();
    }

    @Override
    public int compareTo(EqualsWrapper o) {
      return Values.compare(proto, o.proto);
    }
  }

  private EqualsWrapper wrap(Object entry) {
    return new EqualsWrapper(TestUtil.wrap(entry));
  }
}
