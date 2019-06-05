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

package com.google.firebase.firestore.local;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class EncodedPathTest {

  static class OpenHelper extends SQLiteOpenHelper {
    OpenHelper() {
      super(ApplicationProvider.getApplicationContext(), "test", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE keys (key TEXT PRIMARY KEY)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      throw new UnsupportedOperationException("This is a test");
    }
  }

  private static final String SEP = "\u0001\u0001";

  private SQLiteDatabase db;

  @Before
  public void setUp() {
    db = new OpenHelper().getWritableDatabase();
  }

  @After
  public void tearDown() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  public void testEncodesResourcePaths() {
    assertEncoded(SEP, path());
    assertEncoded("\u0001\u0010" + SEP, path("\0"));
    assertEncoded("\u0001\u0011" + SEP, path("\u0001"));
    assertEncoded("\u0002" + SEP, path("\u0002"));

    assertEncoded("foo\u0001\u0010" + SEP, path("foo\0"));
    assertEncoded("\u0001\u0010foo" + SEP, path("\0foo"));

    // Server specials that we don't care about here.
    assertEncoded("." + SEP, path("."));
    assertEncoded(".." + SEP, path(".."));
    assertEncoded("/" + SEP, path("/"));

    assertEncoded("a" + SEP + "b" + SEP + "c" + SEP, path("a", "b", "c"));
    assertEncoded(
        "a/b" + SEP + "b.c" + SEP + "c\u0001\u0010d" + SEP + "d\u0001\u0011e" + SEP,
        path("a/b", "b.c", "c\0d", "d\u0001e"));
  }

  private void assertEncoded(String expected, ResourcePath path) {
    String encoded = EncodedPath.encode(path);
    assertEquals(expected, encoded);
    ResourcePath decoded = EncodedPath.decodeResourcePath(encoded);
    assertEquals(path, decoded);

    // Verify that the value round trips through the SQLite API too.
    db.execSQL("INSERT INTO keys VALUES (?)", new String[] {encoded});
    Cursor cursor = db.rawQuery("SELECT key FROM keys WHERE key = ?", new String[] {encoded});
    try {
      assertTrue(cursor.moveToNext());
      assertEquals(expected, cursor.getString(0));

    } finally {
      cursor.close();
    }
  }

  @Test
  public void testOrdersResourcePaths() {
    assertOrdered(
        path(),
        path("\0"),
        path("\u0001"),
        path("\u0002"),
        path("\t"),
        path(" "),
        path("%"),
        path("."),
        path("/"),
        path("0"),
        path("z"),
        path("~"));

    assertOrdered(
        path(),
        path("foo"),
        path("foo", ""),
        path("foo", "bar"),
        path("foo/", "bar"),
        path("foob"),
        path("foobar"),
        path("food"));
  }

  private ResourcePath path(String... segments) {
    return ResourcePath.fromSegments(asList(segments));
  }

  private void assertOrdered(ResourcePath... paths) {
    db.execSQL("DELETE FROM keys");

    // Compute the encoded forms of all the given paths
    String[] encoded = new String[paths.length];
    for (int i = 0; i < paths.length; i++) {
      encoded[i] = EncodedPath.encode(paths[i]);
    }

    // Insert those all into a table, but backwards
    db.beginTransaction();
    for (int i = encoded.length; i-- > 0; ) {
      db.execSQL("INSERT INTO keys VALUES (?)", new String[] {encoded[i]});
    }
    db.setTransactionSuccessful();
    db.endTransaction();

    // Read the values out, requiring SQLite to order the keys
    List<String> selected = new ArrayList<>(paths.length);
    Cursor cursor = db.rawQuery("SELECT key FROM keys ORDER BY key", null);
    try {
      while (cursor.moveToNext()) {
        selected.add(cursor.getString(0));
      }
    } finally {
      cursor.close();
    }

    // Finally, verify all the orderings.
    for (int i = 0; i < paths.length; i++) {
      for (int j = 0; j < encoded.length; j++) {
        if (i < j) {
          assertThat(paths[i], lessThan(paths[j]));
          assertThat(encoded[i], lessThan(encoded[j]));
          assertThat(selected.get(i), lessThan(selected.get(j)));
        } else if (i > j) {
          assertThat(paths[i], greaterThan(paths[j]));
          assertThat(encoded[i], greaterThan(encoded[j]));
          assertThat(selected.get(i), greaterThan(selected.get(j)));
        } else {
          assertThat(paths[i], equalTo(paths[j]));
          assertThat(encoded[i], equalTo(encoded[j]));
          assertThat(selected.get(i), equalTo(selected.get(j)));
        }
      }
    }
  }

  @Test
  public void testPrefixSuccessor() {
    assertPrefixSuccessorEquals("\u0001\u0002", path());
    assertPrefixSuccessorEquals("foo" + SEP + "bar\u0001\u0002", path("foo", "bar"));
  }

  private void assertPrefixSuccessorEquals(String expected, ResourcePath path) {
    assertEquals(expected, EncodedPath.prefixSuccessor(EncodedPath.encode(path)));
  }
}
