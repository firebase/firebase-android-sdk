// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.persistence;

import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.model.serialization.CrashlyticsReportJsonTransform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class handles persisting report and event data to disk, combining reports with their
 * associated events into "finalized" report files, reading reports from disk, parsing them to be
 * returned as CrashlyticsReport objects, and deleting them.
 */
public class CrashlyticsReportPersistence {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final String WORKING_DIRECTORY_NAME = "fl";
  private static final String OPEN_SESSIONS_DIRECTORY_NAME = "sessions";
  private static final String FATAL_DIRECTORY_NAME = "fatal";
  private static final String NON_FATAL_DIRECTORY_NAME = "non-fatal";

  private static final String REPORT_FILE_NAME = "report.json";
  private static final String EVENT_FILE_NAME_PREFIX = "event";
  private static final String EVENT_FILE_NAME_FORMAT = EVENT_FILE_NAME_PREFIX + "%s.json";
  private static final String EVENT_COUNTER_FORMAT = "%010d";

  private static final String EVENT_TYPE_FATAL = "crash";

  private static final CrashlyticsReportJsonTransform TRANSFORM =
      new CrashlyticsReportJsonTransform();

  private final AtomicInteger eventCounter = new AtomicInteger(0);

  // Storage for sessions that are still being written to
  private File openSessionsDirectory;

  // Storage for finalized reports
  // Keep finalized reports organized by whether or not they contain a fatal event.
  private File fatalReportsDirectory;
  private File nonFatalReportsDirectory;

  public CrashlyticsReportPersistence(File rootDirectory) {
    final File workingDirectory = new File(rootDirectory, WORKING_DIRECTORY_NAME);
    openSessionsDirectory = new File(workingDirectory, OPEN_SESSIONS_DIRECTORY_NAME);
    fatalReportsDirectory = new File(workingDirectory, FATAL_DIRECTORY_NAME);
    nonFatalReportsDirectory = new File(workingDirectory, NON_FATAL_DIRECTORY_NAME);
  }

  public void persistReport(CrashlyticsReport report) {
    final String sessionId = report.getSession().getIdentifier();
    final File sessionDirectory = prepareDirectory(getSessionDirectoryById(sessionId));
    final String json = TRANSFORM.reportToJson(report);
    writeTextFile(new File(sessionDirectory, REPORT_FILE_NAME), json);
  }

  public void persistEvent(CrashlyticsReport.Session.Event event, String sessionId) {
    final File sessionDirectory = getSessionDirectoryById(sessionId);
    if (!sessionDirectory.isDirectory()) {
      // No open session for this ID
      // TODO: Just drop the event? Log? Throw?
      return;
    }
    final String json = TRANSFORM.eventToJson(event);
    final String eventNumber =
        String.format(Locale.US, EVENT_COUNTER_FORMAT, eventCounter.getAndIncrement());
    final String fileName = String.format(EVENT_FILE_NAME_FORMAT, eventNumber);
    writeTextFile(new File(sessionDirectory, fileName), json);
  }

  public void deleteFinalizedReport(String sessionId) {
    final List<File> reportFiles = new ArrayList<>();
    final FilenameFilter filter = (d, f) -> f.startsWith(sessionId);
    // Could be in either fatal reports or non-fatal reports
    reportFiles.addAll(getFilesInDirectory(fatalReportsDirectory, filter));
    reportFiles.addAll(getFilesInDirectory(nonFatalReportsDirectory, filter));
    for (File reportFile : reportFiles) {
      reportFile.delete();
    }
  }

