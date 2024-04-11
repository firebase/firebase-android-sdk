package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.randomPersonId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  @Test
  fun testOperationRequiredSignIn() = runTest {
    val auth = FirebaseAuth.getInstance()
    auth.useEmulator("10.0.2.2", 9099)
    withContext(Dispatchers.IO) {
      val task: Task<AuthResult> = auth.signInAnonymously()
      task.await()
    }

    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()
    personSchema.createPersonAuth(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPersonAuth(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPersonAuth(id = person3Id, name = "TestName3", age = 44).execute()

    val result = personSchema.getPersonAuth(id = person2Id).execute()

    Truth.assertThat(result.data.person?.name).isEqualTo("TestName2")
    Truth.assertThat(result.data.person?.age).isEqualTo(43)
  }
}
