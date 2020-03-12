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

package com.google.firebase.crashlytics.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

/** Immutable list implementation for use with AutoValue */
public final class ImmutableList<E> implements List<E>, RandomAccess {

  @NonNull
  public static <E> ImmutableList<E> from(E... elements) {
    return new ImmutableList<>(Arrays.asList(elements));
  }

  @NonNull
  public static <E> ImmutableList<E> from(@NonNull List<E> mutableList) {
    return new ImmutableList<>(mutableList);
  }

  private final List<E> immutableList;

  private ImmutableList(List<E> mutableList) {
    this.immutableList = Collections.unmodifiableList(mutableList);
  }

  @Override
  public int size() {
    return immutableList.size();
  }

  @Override
  public boolean isEmpty() {
    return immutableList.isEmpty();
  }

  @Override
  public boolean contains(@Nullable Object o) {
    return immutableList.contains(o);
  }

  @NonNull
  @Override
  public Iterator<E> iterator() {
    return immutableList.iterator();
  }

  @Nullable
  @Override
  public Object[] toArray() {
    return immutableList.toArray();
  }

  @Override
  public <T> T[] toArray(@Nullable T[] a) {
    return immutableList.toArray(a);
  }

  @Override
  public boolean add(@NonNull E e) {
    return immutableList.add(e);
  }

  @Override
  public boolean remove(@Nullable Object o) {
    return immutableList.remove(o);
  }

  @Override
  public boolean containsAll(@NonNull Collection<?> c) {
    return immutableList.containsAll(c);
  }

  @Override
  public boolean addAll(@NonNull Collection<? extends E> c) {
    return immutableList.addAll(c);
  }

  @Override
  public boolean addAll(int index, @NonNull Collection<? extends E> c) {
    return immutableList.addAll(index, c);
  }

  @Override
  public boolean removeAll(@NonNull Collection<?> c) {
    return immutableList.removeAll(c);
  }

  @Override
  public boolean retainAll(@NonNull Collection<?> c) {
    return immutableList.retainAll(c);
  }

  @Override
  public void clear() {
    immutableList.clear();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return immutableList.equals(o);
  }

  @Override
  public int hashCode() {
    return immutableList.hashCode();
  }

  @NonNull
  @Override
  public E get(int index) {
    return immutableList.get(index);
  }

  @NonNull
  @Override
  public E set(int index, @NonNull E element) {
    return immutableList.set(index, element);
  }

  @Override
  public void add(int index, @NonNull E element) {
    immutableList.add(index, element);
  }

  @Override
  public E remove(int index) {
    return immutableList.remove(index);
  }

  @Override
  public int indexOf(@Nullable Object o) {
    return immutableList.indexOf(o);
  }

  @Override
  public int lastIndexOf(@Nullable Object o) {
    return immutableList.lastIndexOf(o);
  }

  @NonNull
  @Override
  public ListIterator<E> listIterator() {
    return immutableList.listIterator();
  }

  @NonNull
  @Override
  public ListIterator<E> listIterator(int index) {
    return immutableList.listIterator(index);
  }

  @NonNull
  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return immutableList.subList(fromIndex, toIndex);
  }
}
