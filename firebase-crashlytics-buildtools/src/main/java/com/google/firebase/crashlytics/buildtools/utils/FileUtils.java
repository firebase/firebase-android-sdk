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

package com.google.firebase.crashlytics.buildtools.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;

public class FileUtils {

  public static final String GZIPPED_FILE_SUFFIX = ".gz";

  public static final String[] FILE_EXTENSIONS = {"so"};

  /**
   * Creates the directory specified by path if it doesn't exist. Throws an IOException if the File exists
   * but is not a directory, or if the directory can't be created.
   */
  public static void verifyDirectory(File path) throws IOException {
    if (!path.exists()) {
      path.mkdirs();
    }

    if (!path.exists() || !path.isDirectory()) {
      throw new IOException("Could not create directory: " + path);
    }
  }

  /**
   * Redirects in to out. Does not close either stream.
   */
  public static void redirect(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024];
    int len;
    while ((len = in.read(buffer)) != -1) {
      out.write(buffer, 0, len);
    }
    out.flush();
  }

  public static void gZipFile(File inputFile, File gZipOutputFile) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(inputFile));
        OutputStream out = new GZIPOutputStream(new FileOutputStream(gZipOutputFile))) {
      IOUtils.copy(in, out);
    }
  }

  /** Simple check that the filename ends with ".zip" (case insensitive) */
  public static boolean isZipFile(File path) {
    return path.exists() && path.isFile() && path.getName().toLowerCase().endsWith(".zip");
  }

  /**
   * Recursively unzips a file into a folder
   */
  public static void unzipArchive(File archive, File outputDir) throws IOException {
    final ZipFile zf = new ZipFile(archive);
    Enumeration<? extends ZipEntry> e = zf.entries();
    while (e.hasMoreElements()) {
      ZipEntry entry = (ZipEntry) e.nextElement();
      unzipEntry(zf, entry, outputDir);
    }
  }

  private static void unzipEntry(ZipFile zipfile, ZipEntry entry, File outputDir)
      throws IOException {
    if (entry.isDirectory()) {
      verifyDirectory(new File(outputDir, entry.getName()));
      return;
    }

    File outputFile = new File(outputDir, entry.getName());
    if (!outputFile.getParentFile().exists()) {
      verifyDirectory(outputFile.getParentFile());
    }

    BufferedInputStream inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
    BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));

    try {
      IOUtils.copy(inputStream, outputStream);
    } catch (IOException e) {
      throw e;
    } finally {
      outputStream.close();
      inputStream.close();
    }
  }

  public static void writeInputStreamToFile(InputStream source, File dest) throws IOException {
    if (!dest.exists()) {
      dest.createNewFile();
    }
    BufferedReader reader = null;
    PrintWriter writer = null;
    try {
      reader = new BufferedReader(new InputStreamReader(source));
      writer = new PrintWriter(dest, "UTF-8");

      String line;
      while ((line = reader.readLine()) != null) {
        writer.println(line);
      }

    } finally {
      if (writer != null) {
        writer.close();
      }
      if (reader != null) {
        reader.close();
      }
    }
  }

  public static Collection<File> listFiles(File dir) {
    return org.apache.commons.io.FileUtils.listFiles(dir, FILE_EXTENSIONS, true);
  }
}
