// Copyright 2019 Google LLC
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

package com.google.firebase.gradle.plugins;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class FirebaseStaticAnalysis {
  public Set<String> errorproneCheckProjects;
  public Set<String> androidLintCheckProjects;


  private boolean disableKotlinInteropLint;

  private final Set<Runnable> kotlinInteropLintDisabledSubscribers = new HashSet<>();

  public FirebaseStaticAnalysis() {
    this(ImmutableSet.of(":tools:errorprone"), ImmutableSet.of(":tools:lint"));
  }

  public FirebaseStaticAnalysis(Set<String> errorproneCheckProjects, Set<String> androidLintCheckProjects) {
    this.errorproneCheckProjects = errorproneCheckProjects;
    this.androidLintCheckProjects = androidLintCheckProjects;
  }

  /** Indicates whether Kotlin Interop Lint checks are enabled for public APIs of the library. */
  public void disableKotlinInteropLint() {
    if (disableKotlinInteropLint) {
      return;
    }
    disableKotlinInteropLint = true;
    for (Runnable subscription : kotlinInteropLintDisabledSubscribers) {
      subscription.run();
    }
  }

  void subscribeToKotlinInteropLintDisabled(Runnable subscription) {
    this.kotlinInteropLintDisabledSubscribers.add(subscription);
    if (disableKotlinInteropLint) {
      subscription.run();
    }
  }
}
