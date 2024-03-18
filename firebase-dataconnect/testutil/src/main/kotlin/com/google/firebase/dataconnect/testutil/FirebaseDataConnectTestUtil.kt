package com.google.firebase.dataconnect.testutil

import android.annotation.SuppressLint
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.RuntimeEnvironment

@SuppressLint("FirebaseUseExplicitDependencies")
fun newMockFirebaseApp(
  applicationId: String = Random.nextAlphanumericString(),
  projectId: String = Random.nextAlphanumericString()
): FirebaseApp {
  val firebaseApp = Mockito.mock(FirebaseApp::class.java)

  val firebaseOptions =
    FirebaseOptions.Builder().setApplicationId(applicationId).setProjectId(projectId).build()
  Mockito.`when`(firebaseApp.options).thenReturn(firebaseOptions)

  val firebaseDataConnectFactoryClass =
    Class.forName("com.google.firebase.dataconnect.core.FirebaseDataConnectFactory")
  val firebaseAppGetAnswer = Answer { invocation ->
    if (invocation.arguments.singleOrNull() !== firebaseDataConnectFactoryClass) {
      throw UnsupportedOperationException("arguments not supported: ${invocation.arguments}")
    }
    firebaseDataConnectFactoryClass.constructors
      .single()
      .newInstance(
        RuntimeEnvironment.getApplication(),
        firebaseApp,
        MoreExecutors.directExecutor(),
        MoreExecutors.directExecutor()
      )
  }
  Mockito.`when`(firebaseApp.get(firebaseDataConnectFactoryClass)).thenAnswer(firebaseAppGetAnswer)

  return firebaseApp
}
