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

import com.google.firebase.crashlytics.buildtools.api.FirebaseMappingFileService;
import com.google.firebase.crashlytics.buildtools.api.MappingFileService;
import com.google.firebase.crashlytics.buildtools.api.RestfulWebApi;
import com.google.firebase.crashlytics.buildtools.api.SymbolFileService;
import com.google.firebase.crashlytics.buildtools.api.WebApi;
import com.google.firebase.crashlytics.buildtools.api.net.proxy.DefaultProxyFactory;
import com.google.firebase.crashlytics.buildtools.buildids.BuildIdInfo;
import com.google.firebase.crashlytics.buildtools.buildids.BuildIdInfoCollector;
import com.google.firebase.crashlytics.buildtools.buildids.BuildIdsWriter;
import com.google.firebase.crashlytics.buildtools.log.ConsoleLogger;
import com.google.firebase.crashlytics.buildtools.log.CrashlyticsLogger;
import com.google.firebase.crashlytics.buildtools.mappingfiles.MappingFileIdReader;
import com.google.firebase.crashlytics.buildtools.mappingfiles.MappingFileIdWriter;
import com.google.firebase.crashlytics.buildtools.ndk.NativeSymbolGenerator;
import com.google.firebase.crashlytics.buildtools.ndk.internal.CodeMappingException;
import com.google.firebase.crashlytics.buildtools.ndk.internal.csym.CsymSymbolFileService;
import com.google.firebase.crashlytics.buildtools.ndk.internal.csym.NdkCSymGenerator;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.filefilter.TrueFileFilter;

public class Buildtools {

  protected static final String BASE_API_URL_PROP = "crashlytics.webApiUrl";
  protected static final String CODEMAPPING_API_URL_PROP = "crashlytics.cmApiUrl";

  private static final String UNSTRIPPED_NATIVE_LIBS_DIR_ERR_MSG =
      "Either specify the correct unstrippedNativeLibsDir or disable Crashlytics symbol uploading.";

  private static Buildtools instance = null;

  // Default logger: INFO to STDOUT; warnings / errors to STDERR.
  private static CrashlyticsLogger logger = new ConsoleLogger(CrashlyticsLogger.Level.INFO);

  private static WebApi sharedWebApi;

  static WebApi getWebApi() {
    return sharedWebApi;
  }

  static void setWebApi(WebApi api) {
    if (!WebApi.DEFAULT_CODEMAPPING_API_URL.equals(api.getCodeMappingApiUrl())) {
      logW("Using overridden Crashlytics host: " + api.getCodeMappingApiUrl(), null);
    }
    sharedWebApi = api;
  }

  /** Creates the connection to the Crashlytics APIs. */
  public static WebApi createWebApi() {

    String codeMappingApiUrl =
        System.getProperty(CODEMAPPING_API_URL_PROP, WebApi.DEFAULT_CODEMAPPING_API_URL);

    return new RestfulWebApi(codeMappingApiUrl, new DefaultProxyFactory());
  }

  public static void setLogger(CrashlyticsLogger logger) {
    Buildtools.logger = logger;
  }

  public static CrashlyticsLogger getLogger() {
    return logger;
  }

  // region: Convenience methods that wrap calls to the logger.

  public static void logV(String msg) {
    logger.logV(msg);
  }

  public static void logD(String msg) {
    logger.logD(msg);
  }

  public static void logI(String msg) {
    logger.logI(msg);
  }

  public static void logW(String msg) {
    logger.logW(msg, null);
  }

  public static void logW(String msg, Throwable t) {
    logger.logW(msg, t);
  }

  public static void logE(String msg, Throwable t) {
    logger.logE(msg, t);
  }

  // endregion

  public static synchronized Buildtools getInstance() {
    if (instance == null) {
      WebApi api = createWebApi();
      instance = new Buildtools(api);
    }
    return instance;
  }

  public static void main(String[] args) {
    CommandLineHelper.main(args);
  }

  /**
   * Package-private constructor, used for testing. Outside of testing, Buildtools is a singleton
   * that should be retrieved via the getInstance() method.
   */
  Buildtools(WebApi api) {
    logD("Crashlytics Buildtools initialized.");

    setWebApi(api);

    // set default values based on the values in the manifest; these can be overridden by the client
    // using setBuildtoolsClientInfo().
    Package buildtoolsPkg = getClass().getPackage();
    setBuildtoolsClientInfo(
        buildtoolsPkg.getImplementationTitle(), buildtoolsPkg.getImplementationVersion());
  }

  public static final String DUMMY_MAPPING_ID = "00000000000000000000000000000000";

  /**
   * @return valid Mapping File ID.
   */
  public static String generateMappingFileId() {
    return UUID.randomUUID().toString().replace("-", "").toLowerCase();
  }

