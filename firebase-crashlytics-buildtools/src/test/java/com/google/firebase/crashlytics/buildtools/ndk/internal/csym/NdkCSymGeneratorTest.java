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

import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.TestUtils;
import com.google.firebase.crashlytics.buildtools.log.ConsoleLogger;
import com.google.firebase.crashlytics.buildtools.log.CrashlyticsLogger;
import java.io.File;
import java.io.FileNotFoundException;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(EasyMockRunner.class)
public class NdkCSymGeneratorTest {

  private NdkCSymGenerator _generatorUnderTest;

  private File _ndkOutDir;

  @Mock(type = MockType.NICE)
  private CrashlyticsLogger _mockLogger;

  @Mock(type = MockType.NICE)
  private CSymFactory _mockCSymFactory;

  @Mock(type = MockType.STRICT)
  private CSymFileWriter _mockWriter;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    _ndkOutDir = tempFolder.newFolder();
    Buildtools.setLogger(_mockLogger);
    _generatorUnderTest = new NdkCSymGenerator();
  }

  @After
  public void tearDown() throws Exception {
    Buildtools.setLogger(new ConsoleLogger());
  }

  @Test
  public void testGenerateCodeMappings_logsWhenLibFileNotFound() throws Exception {
    File libTestElf = setupNdkOutDirAndLibFile();

    FileNotFoundException expectedException = new FileNotFoundException();

    EasyMock.expect(_mockCSymFactory.createCSymFromFile(libTestElf)).andThrow(expectedException);
    EasyMock.replay(_mockCSymFactory);

    _mockLogger.logD(EasyMock.anyString());
    EasyMock.expectLastCall().once();
    EasyMock.replay(_mockLogger);

    EasyMock.replay(_mockWriter); // mockWriter should never be called.
  }

  private File setupNdkOutDirAndLibFile() throws Exception {
    File libTestElf = new File(_ndkOutDir, "libtestelf.so");
    TestUtils.createFileFromResource("testelf/armeabi/libtestelf.so", libTestElf);
    return libTestElf;
  }
}
