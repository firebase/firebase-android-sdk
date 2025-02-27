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

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PostsConnectorIntegrationTest : DataConnectIntegrationTestBase() {

  private val posts: PostsConnector by lazy {
    val firebaseApp = firebaseAppFactory.newInstance()
    val dataConnect = dataConnectFactory.newInstance(firebaseApp, PostsConnector.config)
    PostsConnector.getInstance(firebaseApp, dataConnect.settings).also {
      require(it.dataConnect === dataConnect)
    }
  }

  @Test
  fun instance_ShouldBeAssociatedWithTheDefaultFirebaseApp() {
    val posts = PostsConnector.instance
    cleanupAfterTest(posts)

    posts.dataConnect.app shouldBeSameInstanceAs Firebase.app
  }

  @Test
  fun instance_ShouldAlwaysReturnTheSameObject() {
    val posts1 = PostsConnector.instance
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.instance
    cleanupAfterTest(posts2)
    val posts3 = PostsConnector.instance
    cleanupAfterTest(posts3)

    assertSoftly {
      withClue("posts1===posts2") { posts1 shouldBeSameInstanceAs posts2 }
      withClue("posts1===posts3") { posts1 shouldBeSameInstanceAs posts3 }
    }
  }

  @Test
  fun instance_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    val posts1 = PostsConnector.instance
    posts1.dataConnect.close()
    val posts2 = PostsConnector.instance
    cleanupAfterTest(posts2)

    assertSoftly {
      withClue("posts1") { posts1 shouldNotBeSameInstanceAs posts2 }
      withClue("dataConnect") { posts1.dataConnect shouldNotBeSameInstanceAs posts2.dataConnect }
      withClue("dataConnect.app") {
        posts1.dataConnect.app shouldBeSameInstanceAs posts2.dataConnect.app
      }
    }
  }

  @Test
  fun getInstance_FirebaseApp_ShouldBeAssociatedWithTheGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()

    val posts1 = PostsConnector.getInstance(app1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2)
    cleanupAfterTest(posts2)

    assertSoftly {
      withClue("app1") { posts1.dataConnect.app shouldBeSameInstanceAs app1 }
      withClue("app2") { posts2.dataConnect.app shouldBeSameInstanceAs app2 }
    }
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

    assertSoftly {
      withClue("posts1") { posts1 shouldBeSameInstanceAs posts1b }
      withClue("posts2") { posts2 shouldBeSameInstanceAs posts2b }
    }
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

    assertSoftly {
      withClue("posts1") { posts1 shouldNotBeSameInstanceAs posts1b }
      withClue("posts2") { posts2 shouldNotBeSameInstanceAs posts2b }
      withClue("posts1.app") { posts1.dataConnect.app shouldBeSameInstanceAs app1 }
      withClue("posts2.app") { posts2.dataConnect.app shouldBeSameInstanceAs app2 }
      withClue("posts1b.app") { posts1b.dataConnect.app shouldBeSameInstanceAs app1 }
      withClue("posts2b.app") { posts2b.dataConnect.app shouldBeSameInstanceAs app2 }
    }
  }

  @Test
  fun getInstance_DataConnectSettings_ShouldBeAssociatedWithTheDefaultFirebaseAppAndGivenSettings() {
    // Clear the default `FirebaseDataConnect` instance in case it already exists with different
    // settings, which would cause the calls to `getInstance()` below to unexpectedly throw.
    PostsConnector.instance.dataConnect.close()
    val settings = Arb.dataConnect.dataConnectSettings().next(rs)

    val posts = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts)

    assertSoftly {
      withClue("app") { posts.dataConnect.app shouldBeSameInstanceAs Firebase.app }
      withClue("settings") { posts.dataConnect.settings shouldBeSameInstanceAs settings }
    }
  }

  @Test
  fun getInstance_DataConnectSettings_ShouldAlwaysReturnTheSameObject() {
    // Clear the default `FirebaseDataConnect` instance in case it already exists with different
    // settings, which would cause the calls to `getInstance()` below to unexpectedly throw.
    PostsConnector.instance.dataConnect.close()
    val settings = Arb.dataConnect.dataConnectSettings().next(rs)

    val posts1 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts2)

    posts1 shouldBeSameInstanceAs posts2
  }

  @Test
  fun getInstance_DataConnectSettings_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    // Clear the default `FirebaseDataConnect` instance in case it already exists with different
    // settings, which would cause the calls to `getInstance()` below to unexpectedly throw.
    PostsConnector.instance.dataConnect.close()
    val settings = Arb.dataConnect.dataConnectSettings().next(rs)

    val posts1 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts1)
    posts1.dataConnect.close()
    val posts2 = PostsConnector.getInstance(settings)
    cleanupAfterTest(posts2)

    assertSoftly {
      withClue("posts1") { posts1 shouldNotBeSameInstanceAs posts2 }
      withClue("posts1.app") { posts1.dataConnect.app shouldBeSameInstanceAs Firebase.app }
    }
  }

  @Test
  fun getInstance_FirebaseApp_DataConnectSettings_ShouldBeAssociatedWithTheGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()
    val settings1 = Arb.dataConnect.dataConnectSettings().next(rs)
    val settings2 = Arb.dataConnect.dataConnectSettings().next(rs)

    val posts1 = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2)

    assertSoftly {
      withClue("app1") { posts1.dataConnect.app shouldBeSameInstanceAs app1 }
      withClue("app2") { posts2.dataConnect.app shouldBeSameInstanceAs app2 }
      withClue("settings1") { posts1.dataConnect.settings shouldBeSameInstanceAs settings1 }
      withClue("settings2") { posts2.dataConnect.settings shouldBeSameInstanceAs settings2 }
    }
  }

  @Test
  fun getInstance_FirebaseApp_DataConnectSettings_ShouldAlwaysReturnTheSameObjectForAGivenFirebaseApp() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()
    val settings1 = Arb.dataConnect.dataConnectSettings().next(rs)
    val settings2 = Arb.dataConnect.dataConnectSettings().next(rs)

    val posts1 = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1)
    val posts2 = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2)
    val posts1b = PostsConnector.getInstance(app1, settings1)
    cleanupAfterTest(posts1b)
    val posts2b = PostsConnector.getInstance(app2, settings2)
    cleanupAfterTest(posts2b)

    assertSoftly {
      withClue("posts1") { posts1 shouldBeSameInstanceAs posts1b }
      withClue("posts2") { posts2 shouldBeSameInstanceAs posts2b }
      withClue("settings1") { posts1.dataConnect.settings shouldBeSameInstanceAs settings1 }
      withClue("settings2") { posts2.dataConnect.settings shouldBeSameInstanceAs settings2 }
    }
  }

  @Test
  fun getInstance_FirebaseApp_DataConnectSettings_ShouldReturnANewInstanceIfTheDataConnectIsClosed() {
    val app1 = firebaseAppFactory.newInstance()
    val app2 = firebaseAppFactory.newInstance()
    val settings1 = Arb.dataConnect.dataConnectSettings().next(rs)
    val settings2 = Arb.dataConnect.dataConnectSettings().next(rs)

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

    assertSoftly {
      withClue("posts1") { posts1 shouldNotBeSameInstanceAs posts1b }
      withClue("posts2") { posts2 shouldNotBeSameInstanceAs posts2b }
      withClue("posts1.app") { posts1.dataConnect.app shouldBeSameInstanceAs app1 }
      withClue("posts2.app") { posts2.dataConnect.app shouldBeSameInstanceAs app2 }
      withClue("posts1b.app") { posts1b.dataConnect.app shouldBeSameInstanceAs app1 }
      withClue("posts2b.app") { posts2b.dataConnect.app shouldBeSameInstanceAs app2 }
    }
  }

  @Test
  fun createCommentShouldAddACommentToThePost() = runTest {
    val postId = Arb.dataConnect.postId().next(rs)
    val postContent = Arb.dataConnect.postContent().next(rs)
    posts.createPost(id = postId, content = postContent)

    val comment1Id = "comment1Id_${Arb.alphanumericString().next(rs)}"
    val comment1Content = Arb.dataConnect.postContent().next(rs)
    posts.createComment(id = comment1Id, content = comment1Content, postId = postId)

    val comment2Id = "comment2Id_${Arb.alphanumericString().next(rs)}"
    val comment2Content = Arb.dataConnect.postContent().next(rs)
    posts.createComment(id = comment2Id, content = comment2Content, postId = postId)

    val queryResponse = posts.getPost(id = postId)
    queryResponse.data.post shouldBe
      GetPost.Data.Post(
        content = postContent,
        comments =
          listOf(
            GetPost.Data.Post.Comment(id = comment1Id, content = comment1Content),
            GetPost.Data.Post.Comment(id = comment2Id, content = comment2Content),
          )
      )
  }

  @Test
  fun getPostWithNonExistingId() = runTest {
    val queryResponse = posts.getPost(id = Arb.dataConnect.postId().next(rs))
    queryResponse.data.post.shouldBeNull()
  }

  @Test
  fun createPostThenGetPost() = runTest {
    val postId = Arb.dataConnect.postId().next(rs)
    val postContent = Arb.dataConnect.postContent().next(rs)

    posts.createPost(id = postId, content = postContent)

    val queryResponse = posts.getPost(id = postId)
    queryResponse.data.post shouldBe
      GetPost.Data.Post(content = postContent, comments = emptyList())
  }

  @Test
  fun subscribe() = runTest {
    val postId = Arb.dataConnect.postId().next(rs)
    val postContent = Arb.dataConnect.postContent().next(rs)

    posts.createPost(id = postId, content = postContent)

    val querySubscription = posts.getPost.ref(id = postId).subscribe()
    val subscriptionResult = querySubscription.flow.first()
    val queryResult = subscriptionResult.result.getOrNull().shouldNotBeNull()
    val post = queryResult.data.post.shouldNotBeNull()
    post.content shouldBe postContent
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

  @Suppress("UnusedReceiverParameter")
  private fun DataConnectArb.postId(): Arb<String> = Arb.alphanumericString(prefix = "postId_")

  @Suppress("UnusedReceiverParameter")
  private fun DataConnectArb.postContent(): Arb<String> =
    Arb.alphanumericString(prefix = "postContent_")
}
