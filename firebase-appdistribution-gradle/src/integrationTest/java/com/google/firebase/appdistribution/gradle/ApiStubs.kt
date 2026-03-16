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

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.google.gson.JsonArray

class ApiStubs(testGradleProject: TestGradleProject) {
  private val projectName = "projects/${testGradleProject.projectNumber}"
  private val appName = "${projectName}/apps/${testGradleProject.appId}"
  private val releaseName = "$appName/releases/$RELEASE_ID"
  private val releaseTestName = "$releaseName/tests/$RELEASE_TEST_ID"

  fun stubGetAabInfoSuccess() =
    stubFor(
      get(urlEqualTo("/v1/$appName/aabInfo"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                        {
                          "integrationState": "INTEGRATED",
                          "testCertificate": {}
                        }
                        """
                .trimIndent()
            )
        )
    )

  fun stubUploadDistributionSuccess() {
    val operationName = "$appName/releases/-/operations/operation-id"
    stubFor(
      post(urlEqualTo("/upload/v1/$appName/releases:upload"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                      {
                        "name": "$operationName"
                      }
                      """
                .trimIndent()
            )
        )
    )
  }

  fun stubGetUploadStatusSuccess() {
    val path = "/v1/${appName}/releases/-/operations/operation-id"
    stubFor(
      get(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                      {
                        "done": "true",
                        "response": {
                          "result": "RELEASE_CREATED",
                          "release": {
                            "name": "$releaseName"
                          } 
                        }
                      }
                      """
                .trimIndent()
            )
        )
    )
  }

  fun stubDistributeReleaseSuccess(emails: List<String> = emptyList()) {
    val emailsJson = "[${emails.joinToString(",") { "\"$it\"" }}]"
    stubFor(
      post(urlEqualTo("/v1/${releaseName}:distribute"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                      {
                        "project": "$projectName",
                        "emails": $emailsJson
                      }
                      """
                .trimIndent()
            )
        )
    )
  }

  fun stubAddTestersSuccess(projectNumber: Long, stubbedEmailsResponse: Collection<String>) {
    val emailsJsonArray = JsonArray()
    for (email in stubbedEmailsResponse) {
      emailsJsonArray.add(email)
    }
    stubFor(
      post(urlEqualTo("/v1/projects/${projectNumber}/testers:batchAdd"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                      {
                        "status": "SUCCESS",
                        "message": "",
                        "errorCode": 0,
                        "emails": $emailsJsonArray
                      }
                      """
                .trimIndent()
            )
        )
    )
  }

  fun stubAddTestersFailure(projectNumber: Long, errorCode: Int) =
    stubFor(
      post(urlEqualTo("/v1/projects/$projectNumber/testers:batchAdd"))
        .willReturn(
          aResponse()
            .withStatus(errorCode)
            .withBody(
              """
                        {
                          "status": "ERROR",
                          "message": "",
                          "errorCode": $errorCode
                        }
                        """
                .trimIndent()
            )
        )
    )

  fun stubRemoveTestersSuccess(projectNumber: Long, stubbedEmailsResponse: Collection<String?>) {
    val emailsJsonArray = JsonArray()
    for (email in stubbedEmailsResponse) {
      emailsJsonArray.add(email)
    }
    stubFor(
      post(urlEqualTo("/v1/projects/$projectNumber/testers:batchRemove"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                        {
                          "status": "SUCCESS",
                          "message": "",
                          "errorCode": 0,
                          "emails": $emailsJsonArray
                        }
                        """
                .trimIndent()
            )
        )
    )
  }

  fun stubRemoveTestersSuccessNoEmailsRemoved(projectNumber: Long) =
    stubFor(
      post(urlEqualTo("/v1/projects/$projectNumber/testers:batchRemove"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                          {
                            "status": "SUCCESS",
                            "message": "",
                            "errorCode": 0
                          }
                          """
                .trimIndent()
            )
        )
    )

  fun stubRemoveTestersFailure(projectNumber: Long, errorCode: Int) =
    stubFor(
      post(urlEqualTo("/v1/projects/$projectNumber/testers:batchRemove"))
        .willReturn(
          aResponse()
            .withStatus(errorCode)
            .withBody(
              """
                          {
                            "status": "ERROR",
                            "message": "",
                            "errorCode": $errorCode
                          }
                          """
                .trimIndent()
            )
        )
    )

  fun stubTestReleaseSuccess() =
    stubFor(
      post(urlEqualTo("/v1alpha/$releaseName/tests"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                      {
                        "name": "$releaseTestName"
                      }
                      """
                .trimIndent()
            )
        )
    )

  fun stubReleaseTestLookupSuccess() =
    stubFor(
      get(urlEqualTo("/v1alpha/$releaseTestName"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
              {
                "name": "$releaseTestName",
                "deviceExecutions": [
                  {
                    "state": "PASSED"
                  }
                ]
              }
              """
                .trimIndent()
            )
        )
    )

  companion object {
    const val WIRE_MOCK_PORT = 8089
    private const val RELEASE_ID = "mock_release_id"
    private const val RELEASE_TEST_ID = "mock_release_test_id"
  }
}
