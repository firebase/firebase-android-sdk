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

package com.google.firebase.appdistribution.gradle;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Production tests for for AddTestersTask and RemoveTestersTask. Uses BeePlus
 * to actually hit production. The goal of this test suite is to verify that
 * common successful use cases work end-to-end, as a replacement for manual
 * testing. For more nuanced testing, i.e. CLI parsing error cases, rely on the
 * integration tests.
 */
public final class TesterManagementTest {
  @Rule public final BeePlusGradleProject gradleProject = new BeePlusGradleProject();

  private static final Pattern PATTERN_TESTERS_REMOVED =
      Pattern.compile("Testers removed: \\[(.*)\\]");
  private List<String> emails;

  @Before
  public void setUp() throws IOException {
    gradleProject.writeBuildFile();

    emails = Lists.newArrayList();
    for (int i = 0; i < 20; i++) {
      emails.add("gradle-prod-test-" + i + "@e.mail");
    }
  }

  @Test
  public void testTesterManagement_addAndRemoveTesters() {
    // Remove the testers to make sure we start from a clean slate
    gradleProject.runRemoveTesters(emails);

    BuildResult addTestersResult = gradleProject.runAddTesters(emails);
    verifyAddTestersSuccessful(addTestersResult);

    BuildResult removeTestersResult = gradleProject.runRemoveTesters(emails);
    verifyRemoveTestersSuccessful(removeTestersResult, emails);
  }

  @Test
  public void testTesterManagement_addAndRemoveTestersFromFile() throws IOException {
    // Write the emails file
    File emailsFile = gradleProject.createFile("emails.txt");
    BeePlusGradleProject.writeFile(emailsFile, Joiner.on("\n").join(emails));

    // Remove the testers to make sure we start from a clean slate
    gradleProject.runRemoveTesters(emailsFile);

    BuildResult addTestersResult = gradleProject.runAddTesters(emailsFile);
    verifyAddTestersSuccessful(addTestersResult);

    BuildResult removeTestersResult = gradleProject.runRemoveTesters(emailsFile);
    verifyRemoveTestersSuccessful(removeTestersResult, emails);
  }

  @Test
  public void testTesterManagement_addAndRemoveTestersAreIdempotent() {
    verifyAddTestersSuccessful(gradleProject.runAddTesters(emails));
    verifyAddTestersSuccessful(gradleProject.runAddTesters(emails));

    BuildResult removeTestersResult1 = gradleProject.runRemoveTesters(emails);
    verifyRemoveTestersSuccessful(removeTestersResult1, emails);

    BuildResult removeTestersResult2 = gradleProject.runRemoveTesters(emails);
    verifyRemoveTestersSuccessful(removeTestersResult2, ImmutableList.of());
  }

  private static void verifyAddTestersSuccessful(BuildResult result) {
    assertThat(SUCCESS, equalTo(result.task(":appDistributionAddTesters").getOutcome()));
    assertThat(
        result.getOutput(),
        containsString("Adding 20 testers to project " + BeePlusGradleProject.PROJECT_NUMBER));
    assertThat(result.getOutput(), containsString("Testers added successfully [200]"));
  }

  private static void verifyRemoveTestersSuccessful(BuildResult result, List<String> emails) {
    assertThat(SUCCESS, equalTo(result.task(":appDistributionRemoveTesters").getOutcome()));
    assertThat(
        result.getOutput(),
        containsString("Removing 20 testers from project " + BeePlusGradleProject.PROJECT_NUMBER));
    assertThat(
        result.getOutput(),
        containsString(String.format("%s testers removed successfully [200]", emails.size())));

    if (emails.isEmpty()) {
      verifyNoEmailsRemoved(result);
    } else {
      verifyEmailsRemovedMatches(result, emails);
    }
  }

  private static void verifyEmailsRemovedMatches(
      BuildResult result, List<String> expectedEmailsRemoved) {
    Matcher m = PATTERN_TESTERS_REMOVED.matcher(result.getOutput());

    assertTrue(m.find());
    String emailsStr = m.group(1); // first matched expression
    List<String> emails = Arrays.asList(emailsStr.replaceAll("\"", "").split(","));

    assertThat(
        emails,
        containsInAnyOrder(
            expectedEmailsRemoved.stream().map(Matchers::equalTo).collect(Collectors.toList())));
  }

  private static void verifyNoEmailsRemoved(BuildResult result) {
    assertThat(result.getOutput(), containsString("Testers removed: []"));
  }
}
