/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.removeEdgecases

/**
 * Returns an [Arb] that generates [Long] values suitable as a seed for a random number generator.
 *
 * This [Arb] has no edge cases. Removing edge cases ensures that specific seeds (like 0) aren't
 * chosen disproportionately often, which would lead to redundant test executions for a value that
 * is not itself under test.
 */
fun Arb.Companion.randomSeed(): Arb<Long> = long().removeEdgecases()
