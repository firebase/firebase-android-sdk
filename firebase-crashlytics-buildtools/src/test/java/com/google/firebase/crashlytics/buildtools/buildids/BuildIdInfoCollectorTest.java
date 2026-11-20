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

package com.google.firebase.crashlytics.buildtools.buildids;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

public class BuildIdInfoCollectorTest {
  private static final Comparator<BuildIdInfo> COMPARATOR =
      Comparator.comparing(BuildIdInfo::getBuildId)
          .thenComparing(BuildIdInfo::getLibraryName)
          .thenComparing(BuildIdInfo::getArch);

  @Test
  public void collect_producesBuildIdInfo() throws IOException, URISyntaxException {
    Set<BuildIdInfo> expectedSet = new TreeSet<>(COMPARATOR);
    expectedSet.add(
        new BuildIdInfo("libbuildids.so", "aarch64", "3f19422da263c0ca00b80c39e802ebf2e4498757"));
    expectedSet.add(new BuildIdInfo("libbuildids.so", "arm", "1fbfe18631c6881816e3f32bacd268e3"));
    expectedSet.add(new BuildIdInfo("libbuildids.so", "x86", "9c8a80ffe24989442408f6a8bb3ce387"));
    expectedSet.add(
        new BuildIdInfo("libbuildids.so", "x86_64", "b93becbacc657d035159d231f8870fe6ceae4825"));

    BuildIdInfoCollector buildIdInfoCollector = new BuildIdInfoCollector();
    File inputDir =
        new File(requireNonNull(getClass().getClassLoader().getResource("testbuildids")).toURI());

    List<BuildIdInfo> buildIdInfoList = buildIdInfoCollector.collect(inputDir);
    Set<BuildIdInfo> collectedSet = new TreeSet<>(COMPARATOR);
    collectedSet.addAll(buildIdInfoList);

    assertEquals(buildIdInfoList.size(), 4);
    assertEquals(collectedSet, expectedSet);
  }

  @Test
  public void collect_returnsBuildIdsInDeterministicOrder() throws URISyntaxException {
    Path fixtureRoot =
        new File(requireNonNull(getClass().getClassLoader().getResource("testbuildids")).toURI())
            .toPath();
    List<File> inputFiles =
        Arrays.asList(
            fixtureRoot.resolve("x86_64/libbuildids.so").toFile(),
            fixtureRoot.resolve("x86/libbuildids.so").toFile(),
            fixtureRoot.resolve("armeabi-v7a/libbuildids.so").toFile(),
            fixtureRoot.resolve("arm64-v8a/libbuildids.so").toFile());

    List<BuildIdInfo> buildIdInfoList = new BuildIdInfoCollector().collectFiles(inputFiles);

    assertEquals(4, buildIdInfoList.size());
    assertBuildIdInfo(
        buildIdInfoList.get(0), "aarch64", "3f19422da263c0ca00b80c39e802ebf2e4498757");
    assertBuildIdInfo(buildIdInfoList.get(1), "arm", "1fbfe18631c6881816e3f32bacd268e3");
    assertBuildIdInfo(buildIdInfoList.get(2), "x86", "9c8a80ffe24989442408f6a8bb3ce387");
    assertBuildIdInfo(buildIdInfoList.get(3), "x86_64", "b93becbacc657d035159d231f8870fe6ceae4825");
  }

  @Test
  public void buildIdOrdering_sortsAllFieldsAndAcceptsNulls() {
    List<BuildIdInfo> expected =
        Arrays.asList(
            new BuildIdInfo(null, null, null),
            new BuildIdInfo(null, null, "a"),
            new BuildIdInfo(null, "a", null),
            new BuildIdInfo("a", null, null),
            new BuildIdInfo("a", "a", null),
            new BuildIdInfo("a", "a", "a"),
            new BuildIdInfo("a", "a", "b"),
            new BuildIdInfo("a", "b", "a"),
            new BuildIdInfo("b", "a", "a"));
    List<BuildIdInfo> actual = new ArrayList<>(expected);
    Collections.reverse(actual);

    actual.sort(BuildIdInfoCollector.BUILD_ID_INFO_ORDERING);

    assertEquals(expected, actual);
  }

  @Test
  public void collect_noBuildId_producesTextHash() throws IOException, URISyntaxException {
    BuildIdInfoCollector buildIdInfoCollector = new BuildIdInfoCollector();
    File inputDir =
        new File(requireNonNull(getClass().getClassLoader().getResource("testnobuildids")).toURI());

    List<BuildIdInfo> buildIdInfoList = buildIdInfoCollector.collect(inputDir);

    assertEquals(buildIdInfoList.size(), 1);
    assertEquals(buildIdInfoList.get(0).getLibraryName(), "libbuildids.so");
    assertEquals(buildIdInfoList.get(0).getArch(), "x86");
    assertEquals(buildIdInfoList.get(0).getBuildId(), "b37459cf1bef5f5881b4f7519917583c");
  }

  private static void assertBuildIdInfo(BuildIdInfo buildIdInfo, String arch, String buildId) {
    assertEquals("libbuildids.so", buildIdInfo.getLibraryName());
    assertEquals(arch, buildIdInfo.getArch());
    assertEquals(buildId, buildIdInfo.getBuildId());
  }
}
