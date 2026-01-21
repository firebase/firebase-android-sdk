/*
 * Copyright 2026 Google LLC
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
package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.randomlyInsertStructs
import com.google.firebase.dataconnect.testutil.randomlyInsertValue
import com.google.firebase.dataconnect.testutil.randomlyInsertValues
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStruct
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStructs
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValue
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValues
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.property.Arb
import io.kotest.property.PropertyContext

context(propertyContext: PropertyContext)
fun Struct.withRandomlyInsertedStruct(
  struct: Struct,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): Struct =
  withRandomlyInsertedStruct(
    struct,
    propertyContext.randomSource().random,
    generateKey = { propertyContext.run { structKeyArb.bind() } },
  )

context(propertyContext: PropertyContext)
fun Struct.withRandomlyInsertedStructs(
  structs: List<Struct>,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): Struct = withRandomlyInsertedStructs(
  structs,
  propertyContext.randomSource().random,
  generateKey = { propertyContext.run { structKeyArb.bind() } },
)

context(propertyContext: PropertyContext)
fun Struct.withRandomlyInsertedValue(
  value: Value,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): Struct = withRandomlyInsertedValue(
  value,
  propertyContext.randomSource().random,
  generateKey = { propertyContext.run { structKeyArb.bind() } },
)

context(propertyContext: PropertyContext)
fun Struct.withRandomlyInsertedValues(
  values: List<Value>,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): Struct = withRandomlyInsertedValues(
  values,
  propertyContext.randomSource().random,
  generateKey = { propertyContext.run { structKeyArb.bind() } },
)

context(propertyContext: PropertyContext)
fun Struct.Builder.randomlyInsertStruct(
  struct: Struct,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): DataConnectPath = randomlyInsertStruct(
  struct,
  propertyContext.randomSource().random,
  generateKey = { propertyContext.run { structKeyArb.bind() } },
)

context(propertyContext: PropertyContext)
fun Struct.Builder.randomlyInsertStructs(
  structs: List<Struct>,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): List<DataConnectPath> =
  randomlyInsertStructs(
    structs,
    propertyContext.randomSource().random,
    generateKey = { propertyContext.run { structKeyArb.bind() } },
  )

context(propertyContext: PropertyContext)
fun Struct.Builder.randomlyInsertValue(
  value: Value,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): DataConnectPath = randomlyInsertValue(
  value,
  propertyContext.randomSource().random,
  generateKey = { propertyContext.run { structKeyArb.bind() } },
)


context(propertyContext: PropertyContext)
fun Struct.Builder.randomlyInsertValues(
  values: List<Value>,
  structKeyArb: Arb<String> = Arb.proto.structKey(),
): List<DataConnectPath> = randomlyInsertValues(
  values,
  propertyContext.randomSource().random,
  generateKey = { propertyContext.run { structKeyArb.bind() } },
)

context(propertyContext: PropertyContext)
fun ListValue.withRandomlyInsertedValue(value: Value): ListValue = withRandomlyInsertedValue(value, propertyContext.randomSource().random)

context(propertyContext: PropertyContext)
fun ListValue.withRandomlyInsertedValues(values: List<Value>): ListValue = withRandomlyInsertedValues(values, propertyContext.randomSource().random)

context(propertyContext: PropertyContext)
fun ListValue.Builder.randomlyInsertValue(value: Value): DataConnectPath = randomlyInsertValue(value, propertyContext.randomSource().random)

context(propertyContext: PropertyContext)
fun ListValue.Builder.randomlyInsertValues(values: List<Value>): List<DataConnectPath> = randomlyInsertValues(values, propertyContext.randomSource().random)
