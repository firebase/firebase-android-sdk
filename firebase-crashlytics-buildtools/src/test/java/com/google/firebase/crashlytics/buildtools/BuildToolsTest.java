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

package com.google.firebase.crashlytics.buildtools;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertArrayEquals;

import com.google.firebase.crashlytics.buildtools.api.WebApi;
import com.google.firebase.crashlytics.buildtools.buildids.BuildIdInfo;
import com.google.firebase.crashlytics.buildtools.buildids.XmlResourceUtils;
import com.google.firebase.crashlytics.buildtools.ndk.internal.breakpad.BreakpadSymbolGenerator;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;

public class BuildToolsTest {
  private static final Comparator<BuildIdInfo> COMPARATOR =
      Comparator.comparing(BuildIdInfo::getBuildId)
          .thenComparing(BuildIdInfo::getLibraryName)
          .thenComparing(BuildIdInfo::getArch);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final File DUMP_SYMS_PREBUILDS_ROOT = new File("src/main/resources/dump_syms/");

  @Before
  public void setUp() {
    System.clearProperty(Buildtools.CODEMAPPING_API_URL_PROP);
  }

  @After
  public void tearDown() {
    System.clearProperty(Buildtools.CODEMAPPING_API_URL_PROP);
  }

  @Test
  public void testCreateWebApi_defaultUrls() {
    WebApi api = Buildtools.createWebApi();

    Assert.assertNotNull(api);

    // No overrides, should use default URLs.
    Assert.assertEquals(WebApi.DEFAULT_CODEMAPPING_API_URL, api.getCodeMappingApiUrl());
  }

  @Test
  public void testCreateWebApi_overriddenUrls() {
    String baseUrlOverride = "https://testApi.crashlytics.com";
    String cmUrlOverride = "https://testcm.crashlytics.com";

    // Set URL injection system properties.
    System.setProperty(Buildtools.BASE_API_URL_PROP, baseUrlOverride);
    System.setProperty(Buildtools.CODEMAPPING_API_URL_PROP, cmUrlOverride);

    WebApi api = Buildtools.createWebApi();

    Assert.assertNotNull(api);

    Assert.assertEquals(cmUrlOverride, api.getCodeMappingApiUrl());
  }

  @Test
  public void testFlutterSymbols_convert() throws IOException, URISyntaxException {
    File inputDir =
        new File(
            requireNonNull(getClass().getClassLoader().getResource("flutter_symbols")).toURI());

    File outputDir = temporaryFolder.newFolder("output");

    Buildtools.getInstance()
        .generateNativeSymbolFiles(
            inputDir,
            outputDir,
            new BreakpadSymbolGenerator(fetchDefaultDumpSymsBinaryForTesting()));

    assertArrayEquals(
        new String[] {"app.android-arm-arm-1dacae8bcdd2f988f0a10114976736ed.sym"},
        outputDir.list());
  }

  @Test
  public void testInjectBuildIds_writeFile() throws Exception {
    File inputDir =
        new File(requireNonNull(getClass().getClassLoader().getResource("testbuildids")).toURI());
    File expectedResourceFile =
        new File(
            requireNonNull(getClass().getClassLoader().getResource("resource_build_ids.xml"))
                .toURI());

    File outputDir = temporaryFolder.newFolder("resources");
    File resourceFile = new File(outputDir, "resource.xml");

    Buildtools.getInstance().injectBuildIdsIntoResource(inputDir, resourceFile);

    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document generatedDoc = docBuilder.parse(resourceFile);
    Set<BuildIdInfo> generatedSet = new TreeSet<>(COMPARATOR);
    generatedSet.addAll(XmlResourceUtils.getBuildIds(generatedDoc));

    Document expectedDoc = docBuilder.parse(expectedResourceFile);
    Set<BuildIdInfo> expectedSet = new TreeSet<>(COMPARATOR);
    expectedSet.addAll(XmlResourceUtils.getBuildIds(expectedDoc));

    Assert.assertEquals(generatedSet, expectedSet);
  }

  private static File fetchDefaultDumpSymsBinaryForTesting() {
    final String osProp = System.getProperty("os.name").toLowerCase();
    if (osProp.startsWith("mac")) {
      return new File(DUMP_SYMS_PREBUILDS_ROOT, "macos/dump_syms.bin");
    }
    if (osProp.startsWith("linux")) {
      return new File(DUMP_SYMS_PREBUILDS_ROOT, "linux/dump_syms.bin");
    }

    throw new RuntimeException("Test not supported on this os.");
  }
}
