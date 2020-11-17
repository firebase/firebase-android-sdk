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

private fun annotationSource(): String {
    return """
        package com.google.firebase.annotations;

        @Inherited
        public @interface DeferredApi {}
    """.trimIndent()
}

private fun deferredSource(): String {
    return """
        package com.google.firebase.inject;
        import com.google.firebase.annotations.DeferredApi;

        public interface Deferred<T> {
        interface DeferredHandler<T> {
          @DeferredApi
          void handle(Provider<T> provider);
        }

        void whenAvailable(@NonNull DeferredHandler<T> handler);
      }
    """.trimIndent()
}

fun providerSource(): String {
    return """
        package com.google.firebase.inject;

        public interface Provider<T> {
          T get();
        }
    """.trimIndent()
}

private fun annotatedInterface() = """
            import com.google.firebase.annotations.DeferredApi;
            
            interface MyApi {
              @DeferredApi void myMethod();
            }
        """.trimIndent()

class DeferredApiDetectorTests : LintDetectorTest() {
    override fun getDetector() = DeferredApiDetector()

    override fun getIssues() = mutableListOf(DeferredApiDetector.INVALID_DEFERRED_API_USE)

    fun test_directUseOfDeferredApiMethodInConstructor_shouldFail() {
        lint().files(
                java(annotationSource()),
                java(annotatedInterface()),
                java("""
                     class UseOfMyApi {
                       UseOfMyApi(MyApi api) {
                         api.myMethod();
                       }
                     }
                     """.trimIndent()))
                .run()
                .checkContains("myMethod is only safe to call")
    }

    fun test_directUseOfDeferredApiMethodInMethodThroughField_shouldFail() {
        lint().files(
                java(annotationSource()),
                java(annotatedInterface()),
                java("""
                     class UseOfMyApi {
                       private final MyApi api;
                       UseOfMyApi(MyApi api) {
                         this.api = api;
                       }
                       
                       void foo() {
                         api.myMethod();
                       }
                     }
                     """.trimIndent()))
                .run()
                .checkContains("myMethod is only safe to call")
    }

    fun test_whenAvailableUseOfDeferredApiMethodThroughFieldInLambda_shouldSucceed() {
        lint().files(
                java(annotationSource()),
                java(deferredSource()),
                java(providerSource()),
                java(annotatedInterface()),
                java("""
                     import com.google.firebase.inject.Deferred;
                     class UseOfMyApi {
                       private final Deferred<MyApi> api;
                       UseOfMyApi(Deferred<MyApi> api) {
                         this.api = api;
                       }
                       void foo() {
                         api.whenAvailable(apiProvider -> 
                             apiProvider.get().myMethod());
                       }
                     }
                     """.trimIndent()))
                .run()
                .expectClean()
    }

    fun test_whenAvailableUseOfDeferredApiMethodThroughFieldInAnonymousClass_shouldSucceed() {
        lint().files(
                java(annotationSource()),
                java(deferredSource()),
                java(providerSource()),
                java(annotatedInterface()),
                java("""
                     import com.google.firebase.inject.Deferred;
                     import com.google.firebase.inject.Provider;
                     class UseOfMyApi {
                       private final Deferred<MyApi> api;
                       UseOfMyApi(Deferred<MyApi> api) {
                         this.api = api;
                       }
                       void foo() {
                         api.whenAvailable(new Deferred.DeferredHandler<MyApi>() {
                           @Override public void handle(Provider<MyApi> apiProvider) {
                             apiProvider.get().myMethod();
                           }
                         });
                       }
                     }
                     """.trimIndent()))
                .run()
                .expectClean()
    }

    fun test_whenAvailableUseOfDeferredApiMethodInConstructorInLambda_shouldSucceed() {
        lint().files(
                java(annotationSource()),
                java(deferredSource()),
                java(providerSource()),
                java(annotatedInterface()),
                java("""
                     import com.google.firebase.inject.Deferred;
                     class UseOfMyApi {
                       UseOfMyApi(Deferred<MyApi> api) {
                         api.whenAvailable(apiProvider -> 
                             apiProvider.get().myMethod());
                       }
                     }
                     """.trimIndent()))
                .run()
                .expectClean()
    }

    fun test_whenAvailableUseOfDeferredApiMethodThatCallsToAnotherDeferredApi_shouldSucceed() {
        lint().files(
                java(annotationSource()),
                java(deferredSource()),
                java(providerSource()),
                java(annotatedInterface()),
                java("""
                     import com.google.firebase.annotations.DeferredApi;
                     import com.google.firebase.inject.Deferred;
                     class UseOfMyApi {
                       UseOfMyApi(Deferred<MyApi> api) {
                         api.whenAvailable(apiProvider -> {
                             call(apiProvider.get());
                             });
                       }
                       
                       @DeferredApi
                       void call(MyApi api) {
                         api.myMethod();
                       }
                     }
                     """.trimIndent()))
                .run()
                .expectClean()
    }

    fun test_whenAvailableUseOfRegularMethodThatCallsToAnotherDeferredApi_shouldFail() {
        lint().files(
                java(annotationSource()),
                java(deferredSource()),
                java(providerSource()),
                java(annotatedInterface()),
                java("""
                     import com.google.firebase.annotations.DeferredApi;
                     import com.google.firebase.inject.Deferred;
                     class UseOfMyApi {
                       UseOfMyApi(Deferred<MyApi> api) {
                         api.whenAvailable(apiProvider -> {
                             call(apiProvider.get());
                             });
                       }

                       void call(MyApi api) {
                         api.myMethod();
                       }
                     }
                     """.trimIndent()))
                .run()
                .checkContains("myMethod is only safe to call")
    }
}
