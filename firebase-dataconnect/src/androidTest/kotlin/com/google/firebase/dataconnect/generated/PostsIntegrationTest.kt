// Copyright 2023 Google LLC
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

package com.google.firebase.dataconnect.generated

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.dataconnect.FirebaseDataConnectSettings
import com.google.firebase.dataconnect.nextAlphanumericString
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostsIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val posts: PostsOperationSet by lazy {
    PostsOperationSet(
        app = Firebase.app,
        serviceId = "local",
        location = Random.nextAlphanumericString(),
        settings = FirebaseDataConnectSettings.emulator
      )
      .also { dataConnectFactory.adoptInstance(it.dataConnect) }
  }

  @Test
  fun getPostWithNonExistingId() = runTest {
    val queryResponse = posts.getPost.execute(id = Random.nextAlphanumericString())
    assertWithMessage("queryResponse").that(queryResponse.data.post).isNull()
  }

  @Test
  fun createPostThenGetPost() = runTest {
    val postId = Random.nextAlphanumericString()
    val postContent = Random.Default.nextLong().toString(30)

    posts.createPost.execute(id = postId, content = postContent)

    val queryResponse = posts.getPost.execute(id = postId)
    assertWithMessage("queryResponse")
      .that(queryResponse.data.post)
      .isEqualTo(GetPostQuery.Data.Post(content = postContent, comments = emptyList()))
  }

  @Test
  fun subscribe() = runTest {
    val postId1 = Random.nextAlphanumericString()
    val postContent1 = Random.nextAlphanumericString()
    val postId2 = Random.nextAlphanumericString()
    val postContent2 = Random.nextAlphanumericString()

    posts.createPost.execute(id = postId1, content = postContent1)
    posts.createPost.execute(id = postId2, content = postContent2)

    val querySubscription = posts.getPost.subscribe(id = postId1)
    assertWithMessage("lastResult 0").that(querySubscription.lastResult).isNull()

    val result1 = querySubscription.flow.first()
    assertWithMessage("result1.isSuccess").that(result1.errors).isEmpty()
    assertWithMessage("result1.post.content")
      .that(result1.data.post?.content)
      .isEqualTo(postContent1)

    assertWithMessage("lastResult 1").that(querySubscription.lastResult).isEqualTo(result1)

    val flow2Job = backgroundScope.async { querySubscription.flow.take(2).toList() }

    querySubscription.update { id = postId2 }

    val results2 = flow2Job.await()
    assertWithMessage("results2.size").that(results2.size).isEqualTo(2)
    assertWithMessage("results2[0].isSuccess").that(results2[0].errors).isEmpty()
    assertWithMessage("results2[1].isSuccess").that(results2[1].errors).isEmpty()
    assertWithMessage("results2[0].post.content")
      .that(results2[0].data.post?.content)
      .isEqualTo(postContent1)
    assertWithMessage("results2[1].post.content")
      .that(results2[1].data.post?.content)
      .isEqualTo(postContent2)

    assertWithMessage("lastResult 2").that(querySubscription.lastResult).isEqualTo(results2[1])
  }
}
