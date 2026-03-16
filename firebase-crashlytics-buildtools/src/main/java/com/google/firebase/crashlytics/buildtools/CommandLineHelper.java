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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.crashlytics.buildtools.api.SymbolFileService;
import com.google.firebase.crashlytics.buildtools.log.CrashlyticsLogger;
import com.google.firebase.crashlytics.buildtools.ndk.NativeSymbolGenerator;
import com.google.firebase.crashlytics.buildtools.ndk.internal.breakpad.BreakpadSymbolFileService;
import com.google.firebase.crashlytics.buildtools.ndk.internal.breakpad.BreakpadSymbolGenerator;
import com.google.firebase.crashlytics.buildtools.ndk.internal.csym.CsymSymbolFileService;
import com.google.firebase.crashlytics.buildtools.ndk.internal.csym.NdkCSymGenerator;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

public class CommandLineHelper {

  /**
   * At least one of these commands must be used for build tools CLI to function.
   */
  private static final String[] VALID_COMMANDS = {
    CrashlyticsOptions.OPT_HELP,
    CrashlyticsOptions.OPT_INJECT_MAPPING_FILE_ID,
    CrashlyticsOptions.OPT_UPLOAD_MAPPING_FILE,
    CrashlyticsOptions.OPT_GENERATE_NATIVE_SYMBOLS,
    CrashlyticsOptions.OPT_UPLOAD_NATIVE_SYMBOLS
  };

  private final CommandLine cmd;

  private static final Pattern GOOGLE_APP_ID_PATTERN =
      Pattern.compile("(\\d+):(\\d+):(\\w+):(\\p{XDigit}+)");

