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

package com.google.firebase.crashlytics.buildtools.ndk.internal.elf;

import com.google.firebase.crashlytics.buildtools.test.TestFileResource;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import org.junit.Assert;

public class ElfDataFileResource extends TestFileResource {
  private ClassLoader _classLoader;

  public ElfDataFileResource(ClassLoader classLoader) {
    _classLoader = classLoader;
  }

  public File getFile() throws URISyntaxException {
    URL resUrl = _classLoader.getResource(getFullpath());
    Assert.assertNotNull("Could not find resource file: " + getFullpath(), resUrl);
    return new File(resUrl.toURI());
  }
}
