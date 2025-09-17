/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.firestore.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A wrapper around a [ArrayList] that guarantees its immutability.
 */
public final class ImmutableArrayList<T> implements ImmutableList<T> {

  private final List<T> arrayList;

  private ImmutableArrayList(List<T> arrayList, boolean ignoredDiscriminator) {
    this.arrayList = arrayList;
  }

  private ImmutableArrayList(ArrayList<T> arrayList) {
    this(Collections.unmodifiableList(arrayList), true);
  }

  @Override
  public T get(int index) {
    return arrayList.get(index);
  }

  @Override
  public boolean contains(T element) {
    return arrayList.contains(element);
  }

  @Override
  public boolean isEmpty() {
    return arrayList.isEmpty();
  }

  @NonNull
  @Override
  public Iterator<T> iterator() {
    return arrayList.iterator();
  }

  @Override
  public int size() {
    return arrayList.size();
  }

  @NonNull
  @Override
  public List<T> asUnmodifiableList() {
    return arrayList;
  }

  @NonNull
  @Override
  public ArrayList<T> toArrayList() {
    return new ArrayList<>(arrayList);
  }

  @NonNull
  public Builder<T> toBuilder() {
    return new Builder<>(arrayList);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (object instanceof ImmutableArrayList) {
      return arrayList.equals(((ImmutableArrayList<?>) object).arrayList);
    } else {
      return arrayList.equals(object);
    }
  }

  @Override
  public int hashCode() {
    return arrayList.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return arrayList.toString();
  }

  private static final ImmutableArrayList<Void> EMPTY =
      new ImmutableArrayList<>(Collections.emptyList(), true);

  public static <T> ImmutableArrayList<T> empty() {
    //noinspection unchecked
    return (ImmutableArrayList<T>) EMPTY;
  }

  @SafeVarargs
  public static <T> ImmutableArrayList<T> of(T... elements) {
    ArrayList<T> arrayList = new ArrayList<>(elements.length);
    Collections.addAll(arrayList, elements);
    return new ImmutableArrayList<>(arrayList);
  }

  @NonNull
  public static <T> ImmutableArrayList<T> copyOf(Collection<? extends T> elements) {
    return new ImmutableArrayList<>(new ArrayList<>(elements));
  }

  @NonNull
  public static <T> ImmutableArrayList<T> copyOf(Iterable<? extends T> elements) {
    if (elements instanceof Collection<?>) {
      return copyOf((Collection<? extends T>) elements);
    }
    ArrayList<T> hashSet = new ArrayList<>();
    for (T element : elements) {
      hashSet.add(element);
    }
    return new ImmutableArrayList<>(hashSet);
  }

  public static <T> ImmutableArrayList<T> adopt(ArrayList<T> arrayList) {
    return new ImmutableArrayList<>(arrayList);
  }

  public static final class Builder<T> {

    private ArrayList<T> arrayList;
    private ImmutableArrayList<T> immutableArrayList;

    private Builder(ArrayList<T> arrayList, boolean ignoredDiscriminator) {
      this.arrayList = arrayList;
    }

    public Builder() {
      this(new ArrayList<>(), true);
    }

    public Builder(int initialCapacity) {
      this(new ArrayList<>(initialCapacity), true);
    }

    public Builder(Collection<? extends T> collection) {
      this(new ArrayList<>(collection), true);
    }

    public Builder(Iterable<? extends T> iterable) {
      this(new ArrayList<>(arrayListFromIterable(iterable)), true);
    }

    public Builder(ImmutableCollection<T> immutableCollection) {
      this(immutableCollection.asUnmodifiableCollection());
    }

    public Builder(ImmutableArrayList<T> immutableArrayList) {
      this(immutableArrayList.toArrayList(), true);
    }

    private static <T> ArrayList<T> arrayListFromIterable(Iterable<? extends T> iterable) {
      ArrayList<T> arrayList = new ArrayList<>();
      for (T element : iterable) {
        arrayList.add(element);
      }
      return arrayList;
    }

    public ImmutableArrayList<T> build() {
      if (immutableArrayList == null) {
        immutableArrayList = new ImmutableArrayList<>(arrayList);
        arrayList = null;
      }
      return immutableArrayList;
    }

    public Builder<T> add(T element) {
      arrayList.add(element);
      return this;
    }

    public Builder<T> addAll(Collection<? extends T> elements) {
      arrayList.addAll(elements);
      return this;
    }

    public Builder<T> addAll(Iterable<? extends T> elements) {
      if (elements instanceof Collection) {
        return addAll((Collection<? extends T>) elements);
      }
      for (T element : elements) {
        arrayList.add(element);
      }
      return this;
    }

    public Builder<T> addAll(ImmutableCollection<T> elements) {
      arrayList.addAll(elements.asUnmodifiableCollection());
      return this;
    }

    public Builder<T> remove(T element) {
      arrayList.remove(element);
      return this;
    }

    public Builder<T> removeAll(Collection<? extends T> elements) {
      arrayList.removeAll(elements);
      return this;
    }

    public Builder<T> removeAll(Iterable<? extends T> elements) {
      if (elements instanceof Collection) {
        return removeAll((Collection<? extends T>) elements);
      }
      for (T element : elements) {
        arrayList.remove(element);
      }
      return this;
    }

    public Builder<T> removeAll(ImmutableCollection<T> elements) {
      arrayList.removeAll(elements.asUnmodifiableCollection());
      return this;
    }

    public T get(int index) {
      return arrayList != null ? arrayList.get(index) : immutableArrayList.get(index);
    }

    public boolean contains(T element) {
      return arrayList != null ? arrayList.contains(element) : immutableArrayList.contains(element);
    }

    public boolean isEmpty() {
      return arrayList != null ? arrayList.isEmpty() : immutableArrayList.isEmpty();
    }

    public int size() {
      return arrayList != null ? arrayList.size() : immutableArrayList.size();
    }
  }
}