  public static void main(String[] args) {
    boolean verbose = false;
    try {
      // Manually parse the log level, so we'll log any other argument parsing correctly:
      CrashlyticsLogger logger = Buildtools.getLogger();

      boolean quiet = false;
      for (String arg : args) {
        if (arg.equals("-" + CrashlyticsOptions.OPT_VERBOSE)) {
          verbose = true;
          break; // Verbose supersedes quiet, so we can stop checking.
        } else if (arg.equals("-" + CrashlyticsOptions.OPT_QUIET)) {
          quiet = true;
        }
      }
      if (verbose) {
        Buildtools.getLogger().setLevel(CrashlyticsLogger.Level.VERBOSE);
      } else if (quiet) {
        Buildtools.getLogger().setLevel(CrashlyticsLogger.Level.ERROR);
      }
      Options options = CrashlyticsOptions.createOptions();
      CommandLine cmd = new DefaultParser().parse(options, args);

      if (cmd.hasOption(CrashlyticsOptions.OPT_HELP)) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(Buildtools.class.getName(), options);
        return;
      }

      CommandLineHelper cli = new CommandLineHelper(cmd);
      cli.executeCommand();

    } catch (Exception e) {
      String msg =
          "Crashlytics execution failed."
              + (!verbose
                  ? " Run with -" + CrashlyticsOptions.OPT_VERBOSE + " for additional output."
                  : "");
      Buildtools.logE(msg, e);
      System.exit(-1);
    }
  }

  private void configureWebApi() {
    Buildtools.setWebApi(Buildtools.createWebApi());

    // Metadata for the default user-agent string is read from the manifest info
    Package packageInfo = CommandLineHelper.class.getPackage();
    String clientName = packageInfo.getImplementationTitle();
    String clientVersion = packageInfo.getImplementationVersion();

    // Clients can override the user-agent string via these command line options
    if (cmd.hasOption(CrashlyticsOptions.OPT_CLIENT_NAME)) {
      clientName = cmd.getOptionValue(CrashlyticsOptions.OPT_CLIENT_NAME);
    }
    if (cmd.hasOption(CrashlyticsOptions.OPT_CLIENT_VERSION)) {
      clientVersion = cmd.getOptionValue(CrashlyticsOptions.OPT_CLIENT_VERSION);
    }

    Buildtools.getInstance().setBuildtoolsClientInfo(clientName, clientVersion);
  }

  public CommandLineHelper(CommandLine cmd) {
    this.cmd = cmd;
  }

  public void executeCommand() throws IOException {
    configureWebApi();

    // Need exactly one of the main commands
    int commandCount = 0;
    for (String validCommand : VALID_COMMANDS) {
      if (cmd.hasOption(validCommand)) {
        commandCount++;
      }
    }

    if (commandCount != 1) {
      throw new IllegalArgumentException(
          "Exactly ONE valid command required. Use '-help' valid arguments.");
    }

    if (cmd.hasOption(CrashlyticsOptions.OPT_INJECT_MAPPING_FILE_ID)) {
      executeInjectMappingFileId();
    } else if (cmd.hasOption(CrashlyticsOptions.OPT_UPLOAD_MAPPING_FILE)) {
      executeUploadMappingFile();
    } else if (cmd.hasOption(CrashlyticsOptions.OPT_GENERATE_NATIVE_SYMBOLS)) {
      executeGenerateSymbols();
    } else if (cmd.hasOption(CrashlyticsOptions.OPT_UPLOAD_NATIVE_SYMBOLS)) {
      executeUploadSymbols();
    }
  }

  private void executeInjectMappingFileId() throws IOException {
    // Required inputs are the resource file and the mapping file ID.
    final String resourceFilePath =
        getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_INJECT_MAPPING_FILE_ID);
    if (cmd.hasOption(CrashlyticsOptions.OPT_MAPPING_FILE_ID)) {
      String mappingFileId = getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_MAPPING_FILE_ID);
      Buildtools.getInstance()
          .injectMappingFileIdIntoResource(new File(resourceFilePath), mappingFileId);
    } else {
      Buildtools.getInstance().injectMappingFileIdIntoResource(new File(resourceFilePath));
    }
  }

  private void executeUploadMappingFile() throws IOException {
    File mappingFile =
        new File(getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_UPLOAD_MAPPING_FILE));
    String googleAppId = getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_GOOGLE_APP_ID);
    validateGoogleAppId(googleAppId);
    // Android application id is not required; nulls allowed.
    String androidApplicationId =
        cmd.getOptionValue(CrashlyticsOptions.OPT_ANDROID_APPLICATION_ID, null);

    AppBuildInfo appInfo = new AppBuildInfo(androidApplicationId, googleAppId, null);
    Obfuscator obfuscator = new Obfuscator(Obfuscator.Vendor.PROGUARD, "0.0.0");

    // Get the mappingFileId from the OPT_MAPPING_FILE_ID arg, or read it from OPT_RESOURCE_FILE
    if (cmd.hasOption(CrashlyticsOptions.OPT_MAPPING_FILE_ID)
        && !cmd.hasOption(CrashlyticsOptions.OPT_RESOURCE_FILE)) {

      String mappingFileId = getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_MAPPING_FILE_ID);
      Buildtools.getInstance().uploadMappingFile(mappingFile, mappingFileId, appInfo, obfuscator);
    } else if (cmd.hasOption(CrashlyticsOptions.OPT_RESOURCE_FILE)
        && !cmd.hasOption(CrashlyticsOptions.OPT_MAPPING_FILE_ID)) {

      File resourceFile =
          new File(getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_RESOURCE_FILE));
      Buildtools.getInstance().uploadMappingFile(mappingFile, resourceFile, appInfo, obfuscator);
    } else {
      throw new IllegalArgumentException(
          "When executing "
              + CrashlyticsOptions.OPT_UPLOAD_MAPPING_FILE
              + ", use either "
              + CrashlyticsOptions.OPT_MAPPING_FILE_ID
              + " or "
              + CrashlyticsOptions.OPT_RESOURCE_FILE
              + " (but not both).");
    }
  }

  private void executeGenerateSymbols() throws IOException {
    final String unstrippedLib = cmd.getOptionValue(CrashlyticsOptions.OPT_NATIVE_UNSTRIPPED_LIB);
    final String unstrippedLibsDir =
        cmd.getOptionValue(CrashlyticsOptions.OPT_NATIVE_UNSTRIPPED_LIBS_DIR);

    boolean useSingleLib = (unstrippedLib != null);
    boolean useDirs = (unstrippedLibsDir != null);

    if (!Boolean.logicalXor(useSingleLib, useDirs)) {
      throw new IllegalArgumentException(
          CrashlyticsOptions.OPT_GENERATE_NATIVE_SYMBOLS
              + " requires either 1) "
              + CrashlyticsOptions.OPT_NATIVE_UNSTRIPPED_LIB
              + " or 2) "
              + CrashlyticsOptions.OPT_NATIVE_UNSTRIPPED_LIBS_DIR);
    }

    final File csymDir =
        new File(getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_CSYM_CACHE_DIR));
    FileUtils.verifyDirectory(csymDir);

    final File path = useSingleLib ? new File(unstrippedLib) : new File(unstrippedLibsDir);
    final NativeSymbolGenerator symbolGenerator = createSymbolGenerator(cmd);

    Buildtools.getInstance().generateNativeSymbolFiles(path, csymDir, symbolGenerator);
  }

  private void executeUploadSymbols() throws IOException {
    final File csymDir =
        new File(getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_CSYM_CACHE_DIR));
    FileUtils.verifyDirectory(csymDir);
    final String googleAppId = getOptionValueOrThrow(cmd, CrashlyticsOptions.OPT_GOOGLE_APP_ID);
    validateGoogleAppId(googleAppId);
    final SymbolFileService symbolFileService = createSymbolFileService(cmd);

    Buildtools.getInstance().uploadNativeSymbolFiles(csymDir, googleAppId, symbolFileService);
  }

  private static String getOptionValueOrThrow(CommandLine cmd, String optionKey)
      throws IllegalArgumentException {
    String val = cmd.getOptionValue(optionKey);
    if (val == null) {
      throw new IllegalArgumentException("Required argument missing: " + optionKey);
    }
    return val;
  }

  private static NativeSymbolGenerator createSymbolGenerator(CommandLine cmd)
      throws IllegalArgumentException, IOException {
    final String symbolGenMode =
        cmd.getOptionValue(
            CrashlyticsOptions.OPT_SYMBOL_GENERATOR_TYPE,
            CrashlyticsOptions.SYMBOL_GENERATOR_DEFAULT);
    if (CrashlyticsOptions.SYMBOL_GENERATOR_BREAKPAD.equals(symbolGenMode)) {
      final File dumpSymsBin = resolveDumpSymsBinary(cmd);
      return new BreakpadSymbolGenerator(dumpSymsBin);
    } else if (CrashlyticsOptions.SYMBOL_GENERATOR_CSYM.equals(symbolGenMode)) {
      return new NdkCSymGenerator();
    }
    throwInvalidSymbolGeneratorMode(symbolGenMode);
    return null;
  }

  private static SymbolFileService createSymbolFileService(CommandLine cmd)
      throws IllegalArgumentException {
    final String symbolGenMode =
        cmd.getOptionValue(
            CrashlyticsOptions.OPT_SYMBOL_GENERATOR_TYPE,
            CrashlyticsOptions.SYMBOL_GENERATOR_DEFAULT);
    if (CrashlyticsOptions.SYMBOL_GENERATOR_BREAKPAD.equals(symbolGenMode)) {
      return new BreakpadSymbolFileService();
    } else if (CrashlyticsOptions.SYMBOL_GENERATOR_CSYM.equals(symbolGenMode)) {
      return new CsymSymbolFileService();
    }
    throwInvalidSymbolGeneratorMode(symbolGenMode);
    return null;
  }

  /**
   * Returns the dumpSymsBinary File object specificied in the cli. If the option value is not
   * used, the method will extract the default dump syms binary in the local .crashlytics
   * directory.
   */
  private static File resolveDumpSymsBinary(CommandLine cmd)
      throws IllegalArgumentException, IOException {
    final File toReturn;
    if (cmd.hasOption(CrashlyticsOptions.OPT_DUMP_SYMS_BINARY)) {
      toReturn = new File(cmd.getOptionValue(CrashlyticsOptions.OPT_DUMP_SYMS_BINARY));
      if (toReturn == null) {
        throw new IllegalArgumentException(
            "Option " + CrashlyticsOptions.OPT_DUMP_SYMS_BINARY + " specified without a value.");
      }
    } else {
      final File defaultBinaryDir = new File(".crashlytics");
      if (!defaultBinaryDir.isDirectory()) {
        if (defaultBinaryDir.isFile()) {
          throw new IOException(
              "Could not create Crashlytics directory, a file "
                  + "already exists at that location: "
                  + defaultBinaryDir.getAbsolutePath());
        }
        defaultBinaryDir.mkdir();
      }
      toReturn = BreakpadSymbolGenerator.extractDefaultDumpSymsBinary(defaultBinaryDir);
    }

    return toReturn;
  }

  private static void throwInvalidSymbolGeneratorMode(String symbolGenMode)
      throws IllegalArgumentException {
    throw new IllegalArgumentException(
        "Invalid argument for "
            + CrashlyticsOptions.OPT_SYMBOL_GENERATOR_TYPE
            + " ("
            + symbolGenMode
            + "), must be one of ["
            + CrashlyticsOptions.SYMBOL_GENERATOR_BREAKPAD
            + ", "
            + CrashlyticsOptions.SYMBOL_GENERATOR_CSYM
            + "]");
  }

  @VisibleForTesting
  static void validateGoogleAppId(String googleAppId) {
    checkArgument(
        GOOGLE_APP_ID_PATTERN.matcher(googleAppId).matches(),
        "Google App ID parameter doesn't match the expected format. Check that the parameter has been passed in correctly.");
  }
}
