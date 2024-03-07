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

package com.google.firebase.dataconnect.connectors

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostsConnectorIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val firebaseAppFactory = TestFirebaseAppFactory()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val posts: PostsConnector by lazy {
    PostsConnector.getInstance(firebaseAppFactory.newInstance()).apply {
      dataConnect.useEmulator()
      dataConnectFactory.adoptInstance(dataConnect)
    }
  }

  @Test
  fun createCommentShouldAddACommentToThePost() = runTest {
    val postId = randomPostId()
    val postContent = randomPostContent()
    posts.createPost(id = postId, content = postContent)

    val comment1Content = randomPostContent()
    posts.createComment(content = comment1Content, postId = postId)

    val comment2Content = randomPostContent()
    posts.createComment(content = comment2Content, postId = postId)

    val queryResponse = posts.getPost(id = postId)
    assertWithMessage("queryResponse")
      .that(queryResponse.data.post)
      .isEqualTo(
        GetPost.Data.Post(
          content = postContent,
          comments =
            listOf(
              GetPost.Data.Post.Comment(id = null, content = comment1Content),
              GetPost.Data.Post.Comment(id = null, content = comment2Content),
            )
        )
      )
  }

  @Test
  fun getPostWithNonExistingId() = runTest {
    val queryResponse = posts.getPost(id = randomPostId())
    assertWithMessage("queryResponse").that(queryResponse.data.post).isNull()
  }

  @Test
  fun createPostThenGetPost() = runTest {
    val postId = randomPostId()
    val postContent = randomPostContent()

    posts.createPost(id = postId, content = postContent)

    val queryResponse = posts.getPost(id = postId)
    assertWithMessage("queryResponse")
      .that(queryResponse.data.post)
      .isEqualTo(GetPost.Data.Post(content = postContent, comments = emptyList()))
  }

  @Test
  fun subscribe() = runTest {
    val postId1 = randomPostId()
    val postContent1 = randomPostContent()
    val postId2 = randomPostId()
    val postContent2 = randomPostContent()

    posts.createPost(id = postId1, content = postContent1)
    posts.createPost(id = postId2, content = postContent2)

    val querySubscription = posts.getPost.ref(id = postId1).subscribe()
    assertWithMessage("lastResult 0").that(querySubscription.lastResult).isNull()

    val result1 = querySubscription.resultFlow.first()
    assertWithMessage("result1.post.content")
      .that(result1.data.post?.content)
      .isEqualTo(postContent1)

    assertWithMessage("lastResult 1").that(querySubscription.lastResult).isEqualTo(result1)

    val flow2Job = backgroundScope.async { querySubscription.resultFlow.take(2).toList() }

    querySubscription.update(GetPost.Variables(id = postId2))

    val results2 = flow2Job.await()
    assertWithMessage("results2.size").that(results2.size).isEqualTo(2)
    assertWithMessage("results2[0].post.content")
      .that(results2[0].data.post?.content)
      .isEqualTo(postContent1)
    assertWithMessage("results2[1].post.content")
      .that(results2[1].data.post?.content)
      .isEqualTo(postContent2)

    assertWithMessage("lastResult 2").that(querySubscription.lastResult).isEqualTo(results2[1])
  }

  private companion object {
    fun randomPostId() = "PostId_" + Random.nextAlphanumericString(length = 10)
    fun randomPostContent() = "PostContent_" + Random.nextAlphanumericString(length = 40)
  }
}
