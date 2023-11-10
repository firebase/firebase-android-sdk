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
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(FlowPreview::class)
@RunWith(AndroidJUnit4::class)
class PostsTest {

  @get:Rule val dataConnectFactory = TestDataConnectFactory()
  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @Test
  fun getPostWithNonExistingId() {
    val dc = dataConnectFactory.newInstance(service = "local")
    runBlocking {
      val queryResponse = dc.queries.getPost.execute(id = UUID.randomUUID().toString())
      assertWithMessage("queryResponse").that(queryResponse).isNull()
    }
  }

  @Test
  fun createPostThenGetPost() {
    val dc = dataConnectFactory.newInstance(service = "local")

    val postId = UUID.randomUUID().toString()
    val postContent = Random.Default.nextLong().toString(30)

    runBlocking {
      dc.mutations.createPost.execute(id = postId, content = postContent)

      val queryResponse = dc.queries.getPost.execute(id = postId)
      assertWithMessage("queryResponse")
        .that(queryResponse?.post)
        .isEqualTo(GetPostQuery.Result.Post(content = postContent, comments = emptyList()))
    }
  }

  @Test
  fun subscribe() {
    val dc = dataConnectFactory.newInstance(service = "local")

    val postId1 = UUID.randomUUID().toString()
    val postContent1 = Random.Default.nextLong().absoluteValue.toString(30)
    val postId2 = UUID.randomUUID().toString()
    val postContent2 = Random.Default.nextLong().absoluteValue.toString(30)

    runBlocking {
      dc.mutations.createPost.execute(id = postId1, content = postContent1)
      dc.mutations.createPost.execute(id = postId2, content = postContent2)

      val querySubscription = dc.queries.getPost.subscribe(id = postId1)
      assertWithMessage("lastResult 0").that(querySubscription.lastResult).isNull()

      val result1 = querySubscription.flow.timeout(5.seconds).first()
      assertWithMessage("result1.isSuccess").that(result1.result.isSuccess).isTrue()
      assertWithMessage("result1.post.content")
        .that(result1.result.getOrThrow()?.post?.content)
        .isEqualTo(postContent1)

      assertWithMessage("lastResult 1").that(querySubscription.lastResult).isEqualTo(result1)

      val flow2Job = async { querySubscription.flow.timeout(5.seconds).take(2).toList() }

      querySubscription.update { id = postId2 }

      val results2 = flow2Job.await()
      assertWithMessage("results2.size").that(results2.size).isEqualTo(2)
      assertWithMessage("results2[0].isSuccess").that(results2[0].result.isSuccess).isTrue()
      assertWithMessage("results2[1].isSuccess").that(results2[1].result.isSuccess).isTrue()
      assertWithMessage("results2[0].post.content")
        .that(results2[0].result.getOrThrow()?.post?.content)
        .isEqualTo(postContent1)
      assertWithMessage("results2[1].post.content")
        .that(results2[1].result.getOrThrow()?.post?.content)
        .isEqualTo(postContent2)

      assertWithMessage("lastResult 2").that(querySubscription.lastResult).isEqualTo(results2[1])
    }
  }
}
