/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.perf.plugin.util;

/**
 * Source for the AsmString constants which is generated via "AsmStringGeneratorTask" Gradle task.
 */
public interface AsmString {

  ////////////// Apache HttpClient and corresponding Firebase Perf SDK class names ///////////////

  final String CLASS_HTTP_CLIENT = "org/apache/http/client/HttpClient";
  final String CLASS_FIREBASE_PERF_HTTP_CLIENT =
      "com/google/firebase/perf/network/FirebasePerfHttpClient";

  ///////////////////// OkHttp and corresponding Firebase Perf SDK class names ///////////////////

  final String CLASS_OKHTTP300_CALL = "okhttp3/Call";
  final String CLASS_FIREBASE_PERF_OKHTTP300_CLIENT =
      "com/google/firebase/perf/network/FirebasePerfOkHttpClient";

  ////// Oracle Java (URL & UrlConnection) and corresponding Firebase Perf SDK class names ///////

  final String CLASS_URL_CONNECTION = "java/net/URLConnection";
  final String CLASS_URL = "java/net/URL";
  final String CLASS_FIREBASE_PERF_URL_CONNECTION =
      "com/google/firebase/perf/network/FirebasePerfUrlConnection";

  //////////////////////////////// Firebase Perf SDK class names /////////////////////////////////

  final String CLASS_FIREBASE_PERFORMANCE = "com/google/firebase/perf/FirebasePerformance";
  final String CLASS_FIREBASE_PERF_TRACE = "com/google/firebase/perf/metrics/Trace";

  final String ANNOTATION_FIREBASE_PERF_ADD_TRACE = "Lcom/google/firebase/perf/metrics/AddTrace;";

  ////////////// Apache HttpClient and corresponding Firebase Perf SDK method names //////////////

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST =
      "(Lorg/apache/http/client/methods/HttpUriRequest;)Lorg/apache/http/HttpResponse;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/client/methods/HttpUriRequest;)Lorg/apache/http/HttpResponse;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST =
      "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;)Lorg/apache/http/HttpResponse;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;)Lorg/apache/http/HttpResponse;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT =
      "(Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/HttpResponse;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_CONTEXT =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/HttpResponse;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER =
      "(Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/client/ResponseHandler;)Ljava/lang/Object;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/client/ResponseHandler;)Ljava/lang/Object;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT =
      "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/HttpResponse;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_CONTEXT =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/HttpResponse;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER =
      "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/client/ResponseHandler;)Ljava/lang/Object;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/client/ResponseHandler;)Ljava/lang/Object;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT =
      "(Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/client/ResponseHandler;Lorg/apache/http/protocol/HttpContext;)Ljava/lang/Object;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT = "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_REQUEST_HANDLER_CONTEXT =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/client/ResponseHandler;Lorg/apache/http/protocol/HttpContext;)Ljava/lang/Object;";

  final String METHOD_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT = "execute";
  final String METHOD_DESC_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT =
      "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/client/ResponseHandler;Lorg/apache/http/protocol/HttpContext;)Ljava/lang/Object;";
  final String METHOD_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT =
      "execute";
  final String METHOD_DESC_FIREBASE_PERF_HTTP_CLIENT_EXECUTE_WITH_HOST_REQUEST_HANDLER_CONTEXT =
      "(Lorg/apache/http/client/HttpClient;Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/client/ResponseHandler;Lorg/apache/http/protocol/HttpContext;)Ljava/lang/Object;";

  /////////////////// OkHttp and corresponding Firebase Perf SDK method names ////////////////////

  final String METHOD_OKHTTP300_CALL_EXECUTE = "execute";
  final String METHOD_DESC_OKHTTP300_CALL_EXECUTE = "()Lokhttp3/Response;";
  final String METHOD_FIREBASE_PERF_OKHTTP300_CALL_EXECUTE = "execute";
  final String METHOD_DESC_FIREBASE_PERF_OKHTTP300_CALL_EXECUTE =
      "(Lokhttp3/Call;)Lokhttp3/Response;";

  final String METHOD_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK = "enqueue";
  final String METHOD_DESC_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK = "(Lokhttp3/Callback;)V";
  final String METHOD_FIREBASE_PERF_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK = "enqueue";
  final String METHOD_DESC_FIREBASE_PERF_OKHTTP300_CALL_ENQUEUE_WITH_CALLBACK =
      "(Lokhttp3/Call;Lokhttp3/Callback;)V";

  /////// Firebase Perf SDK method names for Oracle Java (URL and UrlConnection) functions ///////

  final String METHOD_FIREBASE_PERF_URL_CONNECTION_INSTRUMENT_WITH_OBJECT = "instrument";
  final String METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_INSTRUMENT_WITH_OBJECT =
      "(Ljava/lang/Object;)Ljava/lang/Object;";

  final String METHOD_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL = "getContent";
  final String METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL =
      "(Ljava/net/URL;)Ljava/lang/Object;";

  final String METHOD_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL_CLASS_ARRAY = "getContent";
  final String METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL_CLASS_ARRAY =
      "(Ljava/net/URL;[Ljava/lang/Class;)Ljava/lang/Object;";

  final String METHOD_FIREBASE_PERF_URL_CONNECTION_OPEN_STREAM_WITH_URL = "openStream";
  final String METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_OPEN_STREAM_WITH_URL =
      "(Ljava/net/URL;)Ljava/io/InputStream;";

  //////////////////////////////// Firebase Perf SDK method names ////////////////////////////////

  final String METHOD_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING = "startTrace";
  final String METHOD_DESC_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING =
      "(Ljava/lang/String;)Lcom/google/firebase/perf/metrics/Trace;";

  final String METHOD_FIREBASE_PERF_TRACE_START = "start";
  final String METHOD_DESC_FIREBASE_PERF_TRACE_START = "()V";

  final String METHOD_FIREBASE_PERF_TRACE_STOP = "stop";
  final String METHOD_DESC_FIREBASE_PERF_TRACE_STOP = "()V";
}