  // TODO: Deal with potential runtime exceptions
  public void finalizeReports(String currentSessionId) {
    // TODO: Trim down to maximum allowed # of open sessions

    // TODO: Trim down to maximum allowed # of complete reports, deleting non-fatal reports first.

    // TODO: Need to implement procedure to skip finalizing the current session when this is
    //  called on app start, but keep the current session when called at crash time. Currently
    //  this only works when called at app start.
    final FileFilter sessionDirectoryFilter =
        (f) -> f.isDirectory() && !f.getName().equals(currentSessionId);

    final List<File> sessionDirectories =
        getFilesInDirectory(openSessionsDirectory, sessionDirectoryFilter);
    for (File sessionDirectory : sessionDirectories) {
      final List<File> eventFiles =
          getFilesInDirectory(
              sessionDirectory, (f, name) -> name.startsWith(EVENT_FILE_NAME_PREFIX));
      Collections.sort(eventFiles);
      // TODO: Fix nulls
      // Only process the session if it has associated events
      if (!eventFiles.isEmpty()) {
        final CrashlyticsReport report =
            TRANSFORM.reportFromJson(readTextFile(new File(sessionDirectory, REPORT_FILE_NAME)));
        final String sessionId = report.getSession().getIdentifier();
        final List<Event> events = new ArrayList<>();
        boolean hasFatal = false;
        for (File eventFile : eventFiles) {
          final Event event = TRANSFORM.eventFromJson(readTextFile(eventFile));
          hasFatal = hasFatal || event.getType().equals(EVENT_TYPE_FATAL);
          events.add(event);
        }
        // FIXME: If we fail to parse the events, we'll need to bail.
        final File outputDirectory =
            prepareDirectory(hasFatal ? fatalReportsDirectory : nonFatalReportsDirectory);
        writeTextFile(
            new File(outputDirectory, sessionId),
            TRANSFORM.reportToJson(report.withEvents(ImmutableList.from(events))));
      }
      recursiveDelete(sessionDirectory);
    }
  }

  public List<CrashlyticsReport> loadFinalizedReports() {
    final List<CrashlyticsReport> reports = new ArrayList<>();
    final List<File> fatalReports = getAllFilesInDirectory(fatalReportsDirectory);
    for (File reportFile : fatalReports) {
      reports.add(TRANSFORM.reportFromJson(readTextFile(reportFile)));
    }
    final List<File> nonFatalReports = getAllFilesInDirectory(nonFatalReportsDirectory);
    for (File reportFile : nonFatalReports) {
      reports.add(TRANSFORM.reportFromJson(readTextFile(reportFile)));
    }
    return reports;
  }

  private File getSessionDirectoryById(String sessionId) {
    return new File(openSessionsDirectory, sessionId);
  }

  private static List<File> getAllFilesInDirectory(File directory) {
    return getFilesInDirectory(directory, (FileFilter) null);
  }

  private static List<File> getFilesInDirectory(File directory, FilenameFilter filter) {
    if (directory == null || !directory.isDirectory()) {
      return Collections.emptyList();
    }
    final File[] files = (filter == null) ? directory.listFiles() : directory.listFiles(filter);
    return (files != null) ? Arrays.asList(files) : Collections.emptyList();
  }

  private static List<File> getFilesInDirectory(File directory, FileFilter filter) {
    if (directory == null || !directory.isDirectory()) {
      return Collections.emptyList();
    }
    final File[] files = (filter == null) ? directory.listFiles() : directory.listFiles(filter);
    return (files != null) ? Arrays.asList(files) : Collections.emptyList();
  }

  private static File prepareDirectory(File directory) {
    if (directory == null) {
      return null;
    }

    if (directory.exists() || directory.mkdirs()) {
      return directory;
    }

    // TODO: Couldn't create directory. Log? Throw?
    return null;
  }

  private static void writeTextFile(File file, String text) {
    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), UTF_8)) {
      writer.write(text);
    } catch (IOException e) {
      // TODO: Exception writing file to disk. Log? Throw?
    }
  }

  private static String readTextFile(File file) {
    final byte[] readBuffer = new byte[8192];
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (FileInputStream fileInput = new FileInputStream(file)) {
      int read;
      while ((read = fileInput.read(readBuffer)) > 0) {
        bos.write(readBuffer, 0, read);
      }
      return new String(bos.toByteArray(), UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

  private static void recursiveDelete(File file) {
    if (file == null) {
      return;
    }
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        recursiveDelete(f);
      }
    }
    file.delete();
  }
}
