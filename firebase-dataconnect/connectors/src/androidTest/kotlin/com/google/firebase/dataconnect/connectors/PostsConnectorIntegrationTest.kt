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
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.dataconnect.*
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
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostsConnectorIntegrationTest {

  @get:Rule val testNameRule = TestName()
  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val firebaseAppFactory = TestFirebaseAppFactory()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val testName
    get() = this::class.qualifiedName + "." + testNameRule.methodName

  private val posts: PostsConnector by lazy {
    PostsConnector.getInstance(firebaseAppFactory.newInstance()).apply {
      dataConnect.useEmulator()
      dataConnectFactory.adoptInstance(dataConnect)
    }
  }

  @Test
  fun instance_ShouldBeAssociatedWithTheDefaultFirebaseApp() {
    val posts = PostsConnector.instance
    cleanupAfterTest(posts)

    assertThat(posts.dataConnect.app).isSameInstanceAs(Firebase.app)
  }

  @Test
  fun instance_ShouldAlwaysReturnTheSameObject() {
    val posts1 = PostsConnector.instance
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.instance
    cleanupAfterTest(posts2)
    val posts3 = PostsConnector.instance
    cleanupAfterTest(posts3)

    assertThat(posts1).isSameInstanceAs(posts2)
    assertThat(posts1).isSameInstanceAs(posts3)
  }

  @Test
  fun instance_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    val posts1 = PostsConnector.instance
    posts1.dataConnect.close()
    val posts2 = PostsConnector.instance
    cleanupAfterTest(posts2)

    assertThat(posts1).isNotSameInstanceAs(posts2)
    assertThat(posts1.dataConnect).isNotSameInstanceAs(posts2.dataConnect)
    assertThat(posts1.dataConnect.app).isSameInstanceAs(posts2.dataConnect.app)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldBeAssociatedWithTheGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()

    val posts1 = PostsConnector.getInstance(app1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2)
    cleanupAfterTest(posts2)

    assertThat(posts1.dataConnect.app).isSameInstanceAs(app1)
    assertThat(posts2.dataConnect.app).isSameInstanceAs(app2)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldAlwaysReturnTheSameObjectForAGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()

    val posts1 = PostsConnector.getInstance(app1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2)
    cleanupAfterTest(posts2)
    val posts1b = PostsConnector.getInstance(app1)
    cleanupAfterTest(posts1b)
    val posts2b = PostsConnector.getInstance(app2)
    cleanupAfterTest(posts2b)

    assertThat(posts1).isSameInstanceAs(posts1b)
    assertThat(posts2).isSameInstanceAs(posts2b)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()

    val posts1 = PostsConnector.getInstance(app1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2)
    cleanupAfterTest(posts2)
    posts1.dataConnect.close()
    posts2.dataConnect.close()
    val posts1b = PostsConnector.getInstance(app1)
    cleanupAfterTest(posts1b)
    val posts2b = PostsConnector.getInstance(app2)
    cleanupAfterTest(posts2b)

    assertThat(posts1).isNotSameInstanceAs(posts1b)
    assertThat(posts2).isNotSameInstanceAs(posts2b)
    assertThat(posts1.dataConnect.app).isSameInstanceAs(app1)
    assertThat(posts2.dataConnect.app).isSameInstanceAs(app2)
    assertThat(posts1b.dataConnect.app).isSameInstanceAs(app1)
    assertThat(posts2b.dataConnect.app).isSameInstanceAs(app2)
  }

  @Test
  fun getInstance_DataConnectSettings_ShouldBeAssociatedWithTheDefaultFirebaseAppAndGivenSettings() {
    // Clear the default `FirebaseDataConnect` instance in case it already exists with different
    // settings, which would cause the calls to `getInstance()` below to unexpectedly throw.
    PostsConnector.instance.dataConnect.close()
    val settings = randomDataConnectSettings()

    val posts = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts)

    assertThat(posts.dataConnect.app).isSameInstanceAs(Firebase.app)
    assertThat(posts.dataConnect.settings).isSameInstanceAs(settings)
  }

  @Test
  fun getInstance_DataConnectSettings_ShouldAlwaysReturnTheSameObject() {
    // Clear the default `FirebaseDataConnect` instance in case it already exists with different
    // settings, which would cause the calls to `getInstance()` below to unexpectedly throw.
    PostsConnector.instance.dataConnect.close()
    val settings = randomDataConnectSettings()

    val posts1 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts2)

    assertThat(posts1).isSameInstanceAs(posts2)
  }

  @Test
  fun getInstance_DataConnectSettings_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    // Clear the default `FirebaseDataConnect` instance in case it already exists with different
    // settings, which would cause the calls to `getInstance()` below to unexpectedly throw.
    PostsConnector.instance.dataConnect.close()
    val settings = randomDataConnectSettings()

    val posts1 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts1)
    posts1.dataConnect.close()
    val posts2 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts2)

    assertThat(posts1).isNotSameInstanceAs(posts2)
    assertThat(posts1.dataConnect.app).isSameInstanceAs(Firebase.app)
  }

  @Test
  fun getInstance_FirebaseApp_DataConnectSettings_ShouldBeAssociatedWithTheGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()
    val settings1 = randomDataConnectSettings()
    val settings2 = randomDataConnectSettings()

    val posts1 = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2)

    assertThat(posts1.dataConnect.app).isSameInstanceAs(app1)
    assertThat(posts2.dataConnect.app).isSameInstanceAs(app2)
    assertThat(posts1.dataConnect.settings).isSameInstanceAs(settings1)
    assertThat(posts2.dataConnect.settings).isSameInstanceAs(settings2)
  }

  @Test
  fun getInstance_FirebaseApp_DataConnectSettings_ShouldAlwaysReturnTheSameObjectForAGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()
    val settings1 = randomDataConnectSettings()
    val settings2 = randomDataConnectSettings()

    val posts1 = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2)
    val posts1b = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1b)
    val posts2b = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2b)

    assertThat(posts1).isSameInstanceAs(posts1b)
    assertThat(posts2).isSameInstanceAs(posts2b)
    assertThat(posts1.dataConnect.settings).isSameInstanceAs(settings1)
    assertThat(posts2.dataConnect.settings).isSameInstanceAs(settings2)
  }

  @Test
  fun getInstance_FirebaseApp_DataConnectSettings_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()
    val settings1 = randomDataConnectSettings()
    val settings2 = randomDataConnectSettings()

    val posts1 = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2)
    posts1.dataConnect.close()
    posts2.dataConnect.close()
    val posts1b = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1b)
    val posts2b = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2b)

    assertThat(posts1).isNotSameInstanceAs(posts1b)
    assertThat(posts2).isNotSameInstanceAs(posts2b)
    assertThat(posts1.dataConnect.app).isSameInstanceAs(app1)
    assertThat(posts2.dataConnect.app).isSameInstanceAs(app2)
    assertThat(posts1b.dataConnect.app).isSameInstanceAs(app1)
    assertThat(posts2b.dataConnect.app).isSameInstanceAs(app2)
  }

  @Test
  fun createCommentShouldAddACommentToThePost() = runTest {
    val postId = randomPostId()
    val postContent = randomPostContent()
    posts.createPost(id = postId, content = postContent)

    val comment1Id = randomCommentId()
    val comment1Content = randomPostContent()
    posts.createComment(id = comment1Id, content = comment1Content, postId = postId)

    val comment2Id = randomCommentId()
    val comment2Content = randomPostContent()
    posts.createComment(id = comment2Id, content = comment2Content, postId = postId)

    val queryResponse = posts.getPost(id = postId)
    assertWithMessage("queryResponse")
      .that(queryResponse.data.post)
      .isEqualTo(
        GetPost.Data.Post(
          content = postContent,
          comments =
            listOf(
              GetPost.Data.Post.Comment(id = comment1Id, content = comment1Content),
              GetPost.Data.Post.Comment(id = comment2Id, content = comment2Content),
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
    val postId = randomPostId()
    val postContent = randomPostContent()

    posts.createPost(id = postId, content = postContent)

    val querySubscription = posts.getPost.ref(id = postId).subscribe()
    val result = querySubscription.flow.first()
    assertWithMessage("result1.post.content")
      .that(result.result.getOrThrow().data.post?.content)
      .isEqualTo(postContent)
  }

  /**
   * Ensures that the [FirebaseDataConnect] instance encapsulated by the given [PostsConnector] is
   * closed when this test completes. This method should be called immediately after all calls of
   * [PostsConnector.getInstance] and [PostsConnector.instance] to ensure that the instance doesn't
   * leak into other tests.
   */
  private fun cleanupAfterTest(connector: PostsConnector) {
    dataConnectFactory.adoptInstance(connector.dataConnect)
  }

  private fun randomPostId() = "PostId_${testName}_${Random.nextAlphanumericString(length = 10)}"

  private fun randomPostContent() =
    "PostContent_${testName}_${Random.nextAlphanumericString(length = 40)}"

  private fun randomCommentId() =
    "CommentId_${testName}_${Random.nextAlphanumericString(length = 10)}"

  private fun randomHost() = "Host_" + testName + "_" + Random.nextAlphanumericString(length = 10)

  private fun randomDataConnectSettings() = DataConnectSettings(host = randomHost())
}
