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
import com.google.firebase.perf.plugin.instrumentation.network.hook.HttpClientExecuteHostRequestHandlerContextInstrumentation;
import com.google.firebase.perf.plugin.util.AsmString;

/**
 * Gets config information for Apache function "HttpClient#execute(HttpHost, HttpRequest,
 * ResponseHandler, HttpContext)" that ASM will look at for bytecode instrumentation.
 */
public class HttpClientExecuteHostRequestHandlerContextIC
    extends NetworkObjectInstrumentationConfig {

  public HttpClientExecuteHostRequestHandlerContextIC() {
    super(
        new HttpClientExecuteHostRequestHandlerContextInstrumentation.Factory(),
        AsmString.CLASS_HTTP_CLIENT,
        AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT,
        AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT);
  }
}