  /**
   * Adds the mappingFileId as a String resource to the given Android resource (XML) file. If an
   * existing value for the mapping file id is in the resourceFile and it matches mappingFileId, the
   * file is not modified. resourceFile will be created if it does not exist.
   *
   * @param resourceFile The Android XML resource file, which will be created if it does not exist.
   * @param mappingFileId The mappingFileId to be injected into resourceFile.
   * @return true if the mapping file was updated, false if it was not necessary to update.
   * @throws IOException
   */
  public boolean injectMappingFileIdIntoResource(File resourceFile, String mappingFileId)
      throws IOException {
    logD(
        String.format(
            "Injecting mappingFileId into file [mappingFileId: %1$s; file:  %2$s]",
            mappingFileId, resourceFile));

    final MappingFileIdReader reader = MappingFileIdReader.create(resourceFile);
    if (mappingFileId.equals(reader.getMappingFileId())) {
      Buildtools.logD("mappingFileId was NOT updated; correct value already present.");
      return false;
    }

    final MappingFileIdWriter writer = new MappingFileIdWriter(resourceFile);
    writer.writeMappingFileId(mappingFileId);
    return true;
  }

  /**
   * Generates a new mappingFileId and adds it to the given Android resource (XML) file.
   * resourceFile will be created if needed, and any previous value for the mapping file will be
   * overwritten.
   *
   * @param resourceFile The Android XML resource file, which will be created if it does not exist.
   * @throws IOException
   */
  public void injectMappingFileIdIntoResource(File resourceFile) throws IOException {
    injectMappingFileIdIntoResource(resourceFile, generateMappingFileId());
  }

  /**
   * Adds the mappingFileId as a String resource to the given Android resource (XML) file. If an
   * existing value for the mapping file id is in the resourceFile and it matches mappingFileId, the
   * file is not modified. resourceFile will be created if it does not exist.
   *
   * @param resourceFile The Android XML resource file, which will be created if it does not exist.
   * @param mergedNativeLibsPath The set of native libs to be processed.
   * @return true if the mapping file was updated, false if it was not necessary to update.
   * @throws IOException
   */
  public boolean injectBuildIdsIntoResource(File mergedNativeLibsPath, File resourceFile)
      throws IOException {
    BuildIdInfoCollector collector = new BuildIdInfoCollector();

    if (!mergedNativeLibsPath.exists()) {
      throw new IOException(
          String.format(
              "Unstripped native library path does not exist: %s. %s",
              mergedNativeLibsPath.getAbsolutePath(), UNSTRIPPED_NATIVE_LIBS_DIR_ERR_MSG));
    }

    if (mergedNativeLibsPath.isFile() && FileUtils.isZipFile(mergedNativeLibsPath)) {
      logD("Skipping zip file: " + mergedNativeLibsPath.getAbsolutePath());
      return true;
    }

    List<BuildIdInfo> buildIdInfoList = collector.collect(mergedNativeLibsPath);

    final BuildIdsWriter writer = new BuildIdsWriter(resourceFile);
    writer.writeBuildIds(buildIdInfoList);
    return true;
  }

  /**
   * Uploads the given mappingFile, and associates it with the mappingFileId present in the given
   * resourceFile.
   */
  public void uploadMappingFile(
      File mappingFile, File resourceFile, AppBuildInfo appBuildInfo, Obfuscator obfuscator)
      throws IOException {

    logD("Extracting mappingFileId from resource file: " + resourceFile.getAbsolutePath());

    if (!resourceFile.isFile()) {
      throw new IllegalArgumentException(
          "Resource file is not valid: " + resourceFile.getAbsolutePath());
    }

    MappingFileIdReader idReader = MappingFileIdReader.create(resourceFile);
    String mappingFileId = idReader.getMappingFileId();

    if (mappingFileId == null || mappingFileId.isEmpty()) {
      throw new IllegalArgumentException(
          "Resource file does not contain a valid mapping file id: "
              + resourceFile.getAbsolutePath());
    }

    uploadMappingFile(mappingFile, mappingFileId, appBuildInfo, obfuscator);
  }

  public void uploadMappingFile(
      File mappingFile, String mappingFileId, AppBuildInfo appBuildInfo, Obfuscator obfuscator)
      throws IOException {
    logD(
        String.format(
            "Uploading Mapping File [mappingFile: %1$s; mappingFileId: %2$s;"
                + "packageName: %3$s; googleAppId: %4$s]",
            mappingFile.getAbsolutePath(),
            mappingFileId,
            appBuildInfo.getPackageName(),
            appBuildInfo.getGoogleAppId()));

    final MappingFileService mappingService = new FirebaseMappingFileService(getWebApi());
    mappingService.uploadMappingFile(mappingFile, mappingFileId, appBuildInfo, obfuscator);

    // The upload method throws an exception if it failed, so we know it was successful.
    logI(String.format("Mapping file uploaded: %1$s", mappingFile.toString()));
  }

  /** Generates native symbol files for the given path, using the default symbol generator. */
  public void generateNativeSymbolFiles(File path, File symbolFileOutputDir) throws IOException {
    generateNativeSymbolFiles(path, symbolFileOutputDir, new NdkCSymGenerator());
  }

