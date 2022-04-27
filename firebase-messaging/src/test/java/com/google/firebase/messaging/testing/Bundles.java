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
package com.google.firebase.messaging.testing;

import static junit.framework.Assert.assertTrue;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.common.internal.Preconditions;
import java.util.Map;
import java.util.Map.Entry;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

/** Test helpers for {@link Bundle}. */
public final class Bundles {

  // VAL_PARCELABLE in http://cs/android/frameworks/base/core/java/android/os/Parcel.java
  private static final int PARCELABLE = 4;
  // VAL_SERIALIZABLE in http://cs/android/frameworks/base/core/java/android/os/Parcel.java
  private static final int SERIALIZABLE = 21;

  // Byte array representing a serialized object of a class that doesn't exist to test bundles
  // containing such objects.
  // We cannot generate it at runtime as then the class would have to exist at runtime. See
  // commented out createSerializedPoisonPill() at the end of class for how to re-generate it.
  private static final byte[] SERIALIZED_POISON_PILL =
      new byte[] {
        -84, -19, 0, 5, 115, 114, 0, 53, 99, 111, 109, 46, 103, 111, 111, 103, 108, 101, 46, 97,
        110, 100, 114, 111, 105, 100, 46, 103, 109, 115, 46, 103, 99, 109, 46, 116, 101, 115, 116,
        105, 110, 103, 46, 66, 117, 110, 100, 108, 101, 115, 36, 80, 111, 105, 115, 111, 110, 80,
        105, 108, 108, -96, -89, 116, -107, 127, 104, -11, -10, 2, 0, 0, 120, 112
      };

  private Bundles() {}

  public static Bundle of(String... pairs) {
    Preconditions.checkArgument(pairs.length % 2 == 0);

    Bundle bundle = new Bundle();
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      bundle.putString(pairs[i], pairs[i + 1]);
    }
    return bundle;
  }

  public static Bundle of(String key, boolean value) {
    Bundle bundle = new Bundle();
    bundle.putBoolean(key, value);
    return bundle;
  }

  public static Bundle of(String key, Parcelable value) {
    Bundle bundle = new Bundle();
    bundle.putParcelable(key, value);
    return bundle;
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN_MR2)
  public static Bundle of(String key, IBinder value) {
    Bundle bundle = new Bundle();
    bundle.putBinder(key, value);
    return bundle;
  }

  public static Bundle of(String key, int value) {
    Bundle bundle = new Bundle();
    bundle.putInt(key, value);
    return bundle;
  }

  /** assertEquals for bundles as Bundle doesn't provide an equals() method. */
  public static void assertEquals(Bundle expected, Bundle actual) {
    final String message = "Expected: " + expected + ", actual: " + actual;
    assertTrue(message, matcher(expected).matches(actual));
  }

  /** Creates a matcher for the given bundle. */
  public static Matcher<Bundle> matcher(Bundle expected) {
    return new BaseMatcher<Bundle>() {
      @Override
      public boolean matches(Object o) {
        if (!(o instanceof Bundle)) {
          return false;
        }
        Bundle actual = (Bundle) o;
        if (expected.size() != actual.size() || !expected.keySet().containsAll(actual.keySet())) {
          return false;
        }
        for (String key : expected.keySet()) {
          Object actualValue = actual.get(key);
          Object expectedValue = expected.get(key);
          if (expectedValue instanceof Bundle) {
            if (!matcher((Bundle) expectedValue).matches(actualValue)) {
              return false;
            }
          } else {
            if (!Objects.equal(expectedValue, actualValue)) {
              return false;
            }
          }
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(expected.toString());
      }
    };
  }

  public static Bundle fromStringMap(Map<String, String> map) {
    Bundle bundle = new Bundle();
    for (Entry<String, String> entry : map.entrySet()) {
      bundle.putString(entry.getKey(), entry.getValue());
    }
    return bundle;
  }

  /**
   * Creates a poisoned Bundle. Trying to read any values from it will throw a
   * BadParcelableException.
   *
   * @see http://cs/android/frameworks/base/core/java/android/os/Parcel.java
   */
  public static Bundle createPoisonedWithSerializable() throws Exception {
    return createPoisonedInternal(SERIALIZABLE);
  }

  /**
   * Creates a poisoned Bundle. Trying to read any values from it will throw a
   * BadParcelableException.
   *
   * @see http://cs/android/frameworks/base/core/java/android/os/Parcel.java
   */
  public static Bundle createPoisoned() throws Exception {
    return createPoisonedInternal(PARCELABLE);
  }

  /**
   * Creates a poisoned Bundle. Trying to read any values from it will throw a
   * BadParcelableException.
   *
   * @see http://cs/android/frameworks/base/core/java/android/os/Parcel.java
   */
  private static Bundle createPoisonedInternal(int tag) throws Exception {
    // The easiest way to poison a Bundle is to manually write one out to a Parcel.
    // N.B: This doesn't work in a Robolectric environment because Robolectric's Parcel
    // implementation writes a bunch of other nonsense as well, which interferes with what we're
    // trying to do.
    Parcel p = Parcel.obtain();
    p.setDataPosition(0);

    // Write a filler length for now, we'll backfill once we know how much space the Bundle takes
    p.writeInt(-1);
    // Magic sentinel value to identify this as a Bundle
    p.writeInt(0x4C444E42); // 'B' 'N' 'D' 'L'

    // Start of the map
    int mapStartPos = p.dataPosition();
    p.writeInt(1); // number of entries

    // Every Bundle entry is a key (String), int (type tag), and then the value. Parcelable and
    // Serializable values are stored as their class name, then any corresponding data.
    // For poisoned Parcelables it doesn't matter whether we put anything after the class name, as
    // the class will be instantiated before reading it.
    // For poisoned Serializables the class name is ignored and the class is being loaded after
    // object being being read, so we must put the real serialized object there.
    p.writeString("poison"); // key
    p.writeInt(tag); // Parcelable or serializable tag number.
    p.writeString("com.google.firebase.iid.testing.Bundles$PoisonPill");
    if (tag == SERIALIZABLE) {
      p.writeByteArray(SERIALIZED_POISON_PILL);
    }

    // End of the map
    int mapEndPos = p.dataPosition();

    // Lastly, we need to go back and backfill the length.
    p.setDataPosition(0);
    p.writeInt(mapEndPos - mapStartPos);
    p.setDataPosition(0);

    // Now that we have a poisoned parcel, we can use it to create a Bundle
    Bundle poisonedBundle = Bundle.CREATOR.createFromParcel(p);
    p.recycle();
    return poisonedBundle;
  }

  // To regenerate SERIALIZED_POISON_PILL, use createSerializedPoisonPill() return value.
  //
  //   private static byte[] createSerializedPoisonPill() {
  //     ByteArrayOutputStream baos = new ByteArrayOutputStream();
  //     try {
  //         ObjectOutputStream oos = new ObjectOutputStream(baos);
  //         oos.writeObject(new PoisonPill());
  //         oos.close();
  //
  //         return baos.toByteArray();
  //     } catch (IOException ioe) {
  //         throw new RuntimeException(ioe);
  //     }
  //
  //   }
  //   private static class PoisonPill implements Serializable {
  //   }
}
