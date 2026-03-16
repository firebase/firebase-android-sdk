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

package com.google.firebase.crashlytics.buildtools.ndk.internal.breakpad;

import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.ndk.NativeSymbolGenerator;
import com.google.firebase.crashlytics.buildtools.ndk.internal.CodeMappingException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.IOUtils;

public class BreakpadSymbolGenerator implements NativeSymbolGenerator {

  private static final String OS_WINDOWS = "windows";
  private static final String OS_MAC = "macos";
  private static final String OS_LINUX = "linux";

  private static final String OBJ_FILE = "dump_syms.obj";

  private static final Set<PosixFilePermission> DUMP_SYMS_PERMISSIONS =
      Collections.unmodifiableSet(
          new HashSet<PosixFilePermission>(
              Arrays.asList(
                  PosixFilePermission.OWNER_READ,
                  PosixFilePermission.GROUP_READ,
                  PosixFilePermission.OTHERS_READ,
                  PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.GROUP_WRITE,
                  PosixFilePermission.OTHERS_WRITE,
                  PosixFilePermission.OWNER_EXECUTE,
                  PosixFilePermission.GROUP_EXECUTE,
                  PosixFilePermission.OTHERS_EXECUTE)));

  private final File dumpSymsBin;

  /**
   * Extract the platform-specific dump_syms binary executable and any platform-specific
   * dependencies to destPath.
   *
   * @return File representing the dump_syms binary itself
   */
  public static File extractDefaultDumpSymsBinary(File destPath) throws IOException {
    final String osString = getOsForDumpSyms();

    final File outputFile =
        new File(destPath, OS_WINDOWS.equals(osString) ? "dump_syms.exe" : "dump_syms.bin");
    if (outputFile.exists()) {
      Buildtools.logD("Skipping dumpsyms extraction, file exists: " + outputFile.getAbsolutePath());
      return outputFile;
    }

    final String resource = "dump_syms/" + osString + "/dump_syms.bin";
    Buildtools.logD(
        "Extracting dump_syms from " + resource + " to " + outputFile.getAbsolutePath());
    extractResource(resource, outputFile);

    if (!OS_WINDOWS.equals(osString)) {
      Files.setPosixFilePermissions(outputFile.toPath(), DUMP_SYMS_PERMISSIONS);
    } else {
      // Can't set POSIX permissions on Windows, fall back to setExecutable(true)
      boolean result = outputFile.setExecutable(true);
      if (!result) {
        // Log a warning and attempt to continue. Probably going to fail.
        Buildtools.logW(
            "File#setExecutable() failed for "
                + outputFile.getAbsolutePath()
                + "; library extracted without setting permissions.");
      }
      // Windows also needs the object file due to statically linking LGPL libstdc++
      Buildtools.logD("Extracting object file to " + destPath);
      final String dllResource = "dump_syms/" + OS_WINDOWS + "/" + OBJ_FILE;
      extractResource(dllResource, new File(destPath, OBJ_FILE));
    }
    return outputFile;
  }

  private static void extractResource(String pathToResource, File outputFile) throws IOException {
    final InputStream binStream =
        BreakpadSymbolGenerator.class.getClassLoader().getResourceAsStream(pathToResource);
    final OutputStream outStream = new FileOutputStream(outputFile);
    IOUtils.copy(binStream, outStream);
    binStream.close();
    outStream.close();
  }

  private static String getOsForDumpSyms() throws IOException {
    final String osProp = System.getProperty("os.name").toLowerCase();
    if (osProp.startsWith("windows")) {
      return OS_WINDOWS;
    }
    if (osProp.startsWith("mac")) {
      return OS_MAC;
    }
    if (osProp.startsWith("linux")) {
      return OS_LINUX;
    }
    throw new IOException("Cannot extract dump_syms, unexpected os.name: " + osProp);
  }

  public BreakpadSymbolGenerator(File dumpSymsBin) {
    this.dumpSymsBin = dumpSymsBin;
    Buildtools.logD("Breakpad symbol generator initialized: " + dumpSymsBin.getAbsolutePath());
  }

  @Override
  public File generateSymbols(File nativeLib, File symbolFileOutputDir)
      throws IOException, CodeMappingException {

    Buildtools.logD(
        "Crashlytics generating Breakpad Symbol file for: " + nativeLib.getAbsolutePath());

    // The final name of the file must include the UUID & arch, which we don't know yet,
    // so use a temp file for now.
    final File tempOutputFile =
        File.createTempFile(nativeLib.getName(), ".tmp", symbolFileOutputDir);

    Buildtools.logD(
        "Extracting Breakpad symbols to temp file: " + tempOutputFile.getAbsolutePath());

    final Process proc =
        new ProcessBuilder(dumpSymsBin.getAbsolutePath(), nativeLib.getAbsolutePath())
            .redirectOutput(tempOutputFile)
            .start();

    try {
      proc.waitFor();
    } catch (InterruptedException e) {
      // Shouldn't happen
      throw new IOException("Dump symbols was unexpectedly interrupted.", e);
    }

    if (proc.exitValue() != 0) {
      throw new IOException(
          "Breakpad symbol generation failed (exit=" + proc.exitValue() + "), see STDERR");
    }
    final BreakpadRecords breakpadRecords = BreakpadRecords.createFromBreakpadFile(tempOutputFile);
    final String codeId =
        (breakpadRecords.getCodeId() != null)
            ? breakpadRecords.getCodeId()
            : breakpadRecords.getModuleId();

    Buildtools.logD("GNU Build Id for " + nativeLib.getAbsolutePath() + ": " + codeId);

    final String symbolFileBasename =
        NativeSymbolGenerator.createSymbolFileBasename(
            nativeLib.getName(), breakpadRecords.getArchitecture(), codeId);
    final File breakpadOutputFile = new File(symbolFileOutputDir, symbolFileBasename + ".sym");
    Buildtools.logD("Renaming Breakpad symbol file to: " + breakpadOutputFile.getAbsolutePath());
    Files.move(
        tempOutputFile.toPath(), breakpadOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return breakpadOutputFile;
  }
}