  /**
   * Generates native symbol files for the given path, using the given symbol generator.
   *
   * @param path The path to 1) a single native lib; 2) a directory to be recursively scanned for
   *     .so files; or 3) a zip file whose contents will be recursively scanned for .so files.
   * @param symbolFileOutputDir Path to directory where all generated symbol files will be written.
   * @param symbolGenerator generator to produce the symbol files.
   */
  public void generateNativeSymbolFiles(
      File path, File symbolFileOutputDir, NativeSymbolGenerator symbolGenerator)
      throws IOException {

    if (!path.exists()) {
      throw new IOException(
          String.format(
              "Unstripped native library path does not exist: %s. %s",
              path.getAbsolutePath(), UNSTRIPPED_NATIVE_LIBS_DIR_ERR_MSG));
    }

    if (path.isFile() && FileUtils.isZipFile(path)) {
      File unzipDirectory = new File(symbolFileOutputDir, "unzippedLibsCache");

      logD(
          "Zipped input file detected: "
              + path.getAbsolutePath()
              + "; unzipping to temp location: "
              + unzipDirectory.getAbsolutePath());

      try {
        // Delete the directory, if it exists, to avoid processing old files.
        // (Note this could cause problems for parallel executions.)
        org.apache.commons.io.FileUtils.deleteQuietly(unzipDirectory);
        FileUtils.verifyDirectory(unzipDirectory);
        FileUtils.unzipArchive(path, unzipDirectory);

        generateNativeSymbolFiles(unzipDirectory, symbolFileOutputDir, symbolGenerator);
      } catch (Exception e) {
        throw e;
      } finally {
        logD("Cleaning up unzip target dir: " + unzipDirectory.getAbsolutePath());
        org.apache.commons.io.FileUtils.deleteQuietly(unzipDirectory);
      }
      return;
    }

    logD(
        "Generating native symbol files for "
            + path.getAbsolutePath()
            + "; writing output to: "
            + symbolFileOutputDir.getAbsolutePath());

    final Collection<File> soFiles =
        path.isDirectory()
            ? org.apache.commons.io.FileUtils.listFiles(
                path, NativeSymbolGenerator.SO_FILE_FILTER, TrueFileFilter.INSTANCE)
            : Collections.singleton(path);

    if (soFiles.isEmpty()) {
      throw new IOException(
          String.format(
              "No native libraries found at %s. %s",
              path.getAbsolutePath(), UNSTRIPPED_NATIVE_LIBS_DIR_ERR_MSG));
    } else {
      logD("" + soFiles.size() + " native libraries found at " + path);
      FileUtils.verifyDirectory(symbolFileOutputDir);
      try {
        for (File soFile : soFiles) {
          if (skipFile(soFile)) {
            continue;
          }
          File symbolFile = symbolGenerator.generateSymbols(soFile, symbolFileOutputDir);
          if (symbolFile == null) {
            logW(String.format("Null symbol file generated for %s", soFile.getAbsolutePath()));
          } else {
            logD(
                String.format(
                    "Generated symbol file: %s (%,d bytes)",
                    symbolFile.getAbsolutePath(), symbolFile.length()));
          }
        }
      } catch (CodeMappingException ex) {
        throw new IOException(ex);
      }
    }
  }

  /** Return true if the given file should be skipped. */
  private static boolean skipFile(File soFile) {
    if (soFile.getName().startsWith("libFirebase")) {
      logD(String.format("Skipping Firebase so file: %s", soFile.getName()));
      return true;
    }
    return false;
  }

  public void uploadNativeSymbolFiles(File symbolFileDir, String googleAppId) throws IOException {
    uploadNativeSymbolFiles(symbolFileDir, googleAppId, new CsymSymbolFileService());
  }

  /**
   * Uploads all the native symbol files (*.CSYM) in symbolFileDir. They will be deleted upon
   * successful upload.
   */
  public void uploadNativeSymbolFiles(
      File symbolFileDir, String googleAppId, SymbolFileService symbolFileService)
      throws IOException {

    logD("Uploading native symbol files from directory: " + symbolFileDir.getAbsolutePath());
    if (!symbolFileDir.exists()) {
      throw new IOException(
          "Crashlytics native symbol files directory does not exist: "
              + symbolFileDir.getAbsolutePath());
    }

    for (File symbolFile : symbolFileDir.listFiles()) {
      symbolFileService.uploadNativeSymbolFile(getWebApi(), symbolFile, googleAppId);
      logD(
          "Crashlytics symbol file uploaded successfully; deleting local file: "
              + symbolFile.getAbsolutePath());
      symbolFile.delete();
    }
  }

  /**
   * Sets optional info about the client invoking the buildtools. This info is added to HTTP headers
   * to Crashlytics servers.
   */
  public void setBuildtoolsClientInfo(String clientName, String clientVersion) {
    getWebApi().setClientType(clientName);
    getWebApi().setClientVersion(clientVersion);
    getWebApi().setUserAgent(clientName + "/" + clientVersion);
  }
}
