/*
 * Copyright 2024 Google LLC
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
package com.google.firebase.dataconnect.minimaldemo

import com.google.firebase.dataconnect.minimaldemo.connector.*

data object values {
  val string = ""
  val int = 42
  val int64 = 42L
  val float = 1.1
}


suspend fun foo() {
  val connector = Ctry3q3tp6kzxConnector.instance
  val queryResult = connector.getItemsByValues.execute() {
    string = values.string
    this.int = int
    this.int64 = int64
    this.float = float
    this.boolean = boolean
    this.date = date
    this.timestamp = timestamp
    this.any = any
  }
  println("GetItemsByValues query returned: ${queryResult.data}")
}
