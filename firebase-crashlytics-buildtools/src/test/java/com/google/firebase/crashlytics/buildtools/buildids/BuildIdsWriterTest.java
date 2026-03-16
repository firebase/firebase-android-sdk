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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class BuildIdsWriterTest {

  private File testResourceDirectory;

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    testResourceDirectory = testFolder.newFolder("res");
  }

  @Test
  public void testWithoutResourceFolder() throws Exception {
    File resFile = new File(new File(testResourceDirectory, "values"), "test_strings.xml");
    BuildIdsWriter idWriter = new BuildIdsWriter(resFile);

    // The parent directory should not exist yet.
    Assert.assertFalse(resFile.getParentFile().exists());

    // verify that the file is a valid XML file with a resources node.
    List<BuildIdInfo> buildIdInfoList = new ArrayList();
    buildIdInfoList.add(new BuildIdInfo("lib.so", "x86", "aaabbb"));
    idWriter.writeBuildIds(buildIdInfoList);
    Assert.assertTrue(resFile.exists());
    Assert.assertTrue(resFile.isFile());

    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = docBuilder.parse(resFile);
    Node rootNode = doc.getFirstChild();
    Assert.assertEquals("resources", rootNode.getNodeName());
  }

  @Test
  public void testWithResourceFolder() throws Exception {
    File valuesDir = new File(testResourceDirectory, "values");
    File resFile = new File(valuesDir, "test_strings.xml");
    BuildIdsWriter idWriter = new BuildIdsWriter(resFile);

    // Test creation when the values directory already exists and there is a 2nd file in the
    // directory.
    // This is the most common use-case, as usually there will be a res/values/strings.xml file.
    File otherFile = new File(valuesDir, "otherFile.xml");
    Assert.assertFalse(valuesDir.exists());
    valuesDir.mkdirs();
    otherFile.createNewFile();

    Assert.assertTrue(otherFile.exists());
    Assert.assertFalse(resFile.exists());

    List<BuildIdInfo> buildIdInfoList = new ArrayList();
    buildIdInfoList.add(new BuildIdInfo("lib.so", "x86", "aaabbb"));
    idWriter.writeBuildIds(buildIdInfoList);

    Assert.assertTrue(resFile.exists());
    Assert.assertTrue(otherFile.exists());
  }

  @Test
  public void testIdCreate() throws Exception {
    File resFile = new File(new File(testResourceDirectory, "values"), "test_strings.xml");
    BuildIdsWriter idWriter = new BuildIdsWriter(resFile);
    List<BuildIdInfo> buildIdInfoList = new ArrayList();
    buildIdInfoList.add(new BuildIdInfo("lib.so", "x86", "aaabbb"));
    idWriter.writeBuildIds(buildIdInfoList);

    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = docBuilder.parse(resFile);
    List<BuildIdInfo> extractedList = XmlResourceUtils.getBuildIds(doc);
    Assert.assertTrue(buildIdInfoList.size() == extractedList.size());
    Assert.assertTrue(
        buildIdInfoList.get(0).getLibraryName().equals(extractedList.get(0).getLibraryName()));
    Assert.assertTrue(buildIdInfoList.get(0).getArch().equals(extractedList.get(0).getArch()));
    Assert.assertTrue(
        buildIdInfoList.get(0).getBuildId().equals(extractedList.get(0).getBuildId()));
  }
}
