// Copyright 2022 Google LLC
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

package com.example

sealed interface Phrase {
  fun speak(): String
}

/**
 * A phrase used when one is entering a premise.
 *
 * Pronounced <b>he &#183; lo</b>
 *
 * @see Goodbye
 */
class Hello() : Phrase {
  fun sayHello() = "Hello"

  fun speak() = sayHello()
}

/**
 * A phrase used when one is leaving, or departing from a premise.
 *
 * Pronounced <b>good &#183; bi</b>
 *
 * @see Hello
 */
class Goodbye() : Phrase {
  fun sayGoodbye() = "Goodbye"

  fun speak() = sayGoodbye()
}

/**
 * An unprofessional way to greet a friend.
 *
 * @hide
 */
class Wasgood() : Phrase {
  fun speak() = "Wasgood"
}
// add stuff here for transform tests ig?
// build file maybe?
