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

package com.google.firebase.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest

class ProviderAssignmentDetectorTests : LintDetectorTest() {
    override fun getDetector() = ProviderAssignmentDetector()

    override fun getIssues() = mutableListOf(
            ProviderAssignmentDetector.INVALID_PROVIDER_ASSIGNMENT)

    fun test_assignmentToClassField_shouldFail() {
        lint().files(java(providerSource()), java("""
            import com.google.firebase.inject.Provider;
            
            class Foo {
              private final String value;
              Foo(Provider<String> p) {
                this.value = p.get();
              }
            }
        """.trimIndent()))
                .run()
                .checkContains("Provider.get() assignment")
    }

    fun test_assignmentAndUseOfProvider_shouldSucceed() {
        lint().files(java(providerSource()), java("""
            import com.google.firebase.inject.Provider;
            
            class Foo {
              private final Provider<String> p;
              Foo(Provider<String> p) {
                this.p = p;
              }
              void foo() {
                String value = p.get();
              }
            }
        """.trimIndent()))
                .run()
                .expectClean()
    }

    fun test_assignmentFromAStoredProvider_shouldFail() {
        lint().files(java(providerSource()), java("""
            import com.google.firebase.inject.Provider;
            
            class Foo {
              private final Provider<String> p;
              private String value;
              Foo(Provider<String> p) {
                this.p = p;
              }
              void foo() {
                value = p.get();
              }
            }
        """.trimIndent()))
                .run()
                .checkContains("Provider.get() assignment")
    }

    fun test_assignmentToLocalVariable_shouldSucceed() {
        lint().files(java(providerSource()), java("""
            import com.google.firebase.inject.Provider;
            
            class Foo {
              Foo(Provider<String> p) {
                String value = p.get();
              }
            }
        """.trimIndent()))
                .run()
                .expectClean()
    }
}
