/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

private val FIREBASE_APP =
  """
    package com.google.firebase;
    
    public class FirebaseApp {
      public <T> T get(Class<T> t) {
        return null;
      }
      
      public static FirebaseApp getInstance() {
        return null;
      }
    }
"""
    .trimIndent()

class FirebaseAppGetDetectorTests : LintDetectorTest() {
  override fun getDetector(): Detector = FirebaseAppGetDetector()

  override fun getIssues(): MutableList<Issue> = mutableListOf(FirebaseAppGetDetector.ISSUE)

  fun test_app_get_from_getInstance_shouldNotFail() {
    lint()
      .files(
        java(FIREBASE_APP),
        java(
          """
            import com.google.firebase.FirebaseApp;
            
            public class Foo {
              public static Foo getInstance(FirebaseApp app) {
                return app.get(Foo.class);
              } 
            }
        """
            .trimIndent()
        )
      )
      .run()
      .expectClean()
  }

  fun test_app_get_from_getInstance_returningSubclass_shouldNotFail() {
    lint()
      .files(
        java(FIREBASE_APP),
        java(
          """
            import com.google.firebase.FirebaseApp;
            
            public class Foo {
              public static FooImpl getInstance(FirebaseApp app) {
                return app.get(FooImpl.class);
              } 
            }
            class FooImpl extends Foo {}
        """
            .trimIndent()
        )
      )
      .run()
      .expectClean()
  }

  fun test_app_get_from_getInstance_withWrongReturnType_shouldFail() {
    lint()
      .files(
        java(FIREBASE_APP),
        java(
          """
            import com.google.firebase.FirebaseApp;
            
            public class Foo {
              public static String getInstance(FirebaseApp app) {
                app.get(Foo.class);
                return "";
              } 
            }
        """
            .trimIndent()
        )
      )
      .run()
      .checkContains("Use of FirebaseApp#get(Class) is discouraged")
  }

  fun test_app_get_from_getInstance_shouldNotFail_kotlin() {
    lint()
      .files(
        java(FIREBASE_APP),
        kotlin(
          """
            import com.google.firebase.FirebaseApp
            
            class Foo {
              companion object {
                @JvmStatic
                val instance = FirebaseApp.getInstance().get(Foo::class.java)

                @JvmStatic
                fun getInstance(app: FirebaseApp) = app.get(Foo::class.java)
              }
            }
        """
            .trimIndent()
        )
      )
      .run()
      .expectClean()
  }

  fun test_app_get_from_getInstance_returningSubclass_shouldNotFail_kotlin() {
    lint()
      .files(
        java(FIREBASE_APP),
        kotlin(
          """
            import com.google.firebase.FirebaseApp
            
            class Foo {
              companion object {
                @JvmStatic
                val instance = FirebaseApp.getInstance().get(FooImpl::class.java)

                @JvmStatic
                fun getInstance(app: FirebaseApp) = app.get(FooImpl::class.java)
              }
            }
            class FooImpl : Foo()
        """
            .trimIndent()
        )
      )
      .run()
      .expectClean()
  }

  fun test_app_get_from_getInstance_withWrongReturnType_shouldFail_kotlin() {
    lint()
      .files(
        java(FIREBASE_APP),
        kotlin(
          """
            import com.google.firebase.FirebaseApp;
            
            class Foo {
              companion object {

                @JvmStatic
                fun getInstance(app: FirebaseApp) = "".also { app.get(Foo::class.java) }
              }
            }
        """
            .trimIndent()
        )
      )
      .run()
      .checkContains("Use of FirebaseApp#get(Class) is discouraged")
  }
}
