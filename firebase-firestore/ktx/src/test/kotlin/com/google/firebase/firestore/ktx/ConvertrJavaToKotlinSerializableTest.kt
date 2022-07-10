package com.google.firebase.firestore.ktx

import com.google.firebase.firestore.JavaDocumentReference
import com.google.firebase.firestore.JavaGeoPoint
import com.google.firebase.firestore.javaDocumentReference
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlinx.serialization.Serializable

fun main() {

    println(JavaGeoPoint.serializer().descriptor)

//    @Serializable
//    data class TestClass(val name:String)
//    val myObj = TestClass("pig")
//    val res = encodeToMap(myObj)
//    println(myObj)

    val docRef = javaDocumentReference("docRefkey/123")

    @Serializable
    data class TestClass(val name:String, val java: JavaGeoPoint)
//    data class TestClass(val name:String, val java: JavaGeoPoint, val doc: JavaDocumentReference)
    val myObj = TestClass("pig", JavaGeoPoint(10.0, 90.0))
    val res = encodeToMap(myObj)
    println(res)


}