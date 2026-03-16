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

import com.google.common.base.Charsets;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes CSym data in the appropriate format.
 */
public class CSymWriter {

  private static final int CSYM_VERSION = 1;

  private static final String NO_INDEX = "-1";

  private static final String HEADER_FORMAT = "code_mapping\t%d\t%s\t%s\t%s\t%d\t%d\t%d\n";

  /**
   * Write CSym data to the given output stream.
   * @param cSym the CSym data to write.
   * @param os the output stream on which to write the CSym data.
   */
  public static void writeToOutputStream(CSym cSym, OutputStream os) throws IOException {
    write(cSym, new OutputStreamWriter(os, Charsets.UTF_8));
  }

  /**
   * Write CSym data to a simple text file.
   * @param cSym the CSym data to write.
   * @param file the File to write the CSym data.
   * @throws IOException if there is a problem writing to the given file.
   */
  public static void writeToTextFile(CSym cSym, File file) throws IOException {
    FileOutputStream fileOs = null;
    try {
      fileOs = new FileOutputStream(file);
      writeToOutputStream(cSym, fileOs);
    } finally {
      if (fileOs != null) {
        fileOs.close();
      }
    }
  }

  private static void write(CSym cSym, Writer writer) throws IOException {
    BufferedWriter out = new BufferedWriter(writer);
    writeHeader(cSym, out);
    writeFiles(cSym, out);
    writeSymbols(cSym, out);
    writeRanges(cSym, out);
    out.flush();
  }

  private static void writeHeader(CSym cSym, BufferedWriter out) throws IOException {
    out.append(
        String.format(
            HEADER_FORMAT,
            CSYM_VERSION,
            cSym.getType(),
            cSym.getUUID(),
            cSym.getArchitecture(),
            cSym.getFiles().size(),
            cSym.getSymbols().size(),
            cSym.getRanges().size()));
  }

  private static void writeFiles(CSym cSym, BufferedWriter out) throws IOException {
    List<String> files = cSym.getFiles();
    out.append("files\t" + files.size() + "\n");
    for (String file : files) {
      out.append(file + "\n");
    }
  }

  private static void writeSymbols(CSym cSym, BufferedWriter out) throws IOException {
    List<String> symbols = cSym.getSymbols();
    out.append("symbols\t" + symbols.size() + "\n");
    for (String symbol : symbols) {
      out.append(symbol + "\n");
    }
  }

  private static void writeRanges(CSym cSym, BufferedWriter out) throws IOException {
    List<String> files = cSym.getFiles();
    List<String> symbols = cSym.getSymbols();
    List<CSym.Range> ranges = cSym.getRanges();

    Map<String, String> fileMap = new HashMap<String, String>();
    Map<String, String> symbolMap = new HashMap<String, String>();

    for (int i = 0; i < symbols.size(); ++i) {
      symbolMap.put(symbols.get(i), String.valueOf(i));
    }

    for (int i = 0; i < files.size(); ++i) {
      fileMap.put(files.get(i), String.valueOf(i));
    }

    out.append("ranges\t" + ranges.size() + "\n");
    for (CSym.Range range : ranges) {
      out.append(
          range.offset
              + "\t"
              + range.size
              + "\t"
              + nullSafeIndex(symbolMap.get(range.symbol))
              + "\t"
              + nullSafeIndex(fileMap.get(range.file))
              + "\t"
              + range.lineNumber
              + "\n");
    }
  }

  private static String nullSafeIndex(String indexString) {
    return indexString != null ? indexString : NO_INDEX;
  }
}
