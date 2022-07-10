package com.google.firebase.firestore

import com.google.common.truth.Truth
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import com.google.firebase.firestore.testutil.testCollection
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Test

class ConvertJavaToKotlinSerializableIntegrationTests {

    @Test
    fun encoding_DocumentReference_is_supported() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        @Serializable
        data class TestClass(
            val name: String,
            val java: JavaGeoPoint,
            var docId: JavaDocumentReference
        )

        @Serializable
        data class TestClassOld(
            val name: String,
            val java: JavaGeoPoint,
            @Contextual var docId: DocumentReference
        )

        val javaDocRef: JavaDocumentReference =
            JavaDocumentReference(docRefKotlin.key, docRefKotlin.firestore)
        val myObj = TestClass("pig", JavaGeoPoint(10.0, 90.0), javaDocRef)
        val pojoObj = TestClassOld("pig", JavaGeoPoint(10.0, 90.0), docRefKotlin)
        val actual = encodeToMap(myObj)
        val expected = encodeToMap(pojoObj)
        Truth.assertThat(actual).containsExactlyEntriesIn(expected)

        //        @Serializable
        //        data class DocumentIdOnDocRefField(@Contextual var docId: DocumentReference? =
        // null)
        //
        //        val docRefObject = DocumentIdOnDocRefField().apply { docId = docRefKotlin }
        //        docRefKotlin.setData(docRefObject)
        //        docRefPOJO.set(docRefObject)
        //        val expected = waitFor(docRefPOJO.get()).data
        //        val actual = waitFor(docRefKotlin.get()).data
        //        Truth.assertThat(expected).containsExactlyEntriesIn(actual)
    }
}
