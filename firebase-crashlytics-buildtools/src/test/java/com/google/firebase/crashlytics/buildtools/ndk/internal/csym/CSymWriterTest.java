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

package com.google.firebase.crashlytics.buildtools.ndk.internal.csym;

import com.google.firebase.crashlytics.buildtools.TestUtils;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CSymWriterTest {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final File WORKING_DIR = new File(TestUtils.TEST_OUTPUT_DIR, "cSymTests");

  private static final String UUID = "UUID-1234";
  private static final String TYPE = "testCsym";
  private static final String ARCH = "testArch";

  private static final String SYMBOL1 = "symbol1";
  private static final String SYMBOL2 = "symbol2";
  private static final String SYMBOL3 = "symbol3";

  private static final String FILE = "file.c";

  private CSym.Builder _builder;

  @Before
  public void setUp() throws Exception {
    TestUtils.prepareTestDirectory();
    FileUtils.verifyDirectory(WORKING_DIR);

    _builder = new CSym.Builder(UUID, TYPE, ARCH);
    _builder.addRange(0, 1024, SYMBOL1);
    _builder.addRange(1024, 512, SYMBOL2, FILE);
    _builder.addRange(1536, 2048, SYMBOL3, FILE, 398);
  }

  @Test
  public void testCSymWriteToOutputStream() throws IOException {
    CSym csym1 = _builder.build();
    CSym csym2 = _builder.build();

    Assert.assertTrue(csym1 != csym2);

    ByteArrayOutputStream outputStream1 = new ByteArrayOutputStream(1024);
    ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream(1024);
    CSymWriter.writeToOutputStream(csym1, outputStream1);
    CSymWriter.writeToOutputStream(csym2, outputStream2);
    String output1 = new String(outputStream1.toByteArray(), UTF8);
    String output2 = new String(outputStream2.toByteArray(), UTF8);

    Assert.assertEquals(output1, output2);

    Assert.assertTrue(output1.contains(UUID));
    Assert.assertTrue(output1.contains(TYPE));
    Assert.assertTrue(output1.contains(ARCH));
  }

  @Test
  public void testCSymWriteToTextFile() throws IOException {
    File dataFile = new File(WORKING_DIR, "testFile");
    if (dataFile.exists()) {
      dataFile.delete();
    }

    CSym cSym = _builder.build();

    try {
      Assert.assertFalse(dataFile.exists());
      CSymWriter.writeToTextFile(cSym, dataFile);
      Assert.assertTrue(dataFile.exists());

      BufferedReader reader = new BufferedReader(new FileReader(dataFile));
      String header = reader.readLine();
      String[] fields = header.split("\\s");

      Assert.assertEquals(8, fields.length);
      Assert.assertEquals(TYPE, fields[2]);
      Assert.assertEquals(UUID, fields[3]);
      Assert.assertEquals(ARCH, fields[4]);
      Assert.assertEquals(cSym.getFiles().size(), Integer.parseInt(fields[5]));
      Assert.assertEquals(cSym.getSymbols().size(), Integer.parseInt(fields[6]));

      reader.close();

    } finally {
      dataFile.delete();
    }
  }
}
