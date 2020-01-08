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

package com.google.firebase.database.core.utilities;

public final class Pair<T, U> {

  private final T first;
  private final U second;

  public Pair(T first, U second) {
    this.first = first;
    this.second = second;
  }

  public T getFirst() {
    return first;
  }

  public U getSecond() {
    return second;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Pair pair = (Pair) o;

    if (first != null ? !first.equals(pair.first) : pair.first != null) {
      return false;
    }
    if (second != null ? !second.equals(pair.second) : pair.second != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = first != null ? first.hashCode() : 0;
    result = 31 * result + (second != null ? second.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Pair(" + first + "," + second + ")";
  }
}
