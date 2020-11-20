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
package com.google.firebase.gradle.plugins

import groovy.lang.Closure

private val FAKE_THIS = Any()

/** Create a groovy closure backed by a lambda.  */
fun <T> closureOf(action: T.() -> Unit): Closure<T> {
    return object : Closure<T>(FAKE_THIS) {
        fun doCall(t: T) {
            t.action()
        }
    }
}
