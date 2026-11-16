/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.gradle.plugins.report

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class TestReportGeneratorTests {

  @Test
  fun `parseTestReports aggregates unit and integration test workflows in parallel`() {
    val generator = spyk(TestReportGenerator("test-token"))

    every { generator.request("actions/runs?head_sha=abc123") } returns
      json(
        """
        {
          "workflow_runs": [
            { "id": "101", "name": "CI Unit Tests" },
            { "id": "102", "name": "CI Integration Tests" },
            { "id": "103", "name": "Check Format" },
            { "id": "99", "name": "CI Unit Tests" }
          ]
        }
        """
      )

    every { generator.request("actions/runs/101/jobs") } returns
      json(
        """
        {
          "jobs": [
            {
              "name": "Determine changed modules",
              "status": "completed",
              "conclusion": "success",
              "html_url": "https://example.com/jobs/1"
            },
            {
              "name": "Unit Tests (:firebase-common)",
              "status": "completed",
              "conclusion": "success",
              "html_url": "https://example.com/jobs/2"
            }
          ]
        }
        """
      )

    every { generator.request("actions/runs/102/jobs") } returns
      json(
        """
        {
          "jobs": [
            {
              "name": "Instrumentation Tests (:firebase-common)",
              "status": "completed",
              "conclusion": "failure",
              "html_url": "https://example.com/jobs/3"
            }
          ]
        }
        """
      )

    val reports = generator.parseTestReports("abc123")

    reports.shouldContainExactlyInAnyOrder(
      TestReport(
        name = "firebase-common",
        type = TestReport.Type.UNIT_TEST,
        status = TestReport.Status.SUCCESS,
        commit = "abc123",
        url = "https://example.com/jobs/2",
      ),
      TestReport(
        name = "firebase-common",
        type = TestReport.Type.INSTRUMENTATION_TEST,
        status = TestReport.Status.FAILURE,
        commit = "abc123",
        url = "https://example.com/jobs/3",
      ),
    )
    verify(exactly = 0) { generator.request("actions/runs/99/jobs") }
  }

  @Test
  fun `parseTestReports supports legacy combined CI Tests workflow`() {
    val generator = spyk(TestReportGenerator("test-token"))

    every { generator.request("actions/runs?head_sha=old456") } returns
      json(
        """
        {
          "workflow_runs": [
            { "id": "201", "name": "CI Tests" }
          ]
        }
        """
      )

    every { generator.request("actions/runs/201/jobs") } returns
      json(
        """
        {
          "jobs": [
            {
              "name": "Unit Tests (:firebase-storage)",
              "status": "completed",
              "conclusion": "success",
              "html_url": "https://example.com/jobs/10"
            },
            {
              "name": "Instrumentation Tests (:firebase-storage)",
              "status": "completed",
              "conclusion": "success",
              "html_url": "https://example.com/jobs/11"
            }
          ]
        }
        """
      )

    val reports = generator.parseTestReports("old456")

    reports.shouldContainExactlyInAnyOrder(
      TestReport(
        name = "firebase-storage",
        type = TestReport.Type.UNIT_TEST,
        status = TestReport.Status.SUCCESS,
        commit = "old456",
        url = "https://example.com/jobs/10",
      ),
      TestReport(
        name = "firebase-storage",
        type = TestReport.Type.INSTRUMENTATION_TEST,
        status = TestReport.Status.SUCCESS,
        commit = "old456",
        url = "https://example.com/jobs/11",
      ),
    )
  }

  private fun json(raw: String): JsonObject = Json.decodeFromString(raw)
}
