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

package com.google.firebase.dataconnect.testutil

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import java.util.Date
import kotlin.time.Duration.Companion.minutes

class DataConnectTestAppCheckToken(
  private val token: String,
  private val expireTimeMillis: Long,
) : AppCheckToken() {
  override fun getToken(): String = token

  override fun getExpireTimeMillis(): Long = expireTimeMillis
}

class DataConnectTestCustomAppCheckProvider : AppCheckProvider {

  override fun getToken(): Task<AppCheckToken> {
    val expireTimeMillis = Date().time + 60.minutes.inWholeMilliseconds
    return Tasks.forResult(DataConnectTestAppCheckToken("zzyzx", expireTimeMillis))
  }
}

class DataConnectTestCustomAppCheckProviderFactory : AppCheckProviderFactory {
  override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
    return DataConnectTestCustomAppCheckProvider()
  }
}
