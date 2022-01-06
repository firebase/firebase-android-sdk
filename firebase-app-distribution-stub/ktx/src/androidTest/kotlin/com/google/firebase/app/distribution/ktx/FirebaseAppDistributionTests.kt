// Copyright 2021 Google LLC
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

package com.google.firebase.app.distribution.ktx

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.google.common.truth.Truth
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.ktx.Firebase
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class FirebaseAppDistributionTests {
    @Test
    fun appDistribution_default_returnsInstanceOfFirebaseAppDistribution() {
        Truth.assertThat(Firebase.appDistribution).isInstanceOf(FirebaseAppDistribution::class.java)
    }
}
