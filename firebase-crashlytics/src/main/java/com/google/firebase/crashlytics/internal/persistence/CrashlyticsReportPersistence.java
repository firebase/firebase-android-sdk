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

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.model.serialization.CrashlyticsReportJsonTransform;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
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
import java.util.Comparator;
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

  private static final int MAX_OPEN_SESSIONS = 8;
  private static final String WORKING_DIRECTORY_NAME = "report-persistence";
  private static final String OPEN_SESSIONS_DIRECTORY_NAME = "sessions";
  private static final String PRIORITY_REPORTS_DIRECTORY = "priority-reports";
  private static final String NATIVE_REPORTS_DIRECTORY = "native-reports";
  private static final String REPORTS_DIRECTORY = "reports";

  private static final String REPORT_FILE_NAME = "report";
  private static final String USER_FILE_NAME = "user";
  private static final String EVENT_FILE_NAME_PREFIX = "event";
  private static final int EVENT_COUNTER_WIDTH = 10; // String width of maximum positive int value
  private static final String EVENT_COUNTER_FORMAT = "%0" + EVENT_COUNTER_WIDTH + "d";
  private static final int EVENT_NAME_LENGTH =
      EVENT_FILE_NAME_PREFIX.length() + EVENT_COUNTER_WIDTH;
  private static final String PRIORITY_EVENT_SUFFIX = "_";
  private static final String NORMAL_EVENT_SUFFIX = "";

  private static final CrashlyticsReportJsonTransform TRANSFORM =
      new CrashlyticsReportJsonTransform();
  private static final Comparator<? super File> LATEST_SESSION_ID_FIRST_COMPARATOR =
      (f1, f2) -> f2.getName().compareTo(f1.getName());
  private static final FilenameFilter EVENT_FILE_FILTER =
      (f, name) -> name.startsWith(EVENT_FILE_NAME_PREFIX);

  private final AtomicInteger eventCounter = new AtomicInteger(0);

  // Storage for sessions that are still being written to
  private final File openSessionsDirectory;

  // Storage for finalized reports
  private final File priorityReportsDirectory;
  private final File reportsDirectory;

  // Storage for ndk Reports
  private final File nativeReportsDirectory;

  private final SettingsDataProvider settingsDataProvider;

  public CrashlyticsReportPersistence(
      File rootDirectory, SettingsDataProvider settingsDataProvider) {
    final File workingDirectory = new File(rootDirectory, WORKING_DIRECTORY_NAME);
    openSessionsDirectory = new File(workingDirectory, OPEN_SESSIONS_DIRECTORY_NAME);
    priorityReportsDirectory = new File(workingDirectory, PRIORITY_REPORTS_DIRECTORY);
    reportsDirectory = new File(workingDirectory, REPORTS_DIRECTORY);
    nativeReportsDirectory = new File(workingDirectory, NATIVE_REPORTS_DIRECTORY);
    this.settingsDataProvider = settingsDataProvider;
  }

  public void persistReport(CrashlyticsReport report) {
    final String sessionId = report.getSession().getIdentifier();
    final File sessionDirectory = prepareDirectory(getSessionDirectoryById(sessionId));
    final String json = TRANSFORM.reportToJson(report);
    writeTextFile(new File(sessionDirectory, REPORT_FILE_NAME), json);
  }

  /**
   * Persist an event for a given session with normal priority.
   *
   * <p>Only a certain number of normal priority events are stored per-session. When this maximum is
   * reached, the oldest events will be dropped.
   *
   * @param event
   * @param sessionId
   */
  public void persistEvent(CrashlyticsReport.Session.Event event, String sessionId) {
    persistEvent(event, sessionId, false);
  }

  /**
   * Persist an event for a given session, specifying whether or not it is high priority.
   *
   * <p>Only a certain number of normal priority events are stored per-session. When this maximum is
   * reached, the oldest events will be dropped. High priority events are not subject to this limit.
   *
   * @param event
   * @param sessionId
   * @param isHighPriority
   */
  public void persistEvent(
      CrashlyticsReport.Session.Event event, String sessionId, boolean isHighPriority) {
    int maxEventsToKeep =
        settingsDataProvider.getSettings().getSessionData().maxCustomExceptionEvents;
    final File sessionDirectory = getSessionDirectoryById(sessionId);
    if (!sessionDirectory.isDirectory()) {
      // No open session for this ID
      // TODO: Just drop the event? Log? Throw?
      return;
    }
    final String json = TRANSFORM.eventToJson(event);
    final String fileName = generateEventFilename(eventCounter.getAndIncrement(), isHighPriority);
    writeTextFile(new File(sessionDirectory, fileName), json);
    trimEvents(sessionDirectory, maxEventsToKeep);
  }

  public void persistUserIdForSession(String userId, String sessionId) {
    final File sessionDirectory = getSessionDirectoryById(sessionId);
    if (!sessionDirectory.isDirectory()) {
      // No open session for this ID
      // TODO: Just drop the event? Log? Throw?
      return;
    }
    writeTextFile(new File(sessionDirectory, USER_FILE_NAME), userId);
  }

  public void deleteAllReports() {
    for (File reportFile : getAllFinalizedReportFiles()) {
      reportFile.delete();
    }
  }

  public void deleteFinalizedReport(String sessionId) {
    final FilenameFilter filter = (d, f) -> f.startsWith(sessionId);
    List<File> filteredReports =
        sortAndCombineReportFiles(
            getFilesInDirectory(priorityReportsDirectory, filter),
            getFilesInDirectory(reportsDirectory, filter));
    for (File reportFile : filteredReports) {
      reportFile.delete();
    }
  }

  public void finalizeSessionWithNativeEvent(
      String sessionId, CrashlyticsReport crashlyticsReport) {
    final File outputDirectory = prepareDirectory(nativeReportsDirectory);
    writeTextFile(new File(outputDirectory, sessionId), TRANSFORM.reportToJson(crashlyticsReport));
  }

  // TODO: Deal with potential runtime exceptions
  public void finalizeReports(String currentSessionId, long sessionEndTime) {
    // TODO: Need to implement procedure to skip finalizing the current session when this is
    //  called on app start, but keep the current session when called at crash time. Currently
    //  this only works when called at app start.
    List<File> sessionDirectories = capAndGetOpenSessions(currentSessionId);
    for (File sessionDirectory : sessionDirectories) {
      synthesizeReportFile(sessionDirectory, sessionEndTime);
      recursiveDelete(sessionDirectory);
    }

    capFinalizedReports();
  }

  /**
   * @return finalized (no longer changing) Crashlytics Reports, sorted first from high to low
   *     priority, secondarily sorted from most recent to least
   */
  public List<CrashlyticsReport> loadFinalizedReports() {
    final List<File> allReportFiles = getAllFinalizedReportFiles();
    final ArrayList<CrashlyticsReport> allReports = new ArrayList<>();
    allReports.ensureCapacity(allReportFiles.size());
    for (File reportFile : getAllFinalizedReportFiles()) {
      allReports.add(TRANSFORM.reportFromJson(readTextFile(reportFile)));
    }
    return allReports;
  }

  private List<File> capAndGetOpenSessions(String currentSessionId) {
    final FileFilter sessionDirectoryFilter =
        (f) -> f.isDirectory() && !f.getName().equals(currentSessionId);
    List<File> openSessionDirectories =
        getFilesInDirectory(openSessionsDirectory, sessionDirectoryFilter);
    Collections.sort(openSessionDirectories, LATEST_SESSION_ID_FIRST_COMPARATOR);
    if (openSessionDirectories.size() <= MAX_OPEN_SESSIONS) {
      return openSessionDirectories;
    }

    // Make a sublist of the reports that go over the size limit
    List<File> openSessionDirectoriesToRemove =
        openSessionDirectories.subList(MAX_OPEN_SESSIONS, openSessionDirectories.size());
    for (File openSessionDirectory : openSessionDirectoriesToRemove) {
      recursiveDelete(openSessionDirectory);
    }
    return openSessionDirectories.subList(0, MAX_OPEN_SESSIONS);
  }

  private void capFinalizedReports() {
    int maxReportsToKeep =
        settingsDataProvider.getSettings().getSessionData().maxCompleteSessionsCount;
    List<File> finalizedReportFiles = getAllFinalizedReportFiles();

    int fileCount = finalizedReportFiles.size();
    if (fileCount <= maxReportsToKeep) {
      return;
    }

    // Make a sublist of the reports that go over the size limit
    List<File> filesToRemove = finalizedReportFiles.subList(maxReportsToKeep, fileCount);
    for (File reportFile : filesToRemove) {
      reportFile.delete();
    }
  }

  /**
   * @return finalized (no longer changing) files for Crashlytics Reports, sorted first from high to
   *     low priority, secondarily sorted from most recent to least
   */
  @NonNull
  private List<File> getAllFinalizedReportFiles() {
    return sortAndCombineReportFiles(
        combineReportFiles(
            getAllFilesInDirectory(priorityReportsDirectory),
            getAllFilesInDirectory(nativeReportsDirectory)),
        getAllFilesInDirectory(reportsDirectory));
  }

  private File getSessionDirectoryById(String sessionId) {
    return new File(openSessionsDirectory, sessionId);
  }

  private void synthesizeReportFile(File sessionDirectory, long sessionEndTime) {
    final List<File> eventFiles = getFilesInDirectory(sessionDirectory, EVENT_FILE_FILTER);

    // Only process the session if it has associated events
    if (eventFiles.isEmpty()) {
      return;
    }

    // TODO: Handle all nullable return values in below function calls

    Collections.sort(eventFiles);
    final List<Event> events = new ArrayList<>();
    boolean isHighPriorityReport = false;
    for (File eventFile : eventFiles) {
      final String eventJson = readTextFile(eventFile);
      if (eventJson == null) {
        // TODO: Handle null case
        continue;
      }
      final Event event = TRANSFORM.eventFromJson(eventJson);
      isHighPriorityReport = isHighPriorityReport || isHighPriorityEventFile(eventFile.getName());
      events.add(event);
    }
    // FIXME: If we fail to parse the events, we'll need to bail.

    String userId = null;
    final File userFile = new File(sessionDirectory, USER_FILE_NAME);
    if (userFile.exists()) {
      userId = readTextFile(userFile);
    }

    CrashlyticsReport report =
        TRANSFORM.reportFromJson(readTextFile(new File(sessionDirectory, REPORT_FILE_NAME)));

    if (report == null) {
      // TODO: Handle null case
      return;
    }

    report = report.withSessionEndFields(sessionEndTime, isHighPriorityReport, userId);

    final String sessionId = report.getSession().getIdentifier();

    final File outputDirectory =
        prepareDirectory(isHighPriorityReport ? priorityReportsDirectory : reportsDirectory);
    writeTextFile(
        new File(outputDirectory, sessionId),
        TRANSFORM.reportToJson(report.withEvents(ImmutableList.from(events))));
  }

  @NonNull
  private static List<File> sortAndCombineReportFiles(List<File>... reports) {
    for (List<File> reportList : reports) {
      Collections.sort(reportList, LATEST_SESSION_ID_FIRST_COMPARATOR);
    }

    return combineReportFiles(reports);
  }

  private static List<File> combineReportFiles(List<File>... reports) {
    final ArrayList<File> allReportsFiles = new ArrayList<>();
    int totalReports = 0;
    for (List<File> reportList : reports) {
      totalReports += reportList.size();
    }
    allReportsFiles.ensureCapacity(totalReports);
    for (List<File> reportList : reports) {
      allReportsFiles.addAll(reportList);
    }
    return allReportsFiles;
  }

  private static boolean isHighPriorityEventFile(String fileName) {
    return fileName.startsWith(EVENT_FILE_NAME_PREFIX) && fileName.endsWith(PRIORITY_EVENT_SUFFIX);
  }

  private static boolean isNormalPriorityEventFile(File dir, String name) {
    return name.startsWith(EVENT_FILE_NAME_PREFIX) && !name.endsWith(PRIORITY_EVENT_SUFFIX);
  }

  private static String generateEventFilename(int eventNumber, boolean isHighPriority) {
    final String paddedEventNumber = String.format(Locale.US, EVENT_COUNTER_FORMAT, eventNumber);
    final String prioritySuffix = isHighPriority ? PRIORITY_EVENT_SUFFIX : NORMAL_EVENT_SUFFIX;
    return EVENT_FILE_NAME_PREFIX + paddedEventNumber + prioritySuffix;
  }

  private static int trimEvents(File sessionDirectory, int maximum) {
    final List<File> normalPriorityEventFiles =
        getFilesInDirectory(
            sessionDirectory, CrashlyticsReportPersistence::isNormalPriorityEventFile);
    Collections.sort(normalPriorityEventFiles, CrashlyticsReportPersistence::oldestEventFileFirst);
    return capFilesCount(normalPriorityEventFiles, maximum);
  }

  private static String getEventNameWithoutPriority(String eventFileName) {
    return eventFileName.substring(0, EVENT_NAME_LENGTH);
  }

  private static int oldestEventFileFirst(File f1, File f2) {
    final String name1 = getEventNameWithoutPriority(f1.getName());
    final String name2 = getEventNameWithoutPriority(f2.getName());
    return name1.compareTo(name2);
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
      // TODO: Exception reading file from disk. Log? Throw?
      return null;
    }
  }

  /**
   * Deletes files from the list until the list size is equal to the maximum. If list is already
   * correctly sized, no files are deleted. List should be sorted in the order in which files should
   * be deleted.
   *
   * @return the number of files retained on disk
   */
  private static int capFilesCount(List<File> files, int maximum) {
    int numRetained = files.size();
    for (File f : files) {
      if (numRetained <= maximum) {
        return numRetained;
      }
      recursiveDelete(f);
      numRetained--;
    }
    return numRetained;
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
