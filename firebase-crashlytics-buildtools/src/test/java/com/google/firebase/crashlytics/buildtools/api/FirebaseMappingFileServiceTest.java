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

package com.google.firebase.crashlytics.buildtools.api;

import static org.easymock.EasyMock.anyObject;
import static org.junit.Assert.assertThrows;

import com.google.firebase.crashlytics.buildtools.AppBuildInfo;
import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.Obfuscator;
import com.google.firebase.crashlytics.buildtools.exception.ZeroByteFileException;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(EasyMockRunner.class)
public class FirebaseMappingFileServiceTest {
  private static final String mappingFileId = Buildtools.DUMMY_MAPPING_ID;
  private static final String googleAppId = "1:1:ios:1c";
  private static final AppBuildInfo appBuildInfo = new AppBuildInfo("", googleAppId, null);
  private static final Obfuscator obfuscator = new Obfuscator(Obfuscator.Vendor.R8, "12");
  private static final String BASE_PATH = "com/google/firebase/crashlytics/buildtools/api/";

  private FirebaseMappingFileService mappingFileService;

  @Mock(type = MockType.DEFAULT)
  private WebApi webApi;

  @Before
  public void setup() {
    mappingFileService = new FirebaseMappingFileService(webApi);
  }

  @Test
  public void uploadObfuscatorMappingFile_callsWebApi() throws Exception {
    EasyMock.expect(webApi.getCodeMappingApiUrl()).andReturn("http://fakeurl.mcfakey");

    webApi.uploadFile(anyObject(URL.class), anyObject(File.class));
    EasyMock.expectLastCall();

    EasyMock.replay(webApi);

    mappingFileService.uploadMappingFile(
        testFile("nonemptyfile.txt"), mappingFileId, appBuildInfo, obfuscator);
  }

  @Test
  public void uploadObfuscatorMappingFile_zeroByteFile_throwsException() {
    assertThrows(
        ZeroByteFileException.class,
        () ->
            mappingFileService.uploadMappingFile(
                new File("empty"), mappingFileId, appBuildInfo, obfuscator));
  }

  private File testFile(String filename) throws URISyntaxException {
    return new File(getClass().getClassLoader().getResource(BASE_PATH + filename).toURI());
  }
}
