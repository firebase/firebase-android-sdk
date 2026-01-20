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

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public final class CrashlyticsOptions {

  public static final String OPT_INJECT_MAPPING_FILE_ID = "injectMappingFileIdIntoResource";
  public static final String OPT_UPLOAD_MAPPING_FILE = "uploadMappingFile";

  public static final String OPT_RESOURCE_FILE = "resourceFile";
  public static final String OPT_MAPPING_FILE_ID = "mappingFileId";
  public static final String OPT_OBFUSCATOR_NAME = "obfuscatorName";
  public static final String OPT_OBFUSCATOR_VERSION = "obfuscationVersion";

  public static final String OPT_VERBOSE = "verbose";
  public static final String OPT_QUIET = "quiet";
  public static final String OPT_CLIENT_NAME = "clientName";
  public static final String OPT_CLIENT_VERSION = "clientVersion";

  public static final String OPT_SYMBOL_GENERATOR_TYPE = "symbolGenerator";
  public static final String OPT_DUMP_SYMS_BINARY = "dumpSymsBinary";
  public static final String SYMBOL_GENERATOR_BREAKPAD = "breakpad";
  public static final String SYMBOL_GENERATOR_CSYM = "csym";
  public static final String SYMBOL_GENERATOR_DEFAULT = SYMBOL_GENERATOR_BREAKPAD;

  public static final String OPT_GENERATE_NATIVE_SYMBOLS = "generateNativeSymbols";
  public static final String OPT_UPLOAD_NATIVE_SYMBOLS = "uploadNativeSymbols";
  public static final String OPT_NATIVE_UNSTRIPPED_LIB = "unstrippedLibrary";
  public static final String OPT_NATIVE_UNSTRIPPED_LIBS_DIR = "unstrippedLibrariesDir";
  public static final String OPT_CSYM_CACHE_DIR = "symbolFileCacheDir";

  public static final String OPT_GOOGLE_APP_ID = "googleAppId";
  public static final String OPT_ANDROID_APPLICATION_ID = "androidApplicationId";

  public static final String OPT_HELP = "help";

  @SuppressWarnings({"static-access", "ProtectedMembersInFinalClass"})
  // TODO(b/477050030): Fix error prone warnings
  protected static Options createOptions() {
    Options options = new Options();

    options.addOption(new Option(OPT_VERBOSE, "Verbose command line output"));
    options.addOption(new Option(OPT_QUIET, "Silent command line output"));
    options.addOption(new Option(OPT_HELP, "Display command help."));

    options.addOption(
        Option.builder(OPT_CLIENT_NAME)
            .desc("Override the client name sent to Crashlytics in the User-Agent string.")
            .hasArg()
            .argName("clientName")
            .build());

    options.addOption(
        Option.builder(OPT_CLIENT_VERSION)
            .desc("Override the client version sent to Crashlytics in the User-Agent string.")
            .hasArg()
            .argName("clientVersion")
            .build());

    options.addOption(
        Option.builder(OPT_INJECT_MAPPING_FILE_ID)
            .desc(
                "Inject the provided mappingFileId as an Android resource into resourceFile. "
                    + "If not specified, a random mappingFileId will be generated.")
            .hasArg()
            .argName("resourceFile")
            .build());

    options.addOption(
        Option.builder(OPT_MAPPING_FILE_ID)
            .desc("ID to uniquely identifying the mapping file associated with this build.")
            .hasArg()
            .argName("mappingFileId")
            .build());

    options.addOption(
        Option.builder(OPT_RESOURCE_FILE)
            .desc(
                "Android XML resource file, in which to (optionally) locate the mapping file id "
                    + "when using "
                    + OPT_UPLOAD_MAPPING_FILE)
            .hasArg()
            .argName("resourceFile")
            .build());

    options.addOption(
        Option.builder(OPT_UPLOAD_MAPPING_FILE)
            .desc("Upload mappingFile with the associated mappingFileId.")
            .hasArg()
            .argName("mappingFile")
            .build());

    options.addOption(
        Option.builder(OPT_OBFUSCATOR_NAME)
            .desc(
                "Optionally specify an obfuscator vendor identifier for use with "
                    + OPT_OBFUSCATOR_VERSION)
            .hasArg()
            .argName("obfuscatorName")
            .build());

    options.addOption(
        Option.builder(OPT_OBFUSCATOR_VERSION)
            .desc("Optionally specify an obfuscator version for use with " + OPT_OBFUSCATOR_NAME)
            .hasArg()
            .argName("obfuscatorVersion")
            .build());

    options.addOption(
        Option.builder(OPT_GENERATE_NATIVE_SYMBOLS)
            .desc(
                "Generate native symbol mappings to be later uploaded with "
                    + OPT_UPLOAD_NATIVE_SYMBOLS)
            .build());

    options.addOption(
        Option.builder(OPT_UPLOAD_NATIVE_SYMBOLS)
            .desc(
                "Upload native symbol files generated with "
                    + OPT_GENERATE_NATIVE_SYMBOLS
                    + " to Crashlytics.")
            .build());

    options.addOption(
        Option.builder(OPT_NATIVE_UNSTRIPPED_LIB)
            .desc("Unstripped native library file containing debug symbols")
            .hasArg()
            .argName("unstrippedNativeLib")
            .build());

    options.addOption(
        Option.builder(OPT_NATIVE_UNSTRIPPED_LIBS_DIR)
            .desc("Directory path containing subdirs with unstripped native libraries.")
            .hasArg()
            .argName("unstrippedNativeLibsDir")
            .build());

    options.addOption(
        Option.builder(OPT_CSYM_CACHE_DIR)
            .desc(
                "Directory to store Crashlytics symbol files generated from unstripped NDK libraries.")
            .hasArg()
            .argName("symbolFileCacheDir")
            .build());

    options.addOption(
        Option.builder(OPT_SYMBOL_GENERATOR_TYPE)
            .desc(
                "Mode for native symbol generation. Must be one of ["
                    + SYMBOL_GENERATOR_BREAKPAD
                    + ","
                    + SYMBOL_GENERATOR_CSYM
                    + "]")
            .hasArg()
            .argName("nativeSymbolGenerator")
            .build());

    options.addOption(
        Option.builder(OPT_DUMP_SYMS_BINARY)
            .desc(
                "Path to dump_syms.bin, used with the "
                    + OPT_SYMBOL_GENERATOR_TYPE
                    + "="
                    + SYMBOL_GENERATOR_BREAKPAD
                    + " option. If not specified, the bundled dump_syms.bin"
                    + " will be extracted to the local .crashlytics directory and used by default.")
            .hasArg()
            .argName("dumpSymsBinary")
            .build());

    options.addOption(
        Option.builder(OPT_GOOGLE_APP_ID)
            .desc("Google App Id, generally found in google-services.json")
            .hasArg()
            .argName("googleAppId")
            .build());

    options.addOption(
        Option.builder(OPT_ANDROID_APPLICATION_ID)
            .desc("Android application id as declared in the Android Manifest.")
            .hasArg()
            .argName("AndroidAppId")
            .build());

    return options;
  }

  private CrashlyticsOptions() {
    // Not intended for instantiation
  }
}
