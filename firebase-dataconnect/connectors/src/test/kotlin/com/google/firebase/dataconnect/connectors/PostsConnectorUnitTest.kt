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

package com.google.firebase.dataconnect.connectors

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostsConnectorUnitTest {

  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "ex2bk4bks2",
      applicationIdKey = "2f2c3gdydn",
      projectIdKey = "kzbqx23hhn"
    )

  private val posts by lazy { PostsConnector.getInstance(firebaseAppFactory.newInstance()) }

  @Test
  fun `getPost property should always return the same instance`() {
    val operation1 = posts.getPost
    val operation2 = posts.getPost

    assertThat(operation1).isSameInstanceAs(operation2)
  }

  @Test
  fun `createPost property should always return the same instance`() {
    val operation1 = posts.createPost
    val operation2 = posts.createPost

    assertThat(operation1).isSameInstanceAs(operation2)
  }

  @Test
  fun `createComment property should always return the same instance`() {
    val operation1 = posts.createComment
    val operation2 = posts.createComment

    assertThat(operation1).isSameInstanceAs(operation2)
  }
}
