package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.PostsConnector
import com.google.firebase.dataconnect.connectors.getPost
import com.google.firebase.dataconnect.testutil.newMockFirebaseApp
import kotlinx.coroutines.test.*
import org.bouncycastle.util.test.SimpleTest.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostsConnectorTest {

  @Test
  fun `getPost property should always return the same instance`() {
    val posts = PostsConnector.getInstance(newMockFirebaseApp())
    val operation1 = posts.getPost
    val operation2 = posts.getPost

    assertThat(operation1).isSameInstanceAs(operation2)
  }

  @Test
  fun `getPost query runs the query`() = runTest {
    val posts = PostsConnector.getInstance(newMockFirebaseApp())
    posts.dataConnect.useEmulator(host = "localhost")
    posts.getPost(id = "foo")
  }

  @Test
  fun `createPost property should always return the same instance`() {
    val posts = PostsConnector.getInstance(newMockFirebaseApp())
    val operation1 = posts.createPost
    val operation2 = posts.createPost

    assertThat(operation1).isSameInstanceAs(operation2)
  }

  @Test
  fun `createComment property should always return the same instance`() {
    val posts = PostsConnector.getInstance(newMockFirebaseApp())
    val operation1 = posts.createComment
    val operation2 = posts.createComment

    assertThat(operation1).isSameInstanceAs(operation2)
  }
}
