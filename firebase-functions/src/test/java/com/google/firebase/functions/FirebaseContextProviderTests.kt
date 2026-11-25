// Copyright 2026 Google LLC
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

package com.google.firebase.functions

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal
import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider
import java.util.concurrent.Executor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FirebaseContextProviderTests {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val iid = FakeFirebaseInstanceIdInternal()

  @Test
  fun getContext_withoutInstallationIdFlag_includesInstanceIdToken() {
    val callableContext = getContext(providerOf(iid))

    assertThat(callableContext.instanceIdToken).isEqualTo(IID_TOKEN)
    assertThat(iid.getTokenCalls).isEqualTo(1)
  }

  @Test
  fun getContext_withInstallationIdFlagFalse_includesInstanceIdToken() {
    setInstallationIdEnabledMetadata(false)

    val callableContext = getContext(providerOf(iid))

    assertThat(callableContext.instanceIdToken).isEqualTo(IID_TOKEN)
    assertThat(iid.getTokenCalls).isEqualTo(1)
  }

  @Test
  fun getContext_withInstallationIdFlagTrue_doesNotGetLegacyToken() {
    setInstallationIdEnabledMetadata(true)

    val callableContext = getContext(providerOf(iid))

    assertThat(callableContext.instanceIdToken).isNull()
    assertThat(iid.getTokenCalls).isEqualTo(0)
    assertThat(iid.getTokenTaskCalls).isEqualTo(0)
  }

  @Test
  fun getContext_whenPackageManagerThrows_includesInstanceIdToken() {
    val brokenContext =
      object : ContextWrapper(context) {
        override fun getPackageManager(): PackageManager =
          throw RuntimeException("Package manager has died")
      }

    val callableContext = getContext(providerOf(iid), brokenContext)

    assertThat(callableContext.instanceIdToken).isEqualTo(IID_TOKEN)
  }

  @Test
  fun getContext_withoutInstanceIdComponent_omitsInstanceIdToken() {
    val callableContext = getContext(providerOf(null))

    assertThat(callableContext.instanceIdToken).isNull()
  }

  private fun getContext(
    instanceId: Provider<FirebaseInstanceIdInternal>,
    applicationContext: Context = context,
  ): HttpsCallableContext {
    val contextProvider =
      FirebaseContextProvider(
        applicationContext,
        providerOf<InternalAuthProvider>(null),
        instanceId,
        Deferred<InteropAppCheckTokenProvider> {},
        DIRECT_EXECUTOR
      )
    val task: Task<HttpsCallableContext?> = contextProvider.getContext(false)!!
    assertThat(task.isComplete).isTrue()
    return task.result!!
  }

  private fun setInstallationIdEnabledMetadata(enabled: Boolean) {
    val packageInfo =
      shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
    packageInfo.applicationInfo.metaData =
      Bundle().apply { putBoolean("firebase_messaging_installation_id_enabled", enabled) }
  }

  private class FakeFirebaseInstanceIdInternal : FirebaseInstanceIdInternal {
    var getTokenCalls = 0
    var getTokenTaskCalls = 0

    override fun getId(): String = throw UnsupportedOperationException()

    override fun getToken(): String {
      getTokenCalls++
      return IID_TOKEN
    }

    override fun getTokenTask(): Task<String> {
      getTokenTaskCalls++
      return Tasks.forResult(IID_TOKEN)
    }

    override fun deleteToken(senderId: String, scope: String) =
      throw UnsupportedOperationException()

    override fun addNewTokenListener(listener: FirebaseInstanceIdInternal.NewTokenListener) =
      throw UnsupportedOperationException()
  }

  private companion object {
    const val IID_TOKEN = "iidToken"
    val DIRECT_EXECUTOR = Executor { it.run() }

    fun <T> providerOf(value: T?): Provider<T> = Provider { value }
  }
}
