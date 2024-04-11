package com.google.firebase.dataconnect.auth

import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.testutil.DelayedDeferred
import com.google.firebase.dataconnect.testutil.ImmediateDeferred
import com.google.firebase.dataconnect.testutil.UnavailableDeferred
import com.google.firebase.dataconnect.util.Listener
import com.google.firebase.internal.InternalTokenResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.*
import org.mockito.Mockito.*

@Suppress("ReplaceCallWithBinaryOperator")
class FirebaseAuthCredentialsProviderTest {

  @Mock(name = "mockInternalAuthProvider")
  private lateinit var mockInternalAuthProvider: InternalAuthProvider

  @Mock(name = "mockUserListener") private lateinit var mockUserListener: Listener<User>

  @Captor private lateinit var idTokenListenerCaptor: ArgumentCaptor<IdTokenListener>

  @Mock(name = "mockInternalTokenResult")
  private lateinit var mockInternalTokenResult: InternalTokenResult

  @Mock private lateinit var mockGetTokenResult: GetTokenResult

  @Mock private lateinit var mockGetTokenResult2: GetTokenResult

  private var testScope = TestScope()

  @Before
  fun prepare() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun `setChangeListener should be called with unauthenticated if provider is not available`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider = FirebaseAuthCredentialsProvider(UnavailableDeferred())
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User.UNAUTHENTICATED)
    }
  }

  @Test
  fun `setChangeListener should be called with unauthenticated user if uid is null`() {
    testScope.runTest {
      `when`(mockInternalAuthProvider.uid).thenReturn(null)
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User.UNAUTHENTICATED)
    }
  }

  @Test
  fun `setChangeListener should be called with authenticated user if uid is not null`() {
    testScope.runTest {
      `when`(mockInternalAuthProvider.uid).thenReturn("TestUID")
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User("TestUID"))
    }
  }

  @Test
  fun `setChangeListener should be called with unauthenticated user when provider with null uid becomes available`() {
    testScope.runTest {
      val delayedDeferredInternalAuthProvider = DelayedDeferred<InternalAuthProvider>()
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider)
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User.UNAUTHENTICATED)
      `when`(mockInternalAuthProvider.uid).thenReturn(null)
      delayedDeferredInternalAuthProvider.setInstance(mockInternalAuthProvider)
      verify(mockUserListener, times(2)).onValue(User.UNAUTHENTICATED)
    }
  }

  @Test
  fun `setChangeListener should be called with authenticated user when provider with authenticated user becomes available`() {
    testScope.runTest {
      val delayedDeferredInternalAuthProvider = DelayedDeferred<InternalAuthProvider>()
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider)
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User.UNAUTHENTICATED)
      `when`(mockInternalAuthProvider.uid).thenReturn("TestUID")
      delayedDeferredInternalAuthProvider.setInstance(mockInternalAuthProvider)
      verify(mockUserListener).onValue(User("TestUID"))
    }
  }

  @Test
  fun `setChangeListener should be called when IdTokenChanges`() {
    testScope.runTest {
      `when`(mockInternalAuthProvider.uid).thenReturn("TestUID1")
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      verify(mockInternalAuthProvider).addIdTokenListener(idTokenListenerCaptor.capture())
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User("TestUID1"))
      `when`(mockInternalAuthProvider.uid).thenReturn("TestUID2")
      idTokenListenerCaptor.value.onIdTokenChanged(mockInternalTokenResult)
      verify(mockUserListener).onValue(User("TestUID2"))
    }
  }

  @Test
  fun `removeChangeListener should stop notifying the listener`() {
    testScope.runTest {
      val delayedDeferredInternalAuthProvider = DelayedDeferred<InternalAuthProvider>()
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider)
      firebaseAuthCredentialsProvider.setChangeListener(mockUserListener)
      verify(mockUserListener).onValue(User.UNAUTHENTICATED)
      firebaseAuthCredentialsProvider.removeChangeListener()
      delayedDeferredInternalAuthProvider.setInstance(mockInternalAuthProvider)
      verifyNoMoreInteractions(mockUserListener)
    }
  }

  @Test
  fun `removeChangeListener should not throw if provider is not available`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider = FirebaseAuthCredentialsProvider(UnavailableDeferred())
      firebaseAuthCredentialsProvider.removeChangeListener()
    }
  }

  @Test
  fun `removeChangeListener should unregister the idTokenListener`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      verify(mockInternalAuthProvider).addIdTokenListener(idTokenListenerCaptor.capture())
      firebaseAuthCredentialsProvider.removeChangeListener()
      verify(mockInternalAuthProvider).removeIdTokenListener(idTokenListenerCaptor.value)
    }
  }

  @Test
  fun `token should throw if provider is not available`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider = FirebaseAuthCredentialsProvider(UnavailableDeferred())
      val token = firebaseAuthCredentialsProvider.getToken()
      Truth.assertThat(token).isEqualTo(null)
    }
  }

  @Test
  fun `token should throw if access token fails`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      val getAccessTokenException = Exception()
      `when`(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forException(getAccessTokenException))
      val token = firebaseAuthCredentialsProvider.getToken()
      Truth.assertThat(token).isEqualTo(null)
    }
  }

  @Test
  fun `token should return a token string if getAccessToken succeeds`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      `when`(mockGetTokenResult.token).thenReturn("TestToken")
      `when`(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult))
      val token = firebaseAuthCredentialsProvider.getToken()
      Truth.assertThat(token).isEqualTo("TestToken")
    }
  }

  @Test
  fun `token should not force refresh if invalidateToken is not called`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      `when`(mockGetTokenResult.token).thenReturn("TestToken")
      `when`(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult))
      firebaseAuthCredentialsProvider.getToken()
      verify(mockInternalAuthProvider).getAccessToken(false)
    }
  }

  @Test
  fun `invalidate token should cause token force refresh`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      `when`(mockGetTokenResult.token).thenReturn("TestToken")
      `when`(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult))
      firebaseAuthCredentialsProvider.invalidateToken()
      firebaseAuthCredentialsProvider.getToken()
      verify(mockInternalAuthProvider).getAccessToken(true)
    }
  }

  @Test
  fun `invalidateToken should only force refresh on the immediately following getToken invocation`() {
    testScope.runTest {
      val firebaseAuthCredentialsProvider =
        FirebaseAuthCredentialsProvider(ImmediateDeferred(mockInternalAuthProvider))
      `when`(mockGetTokenResult.token).thenReturn("TestToken")
      `when`(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult))
      firebaseAuthCredentialsProvider.invalidateToken()
      firebaseAuthCredentialsProvider.getToken()
      firebaseAuthCredentialsProvider.getToken()
      verify(mockInternalAuthProvider).getAccessToken(true)
      verify(mockInternalAuthProvider).getAccessToken(false)
    }
  }
}
