@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class, TimestampSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable public data class DateVariantsKey(val id: String)

@Serializable public data class FooKey(val id: String)

@Serializable public data class Int64variantsKey(val id: String)

@Serializable public data class Nested1Key(val id: java.util.UUID)

@Serializable public data class Nested2Key(val id: java.util.UUID)

@Serializable public data class Nested3Key(val id: java.util.UUID)

@Serializable
public data class PrimaryKeyIsCompositeKey(val foo: Int, val bar: String, val baz: Boolean)

@Serializable public data class PrimaryKeyIsDateKey(val foo: java.util.Date)

@Serializable public data class PrimaryKeyIsFloatKey(val foo: Double)

@Serializable public data class PrimaryKeyIsIntKey(val foo: Int)

@Serializable public data class PrimaryKeyIsStringKey(val id: String)

@Serializable public data class PrimaryKeyIsTimestampKey(val foo: com.google.firebase.Timestamp)

@Serializable public data class PrimaryKeyIsUuidKey(val id: java.util.UUID)

@Serializable public data class StringVariantsKey(val id: String)

@Serializable public data class SyntheticIdKey(val id: java.util.UUID)

@Serializable public data class TimestampVariantsKey(val id: String)

@Serializable public data class UuidvariantsKey(val id: String)

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
