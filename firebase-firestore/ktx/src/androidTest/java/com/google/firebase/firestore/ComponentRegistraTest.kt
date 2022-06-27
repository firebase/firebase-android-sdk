package com.google.firebase.firestore

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.testutil.testCollection
import com.google.firebase.firestore.testutil.waitFor
import com.google.firebase.ktx.Firebase
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.junit.Test

class ComponentRegistraTest {
    @Test
    fun test_for_component_registrar() {
        val db: FirebaseFirestore = Firebase.firestore
        //        val reg = db.instanceRegistry as FirestoreMultiDbComponent
        //        val myInterface = reg.mapEncoderProvider.get()
        //        val str = myInterface.print("xxx")

        //        Log.d("TestLog", str.toString())

        @Serializable data class Test(val a: Int = 100)

        @Serializable
        class Student {
            var name: String? = null
            var id: Int? = null
            var test: Test? = null
            @Transient
            var transparent: Long? = 123456789
        }

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        val student = Student()
        student.name = "foo-bar"
        student.id = 99
        student.test = Test(88)
        student.transparent = 100L
        docRefKotlin.set(student)

        val annotations = student.javaClass.annotations.asList()
//        val bbb: Boolean = student.javaClass.isAnnotationPresent(Serializable.class)
//        Log.d("TestLog", bbb.toString())

        for (a in annotations) {
            val the_same: Boolean = (a.annotationClass == Serializable::class)
            Log.d("TestLog", the_same.toString())
            Log.d("TestLog", a.annotationClass.simpleName)
        }

        val actual = waitFor(docRefKotlin.get()).data
        Log.d("TestLog", actual.toString())
    }
}
