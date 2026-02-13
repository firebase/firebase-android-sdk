/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin.instrumentation;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/** Unit tests for {@link InstrumentationVisitor}. */
public class InstrumentationVisitorTest {

  // LINT.IfChange(asm_api_Version)
  private static final int CURRENT_ASM_API_VERSION = Opcodes.ASM9;

  // LINT.ThenChange()

  @Test
  public void instrumentationVisitorAsmApiVersion_matchesCurrentAsmApiVersion() {
    assertThat(Instrument.ASM_API_VERSION).isEqualTo(CURRENT_ASM_API_VERSION);
  }
}
