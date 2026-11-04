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

package com.google.firebase.appdistribution.gradle

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.testing.http.MockHttpTransport
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import org.junit.rules.TemporaryFolder

class AuthenticatedHttpClientTest {
  @get:Rule val restoreSystemProperties = RestoreSystemProperties()
  @get:Rule val tempFolder = TemporaryFolder()

  private val mockTransport = MockHttpTransport()
  private val authenticatedHttpClient = AuthenticatedHttpClient(mockTransport)

  @Test
  fun testNewGetRequest() {
    val url = GenericUrl("http://foo.bar")
    val builder = authenticatedHttpClient.newGetRequest(url)
    val response = builder.execute()
    val request = response.request
    assertEquals(url, request.url)
    assertEquals("GET", request.requestMethod)
  }

  @Test
  fun testNewPostRequest() {
    val url = GenericUrl("http://foo.bar")
    val builder = authenticatedHttpClient.newPostRequest(url)
    val response = builder.execute()
    val request = response.request
    assertEquals(url, request.url)
    assertEquals("POST", request.requestMethod)
  }

  @Test
  fun testHttpsRequest_withoutCustomTrustStore_rejectsUntrustedServerCert() {
    val password = "testpassword"
    val keyStoreFile = generateLocalhostKeyStore(password)

    System.clearProperty("javax.net.ssl.trustStore")
    System.clearProperty("javax.net.ssl.trustStorePassword")

    withLocalHttpsServer(keyStoreFile, password) { serverUrl ->
      val client = AuthenticatedHttpClient(HttpRequestInitializer {})
      assertThrows(SSLHandshakeException::class.java) {
        client.newGetRequest(GenericUrl(serverUrl)).execute()
      }
    }
  }

  @Test
  fun testHttpsRequest_withCustomTrustStore_succeedsEndToEnd() {
    val password = "testpassword"
    val keyStoreFile = generateLocalhostKeyStore(password)

    System.setProperty("javax.net.ssl.trustStore", keyStoreFile.absolutePath)
    System.setProperty("javax.net.ssl.trustStorePassword", password)

    withLocalHttpsServer(keyStoreFile, password) { serverUrl ->
      val client = AuthenticatedHttpClient(HttpRequestInitializer {})
      val response = client.newGetRequest(GenericUrl(serverUrl)).execute()
      try {
        assertEquals(200, response.statusCode)
        assertEquals("ok", response.parseAsString())
      } finally {
        response.disconnect()
      }
    }
  }

  private fun generateLocalhostKeyStore(password: String): File {
    val keyStoreFile = File(tempFolder.root, "localhost.jks")
    val keytoolPath =
      Paths.get(System.getProperty("java.home"), "bin", "keytool").toFile().absolutePath
    val process =
      ProcessBuilder(
          keytoolPath,
          "-genkeypair",
          "-alias",
          "localhost",
          "-keyalg",
          "RSA",
          "-keysize",
          "2048",
          "-validity",
          "1",
          "-dname",
          "CN=localhost",
          "-ext",
          "SAN=dns:localhost,ip:127.0.0.1",
          "-storetype",
          "JKS",
          "-keystore",
          keyStoreFile.absolutePath,
          "-storepass",
          password,
          "-keypass",
          password,
        )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    assertTrue("keytool failed ($exitCode): $output", exitCode == 0)
    return keyStoreFile
  }

  private fun withLocalHttpsServer(
    keyStoreFile: File,
    password: String,
    block: (String) -> Unit,
  ) {
    val keyStore = KeyStore.getInstance("JKS")
    FileInputStream(keyStoreFile).use { input -> keyStore.load(input, password.toCharArray()) }

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(keyStore, password.toCharArray())

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.keyManagers, null, null)

    val server = HttpsServer.create(InetSocketAddress("localhost", 0), 0)
    server.httpsConfigurator = HttpsConfigurator(sslContext)
    server.createContext("/test") { exchange ->
      val body = "ok".toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { out -> out.write(body) }
    }
    server.start()
    try {
      block("https://localhost:${server.address.port}/test")
    } finally {
      server.stop(0)
    }
  }
}
