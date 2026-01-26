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

package com.google.firebase.perf.plugin.instrumentation.network.config;

import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.network.hook.UrlConnectionInstrumentation;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.URL;
import org.objectweb.asm.Type;

/**
 * Gets config information for Oracle Java function "URL#openConnection(Proxy)" that ASM will look
 * at for bytecode instrumentation.
 */
public class UrlConnectionOpenConnectionProxyIC extends NetworkObjectInstrumentationConfig {

  private static final String CLASS_NAME;
  private static final String METHOD_NAME;
  private static final String METHOD_DESC;

  static {
    try {
      CLASS_NAME = Type.getInternalName(URL.class);

      final Method openConnectionMethod =
          URL.class.getDeclaredMethod(/* name */ "openConnection", Proxy.class);

      METHOD_NAME = openConnectionMethod.getName();
      METHOD_DESC = Type.getType(openConnectionMethod).getDescriptor();

    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public UrlConnectionOpenConnectionProxyIC() {
    super(new UrlConnectionInstrumentation.Factory(), CLASS_NAME, METHOD_NAME, METHOD_DESC);
  }
}
