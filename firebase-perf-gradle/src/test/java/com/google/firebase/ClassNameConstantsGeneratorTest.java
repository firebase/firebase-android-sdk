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

package com.google.firebase;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.perf.plugin.util.AsmString;
import org.junit.jupiter.api.Test;

/** Unit tests for the "buildSrc/../ClassNameConstantsGenerator.java". */
public class ClassNameConstantsGeneratorTest {

  @Test
  public void testGenerator_generatesHttpClientConstants_correspondingCorrectClassTypes() {
    assertThat(AsmString.CLASS_HTTP_CLIENT).isEqualTo("org/apache/http/client/HttpClient");

    assertThat(AsmString.CLASS_FIREBASE_PERF_HTTP_CLIENT)
        .isEqualTo("com/google/firebase/perf/network/FirebasePerfHttpClient");
  }

  @Test
  public void testGenerator_generatesOkHttpConstants_correspondingCorrectClassTypes() {
    assertThat(AsmString.CLASS_OKHTTP300_CALL).isEqualTo("okhttp3/Call");

    assertThat(AsmString.CLASS_FIREBASE_PERF_OKHTTP300_CLIENT)
        .isEqualTo("com/google/firebase/perf/network/FirebasePerfOkHttpClient");
  }

  @Test
  public void testGenerator_generatesUrlConnectionConstants_correspondingCorrectClassTypes() {
    assertThat(AsmString.CLASS_URL_CONNECTION).isEqualTo("java/net/URLConnection");
    assertThat(AsmString.CLASS_URL).isEqualTo("java/net/URL");

    assertThat(AsmString.CLASS_FIREBASE_PERF_URL_CONNECTION)
        .isEqualTo("com/google/firebase/perf/network/FirebasePerfUrlConnection");
  }

  @Test
  public void testGenerator_generatesFirebasePerformanceConstants_correspondingCorrectClassTypes() {
    assertThat(AsmString.CLASS_FIREBASE_PERFORMANCE)
        .isEqualTo("com/google/firebase/perf/FirebasePerformance");

    assertThat(AsmString.CLASS_FIREBASE_PERF_TRACE)
        .isEqualTo("com/google/firebase/perf/metrics/Trace");

    assertThat(AsmString.ANNOTATION_FIREBASE_PERF_ADD_TRACE)
        .isEqualTo("Lcom/google/firebase/perf/metrics/AddTrace;");
  }

