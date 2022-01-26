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

package com.google.firebase.testing.integ;

import org.junit.internal.Throwables;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class MultipleViolationsException extends Exception {
  private final List<Throwable> errors;

  private MultipleViolationsException(List<Throwable> errors) {
    this.errors = new ArrayList<>(errors);
  }

  @Override
  public String getMessage() {
    StringBuilder sb = new StringBuilder("There were " + errors.size() + " errors:");
    for (Throwable e : errors) {
      sb.append(String.format("%n  %s(%s)", e.getClass().getName(), e.getMessage()));
    }
    return sb.toString();
  }

  @Override
  public void printStackTrace() {
    for (Throwable e : errors) {
      e.printStackTrace();
    }
  }

  @Override
  public void printStackTrace(PrintStream s) {
    for (Throwable e : errors) {
      e.printStackTrace(s);
    }
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    for (Throwable e : errors) {
      e.printStackTrace(s);
    }
  }

  public static void assertEmpty(List<Throwable> errors) throws Exception {
    if (errors.isEmpty()) {
      return;
    }
    if (errors.size() == 1) {
      throw Throwables.rethrowAsException(errors.get(0));
    }
    throw new MultipleViolationsException(errors);
  }
}
