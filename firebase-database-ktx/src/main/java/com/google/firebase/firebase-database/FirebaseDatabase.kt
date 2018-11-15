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

package com.google.firebase.database

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp

/** Returns the [FirebaseDatabase] instance of the default [FirebaseApp]. */
inline val Firebase.database: FirebaseDatabase
  get() = FirebaseDatabase.getInstance()

/** Returns the [FirebaseDatabase] instance of a given [FirebaseApp]. */
inline val FirebaseApp.database: FirebaseDatabase
  get() = FirebaseDatabase.getInstance(this)


/** Used to marshall the data contained in this instance into a class of your choosing */
inline fun <reified T> MutableData.getData(): T? = getValue(T::class.java)


/** Returns the data contained in this snapshot as native types. */
inline fun <reified T> DataSnapshot.getData(): T? = getValue(T::class.java)
