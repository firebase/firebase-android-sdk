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

package com.google.firebase.firestore.model.mutation;

import android.support.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.FieldValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class used for union and remove array transforms.
 *
 * <p>Implementations are: ArrayTransformOperation.Union and ArrayTransformOperation.Remove
 */
public abstract class ArrayTransformOperation implements TransformOperation {
  private final List<FieldValue> elements;

  ArrayTransformOperation(List<FieldValue> elements) {
    this.elements = Collections.unmodifiableList(elements);
  }

  public List<FieldValue> getElements() {
    return elements;
  }

  @Override
  public FieldValue applyToLocalView(FieldValue previousValue, Timestamp localWriteTime) {
    return apply(previousValue);
  }

  @Override
  public FieldValue applyToRemoteDocument(FieldValue previousValue, FieldValue transformResult) {
    // The server just sends null as the transform result for array operations, so we have to
    // calculate a result the same as we do for local applications.
    return apply(previousValue);
  }

  @Override
  @SuppressWarnings("EqualsGetClass") // subtype-sensitive equality is intended.
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArrayTransformOperation that = (ArrayTransformOperation) o;

    return elements.equals(that.elements);
  }

  @Override
  public int hashCode() {
    int result = getClass().hashCode();
    result = 31 * result + elements.hashCode();
    return result;
  }

  /** Applies this ArrayTransformOperation against the specified previousValue. */
  protected abstract ArrayValue apply(FieldValue previousValue);

  /**
   * Inspects the provided value, returning an ArrayList copy of the internal array if it's an
   * ArrayValue and an empty ArrayList if it's null or any other type of FSTFieldValue.
   */
  static ArrayList<FieldValue> coercedFieldValuesArray(@Nullable FieldValue value) {
    if (value instanceof ArrayValue) {
      return new ArrayList<>(((ArrayValue) value).getInternalValue());
    } else {
      // coerce to empty array.
      return new ArrayList<>();
    }
  }

  @Override
  public boolean isIdempotent() {
    return true;
  }

  /** An array union transform operation. */
  public static class Union extends ArrayTransformOperation {
    public Union(List<FieldValue> elements) {
      super(elements);
    }

    @Override
    protected ArrayValue apply(FieldValue previousValue) {
      ArrayList<FieldValue> result = coercedFieldValuesArray(previousValue);
      for (FieldValue element : getElements()) {
        if (!result.contains(element)) {
          result.add(element);
        }
      }
      return ArrayValue.fromList(result);
    }
  }

  /** An array remove transform operation. */
  public static class Remove extends ArrayTransformOperation {
    public Remove(List<FieldValue> elements) {
      super(elements);
    }

    @Override
    protected ArrayValue apply(FieldValue previousValue) {
      ArrayList<FieldValue> result = coercedFieldValuesArray(previousValue);
      for (FieldValue element : getElements()) {
        result.removeAll(Collections.singleton(element));
      }
      return ArrayValue.fromList(result);
    }
  }
}