  @Test
  public void testGenerator_generatesHttpClientConstants_correspondingExecuteWithRequestMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST)
        .isEqualTo(
            "(Lorg/apache/http/client/methods/HttpUriRequest;)" + "Lorg/apache/http/HttpResponse;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;"
                + "Lorg/apache/http/client/methods/HttpUriRequest;)Lorg/apache/http/HttpResponse;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithHostRequestMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST)
        .isEqualTo(
            "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;)"
                + "Lorg/apache/http/HttpResponse;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;"
                + "Lorg/apache/http/HttpRequest;)Lorg/apache/http/HttpResponse;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithRequestContextMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/client/methods/HttpUriRequest;"
                + "Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/HttpResponse;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;"
                + "Lorg/apache/http/client/methods/HttpUriRequest;"
                + "Lorg/apache/http/protocol/HttpContext;)"
                + "Lorg/apache/http/HttpResponse;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithRequestHandlerMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER)
        .isEqualTo(
            "(Lorg/apache/http/client/methods/HttpUriRequest;"
                + "Lorg/apache/http/client/ResponseHandler;)"
                + "Ljava/lang/Object;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;"
                + "Lorg/apache/http/client/methods/HttpUriRequest;"
                + "Lorg/apache/http/client/ResponseHandler;)Ljava/lang/Object;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithHostRequestContextMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;"
                + "Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/HttpResponse;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;"
                + "Lorg/apache/http/HttpRequest;Lorg/apache/http/protocol/HttpContext;)"
                + "Lorg/apache/http/HttpResponse;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithHostRequestHandlerMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER)
        .isEqualTo(
            "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;"
                + "Lorg/apache/http/client/ResponseHandler;)Ljava/lang/Object;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;"
                + "Lorg/apache/http/HttpRequest;Lorg/apache/http/client/ResponseHandler;)"
                + "Ljava/lang/Object;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithRequestHandlerContextMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/client/methods/HttpUriRequest;"
                + "Lorg/apache/http/client/ResponseHandler;Lorg/apache/http/protocol/HttpContext;)"
                + "Ljava/lang/Object;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;"
                + "Lorg/apache/http/client/methods/HttpUriRequest;"
                + "Lorg/apache/http/client/ResponseHandler;"
                + "Lorg/apache/http/protocol/HttpContext;)Ljava/lang/Object;");
  }

  @Test
  public void
      testGenerator_generatesHttpClientConstants_correspondingExecuteWithHostRequestHandlerContextMethod() {
    assertThat(AsmString.METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT)
        .isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;"
                + "Lorg/apache/http/client/ResponseHandler;Lorg/apache/http/protocol/HttpContext;)"
                + "Ljava/lang/Object;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT)
        .isEqualTo("execute");

    assertThat(
            AsmString
                .METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT)
        .isEqualTo(
            "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;"
                + "Lorg/apache/http/HttpRequest;Lorg/apache/http/client/ResponseHandler;"
                + "Lorg/apache/http/protocol/HttpContext;)Ljava/lang/Object;");
  }

  @Test
  public void testGenerator_generatesOkHttpConstants_correspondingCallExecuteMethod() {
    assertThat(AsmString.METHOD_OKHTTP300_CALL_EXECUTE).isEqualTo("execute");
    assertThat(AsmString.METHOD_DESC_OKHTTP300_CALL_EXECUTE).isEqualTo("()Lokhttp3/Response;");

    assertThat(AsmString.METHOD_FIREBASE_PERF_OKHTTP300_CALL_EXECUTE).isEqualTo("execute");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_OKHTTP300_CALL_EXECUTE)
        .isEqualTo("(Lokhttp3/Call;)Lokhttp3/Response;");
  }

  @Test
  public void testGenerator_generatesOkHttpConstants_correspondingCallEnqueueWithCallbackMethod() {
    assertThat(AsmString.METHOD_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK).isEqualTo("enqueue");

    assertThat(AsmString.METHOD_DESC_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK)
        .isEqualTo("(Lokhttp3/Callback;)V");

    assertThat(AsmString.METHOD_FIREBASE_PERF_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK)
        .isEqualTo("enqueue");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK)
        .isEqualTo("(Lokhttp3/Call;Lokhttp3/Callback;)V");
  }

  @Test
  public void
      testGenerator_generatesUrlConnectionConstants_correspondingInstrumentWithObjectMethod() {
    assertThat(AsmString.METHOD_FIREBASE_PERF_URL_CONNECTION_INSTRUMENT_WITH_OBJECT)
        .isEqualTo("instrument");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_INSTRUMENT_WITH_OBJECT)
        .isEqualTo("(Ljava/lang/Object;)Ljava/lang/Object;");
  }

  @Test
  public void testGenerator_generatesUrlConnectionConstants_correspondingGetContentWithUrlMethod() {
    assertThat(AsmString.METHOD_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL)
        .isEqualTo("getContent");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL)
        .isEqualTo("(Ljava/net/URL;)Ljava/lang/Object;");
  }

  @Test
  public void
      testGenerator_generatesUrlConnectionConstants_correspondingGetContentWithUrlClassArrayMethod() {
    assertThat(AsmString.METHOD_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL_CLASS_ARRAY)
        .isEqualTo("getContent");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL_CLASS_ARRAY)
        .isEqualTo("(Ljava/net/URL;[Ljava/lang/Class;)Ljava/lang/Object;");
  }

  @Test
  public void testGenerator_generatesUrlConnectionConstants_correspondingOpenStreamWithUrlMethod() {
    assertThat(AsmString.METHOD_FIREBASE_PERF_URL_CONNECTION_OPEN_STREAM_WITH_URL)
        .isEqualTo("openStream");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_OPEN_STREAM_WITH_URL)
        .isEqualTo("(Ljava/net/URL;)Ljava/io/InputStream;");
  }

  @Test
  public void
      testGenerator_generatesFirebasePerformanceConstants_correspondingStartTraceWithStringMethod() {
    assertThat(AsmString.METHOD_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING)
        .isEqualTo("startTrace");

    assertThat(AsmString.METHOD_DESC_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING)
        .isEqualTo("(Ljava/lang/String;)Lcom/google/firebase/perf/metrics/Trace;");
  }

  @Test
  public void
      testGenerator_generatesFirebasePerfTraceClassConstants_correspondingStartAndStopTraceMethods() {
    assertThat(AsmString.METHOD_FIREBASE_PERF_TRACE_START).isEqualTo("start");
    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_TRACE_START).isEqualTo("()V");

    assertThat(AsmString.METHOD_FIREBASE_PERF_TRACE_STOP).isEqualTo("stop");
    assertThat(AsmString.METHOD_DESC_FIREBASE_PERF_TRACE_STOP).isEqualTo("()V");
  }
}
