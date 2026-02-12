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

package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.variable
import com.google.firebase.firestore.testutil.IntegrationTestUtil
import com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor
import com.google.firebase.firestore.util.Util.autoId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubqueryIntegrationTest {
  private lateinit var db: FirebaseFirestore

  @Before
  fun setUp() {
    // org.junit.Assume.assumeTrue(
    //   "Skip SubqueryIntegrationTest on prod",
    //   IntegrationTestUtil.isRunningAgainstEmulator()
    // )
    org.junit.Assume.assumeTrue(
      "Skip SubqueryIntegrationTest on standard backend",
      IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE
    )

    // Using testFirestore() ensures we get a uniquely configured instance if needed, 
    // but typically we want a clean DB reference.
    // IntegrationTestUtil.testFirestore() is standard.
    val collRef = IntegrationTestUtil.testCollection()
    db = collRef.firestore
  }

  @After
  fun tearDown() {
    IntegrationTestUtil.tearDown()
  }

  @Test
  fun testSubquery() {
    val reviewCollectionId = autoId()
    val reviewerCollectionId = autoId()

    // Setup reviewers
    val reviewersCollection = db.collection(reviewerCollectionId)
    val r1 = reviewersCollection.document("r1")
    waitFor(r1.set(mapOf("name" to "reviewer1")))

    // Setup reviews
    // Using collectionGroup requires consistent collection ID across hierarchy or just any collection with that ID.
    // We'll create a top-level collection with the random ID for simplicity.
    val reviewsRef = db.collection(reviewCollectionId)
    
    // Store author as a DocumentReference to match __name__ which is a Reference.
    waitFor(reviewsRef.document("run1_1").set(mapOf("author" to r1, "rating" to 5)))
    waitFor(reviewsRef.document("run1_2").set(mapOf("author" to r1, "rating" to 3)))

    // Construct subquery
    // Find reviews where author matches the variable 'author'
    val subquery = db.pipeline().collectionGroup(reviewCollectionId)
        .where(equal("author", variable("author")))
        .aggregate(field("rating").average().alias("avg_rating"))

    // Construct main pipeline
    val pipeline = db.pipeline().collection(reviewerCollectionId)
        .define(field("__name__").alias("author"))
        .addFields(subquery.toScalarExpression().alias("avg_review"))

    // Execute
    val results = waitFor(pipeline.execute())
    
    // Check results
    assertThat(results).hasSize(1)
    val doc = results.first()
    assertThat(doc.get("avg_review")).isEqualTo(4.0)
  }
}
