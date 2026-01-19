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

package com.google.firebase.crashlytics.buildtools.mappingfiles;

import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MappingFileIdReaderTest {

  private File _testResourceDirectory;

  private static final String ID = "f204c6a6-52b3-4324-a05d-a8ac7041e688";
  private static final String MERGED_RES_VALUES = "test_mapping_file_id_manager/merged_values.xml";
  private static final String MERGED_RES_VALUES_WITH_ID =
      "test_mapping_file_id_manager/merged_values_with_existing_mapping_file_id.xml";

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    _testResourceDirectory = testFolder.newFolder("res");
  }

  /**
   * Test the creation of a new resource file. Also tests how the ResourceFileManager constructor deals with
   * missing project and resource directories.
   */
  @Test
  public void testGetMappingFileIdWhenNotPresent() throws Exception {
    File resFile = setupTestResFile(MERGED_RES_VALUES);
    MappingFileIdReader idReader = MappingFileIdReader.create(resFile);
    Assert.assertNull(idReader.getMappingFileId());
  }

  @Test
  public void testGetMappingFileIdWhenPresent() throws Exception {
    File resFile = setupTestResFile(MERGED_RES_VALUES_WITH_ID);
    MappingFileIdReader idReader = MappingFileIdReader.create(resFile);
    String buildID = idReader.getMappingFileId();
    Assert.assertEquals(ID, buildID);
  }

  private File setupTestResFile(String testResFile) throws IOException {
    File resFile = new File(new File(_testResourceDirectory, "values"), "test_strings.xml");
    resFile.getParentFile().mkdirs();
    resFile.createNewFile();

    BufferedOutputStream temporaryRes = null;
    InputStream inputTestRes = null;
    try {
      inputTestRes = getClass().getClassLoader().getResourceAsStream(testResFile);
      temporaryRes = new BufferedOutputStream(new FileOutputStream(resFile));
      FileUtils.redirect(inputTestRes, temporaryRes);
      return resFile;
    } finally {
      IOUtils.closeQuietly(temporaryRes);
      IOUtils.closeQuietly(inputTestRes);
    }
  }
}
