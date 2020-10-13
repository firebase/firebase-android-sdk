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
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session;
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

  @NonNull private final AtomicInteger eventCounter = new AtomicInteger(0);

  // Storage for sessions that are still being written to
  @NonNull private final File openSessionsDirectory;

  // Storage for finalized reports
  @NonNull private final File priorityReportsDirectory;
  @NonNull private final File reportsDirectory;

  // Storage for NDK Reports
  @NonNull private final File nativeReportsDirectory;

  @NonNull private final SettingsDataProvider settingsDataProvider;

  public CrashlyticsReportPersistence(
      @NonNull File rootDirectory, @NonNull SettingsDataProvider settingsDataProvider) {
    final File workingDirectory = new File(rootDirectory, WORKING_DIRECTORY_NAME);
    openSessionsDirectory = new File(workingDirectory, OPEN_SESSIONS_DIRECTORY_NAME);
    priorityReportsDirectory = new File(workingDirectory, PRIORITY_REPORTS_DIRECTORY);
    reportsDirectory = new File(workingDirectory, REPORTS_DIRECTORY);
    nativeReportsDirectory = new File(workingDirectory, NATIVE_REPORTS_DIRECTORY);
    this.settingsDataProvider = settingsDataProvider;
  }

  public void persistReport(@NonNull CrashlyticsReport report) {
    final Session session = report.getSession();
    if (session == null) {
      Logger.getLogger().d("Could not get session for report");
      return;
    }

    final String sessionId = session.getIdentifier();
    try {
      final File sessionDirectory = prepareDirectory(getSessionDirectoryById(sessionId));
      final String json = TRANSFORM.reportToJson(report);
      writeTextFile(new File(sessionDirectory, REPORT_FILE_NAME), json);
    } catch (IOException e) {
      Logger.getLogger().d("Could not persist report for session " + sessionId, e);
    }
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
  public void persistEvent(
      @NonNull CrashlyticsReport.Session.Event event, @NonNull String sessionId) {
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
      @NonNull CrashlyticsReport.Session.Event event,
      @NonNull String sessionId,
      boolean isHighPriority) {
    int maxEventsToKeep =
        settingsDataProvider.getSettings().getSessionData().maxCustomExceptionEvents;
    final File sessionDirectory = getSessionDirectoryById(sessionId);
    final String json = TRANSFORM.eventToJson(event);
    final String fileName = generateEventFilename(eventCounter.getAndIncrement(), isHighPriority);
    try {
      writeTextFile(new File(sessionDirectory, fileName), json);
    } catch (IOException e) {
      Logger.getLogger().d("Could not persist event for session " + sessionId, e);
    }
    trimEvents(sessionDirectory, maxEventsToKeep);
  }

  public void persistUserIdForSession(@NonNull String userId, @NonNull String sessionId) {
    final File sessionDirectory = getSessionDirectoryById(sessionId);
    try {
      writeTextFile(new File(sessionDirectory, USER_FILE_NAME), userId);
    } catch (IOException e) {
      // Session directory is not guaranteed to exist
      Logger.getLogger().d("Could not persist user ID for session " + sessionId, e);
    }
  }

  public void deleteAllReports() {
    for (File reportFile : getAllFinalizedReportFiles()) {
      reportFile.delete();
    }
  }

  public void deleteFinalizedReport(String sessionId) {
    final FilenameFilter filter = (d, f) -> f.startsWith(sessionId);
    List<File> filteredReports =
        combineReportFiles(
            getFilesInDirectory(priorityReportsDirectory, filter),
            getFilesInDirectory(nativeReportsDirectory, filter),
            getFilesInDirectory(reportsDirectory, filter));
    for (File reportFile : filteredReports) {
      reportFile.delete();
    }
  }

  /**
   * Finalizes all open sessions except for the current session ID
   *
   * @param currentSessionId current session ID (to skip). If this is null, all open sessions will
   *     be finalized.
   * @param sessionEndTime
   */
  public void finalizeReports(@Nullable String currentSessionId, long sessionEndTime) {
    final List<File> sessionDirectories = capAndGetOpenSessions(currentSessionId);
    for (File sessionDirectory : sessionDirectories) {
      Logger.getLogger().d("Finalizing report for session " + sessionDirectory.getName());
      synthesizeReport(sessionDirectory, sessionEndTime);
      recursiveDelete(sessionDirectory);
    }

    capFinalizedReports();
  }

  public void finalizeSessionWithNativeEvent(
      @NonNull String previousSessionId, @NonNull CrashlyticsReport.FilesPayload ndkPayload) {
    final File reportFile = new File(getSessionDirectoryById(previousSessionId), REPORT_FILE_NAME);
    synthesizeNativeReportFile(reportFile, nativeReportsDirectory, ndkPayload, previousSessionId);
  }

  /**
   * @return finalized (no longer changing) Crashlytics Reports, sorted first from high to low
   *     priority, secondarily sorted from most recent to least
   */
  @NonNull
  public List<CrashlyticsReportWithSessionId> loadFinalizedReports() {
    final List<File> allReportFiles = getAllFinalizedReportFiles();
    final ArrayList<CrashlyticsReportWithSessionId> allReports = new ArrayList<>();
    allReports.ensureCapacity(allReportFiles.size());
    for (File reportFile : getAllFinalizedReportFiles()) {
      try {
        CrashlyticsReport jsonReport = TRANSFORM.reportFromJson(readTextFile(reportFile));
        allReports.add(CrashlyticsReportWithSessionId.create(jsonReport, reportFile.getName()));
      } catch (IOException e) {
        Logger.getLogger().d("Could not load report file " + reportFile + "; deleting", e);
        reportFile.delete();
      }
    }
    return allReports;
  }

  @NonNull
  private List<File> capAndGetOpenSessions(@Nullable String currentSessionId) {
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

  @NonNull
  private File getSessionDirectoryById(@NonNull String sessionId) {
    return new File(openSessionsDirectory, sessionId);
  }

  private void synthesizeReport(@NonNull File sessionDirectory, long sessionEndTime) {
    final List<File> eventFiles = getFilesInDirectory(sessionDirectory, EVENT_FILE_FILTER);

    // Only process the session if it has associated events
    if (eventFiles.isEmpty()) {
      Logger.getLogger().d("Session " + sessionDirectory.getName() + " has no events.");
      return;
    }

    Collections.sort(eventFiles);
    final List<Event> events = new ArrayList<>();
    boolean isHighPriorityReport = false;

    for (File eventFile : eventFiles) {
      try {
        events.add(TRANSFORM.eventFromJson(readTextFile(eventFile)));
        isHighPriorityReport = isHighPriorityReport || isHighPriorityEventFile(eventFile.getName());
      } catch (IOException e) {
        Logger.getLogger().d("Could not add event to report for " + eventFile, e);
      }
    }

    // b/168902195
    if (events.isEmpty()) {
      Logger.getLogger().d("Could not parse event files for session " + sessionDirectory.getName());
      return;
    }

    String userId = null;
    final File userIdFile = new File(sessionDirectory, USER_FILE_NAME);
    if (userIdFile.isFile()) {
      try {
        userId = readTextFile(userIdFile);
      } catch (IOException e) {
        Logger.getLogger().d("Could not read user ID file in " + sessionDirectory.getName(), e);
      }
    }

    final File reportFile = new File(sessionDirectory, REPORT_FILE_NAME);
    final File outputDirectory = isHighPriorityReport ? priorityReportsDirectory : reportsDirectory;
    synthesizeReportFile(
        reportFile, outputDirectory, events, sessionEndTime, isHighPriorityReport, userId);
  }

  private static void synthesizeNativeReportFile(
      @NonNull File reportFile,
      @NonNull File outputDirectory,
      @NonNull CrashlyticsReport.FilesPayload ndkPayload,
      @NonNull String previousSessionId) {
    try {
      final CrashlyticsReport report =
          TRANSFORM.reportFromJson(readTextFile(reportFile)).withNdkPayload(ndkPayload);

      writeTextFile(
          new File(prepareDirectory(outputDirectory), previousSessionId),
          TRANSFORM.reportToJson(report));
    } catch (IOException e) {
      Logger.getLogger().d("Could not synthesize final native report file for " + reportFile, e);
    }
  }

  private static void synthesizeReportFile(
      @NonNull File reportFile,
      @NonNull File outputDirectory,
      @NonNull List<Event> events,
      long sessionEndTime,
      boolean isCrashed,
      @Nullable String userId) {
    try {
      CrashlyticsReport report =
          TRANSFORM
              .reportFromJson(readTextFile(reportFile))
              .withSessionEndFields(sessionEndTime, isCrashed, userId)
              .withEvents(ImmutableList.from(events));

      final Session session = report.getSession();

      if (session == null) {
        // This shouldn't happen, but is a valid state for NDK-based reports
        return;
      }

      writeTextFile(
          new File(prepareDirectory(outputDirectory), session.getIdentifier()),
          TRANSFORM.reportToJson(report));
    } catch (IOException e) {
      Logger.getLogger().d("Could not synthesize final report file for " + reportFile, e);
    }
  }

  @NonNull
  private static List<File> sortAndCombineReportFiles(@NonNull List<File>... reports) {
    for (List<File> reportList : reports) {
      Collections.sort(reportList, LATEST_SESSION_ID_FIRST_COMPARATOR);
    }

    return combineReportFiles(reports);
  }

  @NonNull
  private static List<File> combineReportFiles(@NonNull List<File>... reports) {
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

  private static boolean isHighPriorityEventFile(@NonNull String fileName) {
    return fileName.startsWith(EVENT_FILE_NAME_PREFIX) && fileName.endsWith(PRIORITY_EVENT_SUFFIX);
  }

  private static boolean isNormalPriorityEventFile(@NonNull File dir, @NonNull String name) {
    return name.startsWith(EVENT_FILE_NAME_PREFIX) && !name.endsWith(PRIORITY_EVENT_SUFFIX);
  }

  @NonNull
  private static String generateEventFilename(int eventNumber, boolean isHighPriority) {
    final String paddedEventNumber = String.format(Locale.US, EVENT_COUNTER_FORMAT, eventNumber);
    final String prioritySuffix = isHighPriority ? PRIORITY_EVENT_SUFFIX : NORMAL_EVENT_SUFFIX;
    return EVENT_FILE_NAME_PREFIX + paddedEventNumber + prioritySuffix;
  }

  private static int trimEvents(@NonNull File sessionDirectory, int maximum) {
    final List<File> normalPriorityEventFiles =
        getFilesInDirectory(
            sessionDirectory, CrashlyticsReportPersistence::isNormalPriorityEventFile);
    Collections.sort(normalPriorityEventFiles, CrashlyticsReportPersistence::oldestEventFileFirst);
    return capFilesCount(normalPriorityEventFiles, maximum);
  }

  @NonNull
  private static String getEventNameWithoutPriority(@NonNull String eventFileName) {
    return eventFileName.substring(0, EVENT_NAME_LENGTH);
  }

  private static int oldestEventFileFirst(@NonNull File f1, @NonNull File f2) {
    final String name1 = getEventNameWithoutPriority(f1.getName());
    final String name2 = getEventNameWithoutPriority(f2.getName());
    return name1.compareTo(name2);
  }

  @NonNull
  private static List<File> getAllFilesInDirectory(@NonNull File directory) {
    return getFilesInDirectory(directory, (FileFilter) null);
  }

  @NonNull
  private static List<File> getFilesInDirectory(
      @NonNull File directory, @Nullable FilenameFilter filter) {
    if (!directory.isDirectory()) {
      return Collections.emptyList();
    }
    final File[] files = (filter == null) ? directory.listFiles() : directory.listFiles(filter);
    return (files != null) ? Arrays.asList(files) : Collections.emptyList();
  }

  @NonNull
  private static List<File> getFilesInDirectory(
      @NonNull File directory, @Nullable FileFilter filter) {
    if (!directory.isDirectory()) {
      return Collections.emptyList();
    }
    final File[] files = (filter == null) ? directory.listFiles() : directory.listFiles(filter);
    return (files != null) ? Arrays.asList(files) : Collections.emptyList();
  }

  @NonNull
  private static File prepareDirectory(@NonNull File directory) throws IOException {
    if (!makeDirectory(directory)) {
      throw new IOException("Could not create directory " + directory);
    }
    return directory;
  }

  private static boolean makeDirectory(@NonNull File directory) {
    return directory.exists() || directory.mkdirs();
  }

  private static void writeTextFile(File file, String text) throws IOException {
    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), UTF_8)) {
      writer.write(text);
    }
  }

  @NonNull
  private static String readTextFile(@NonNull File file) throws IOException {
    final byte[] readBuffer = new byte[8192];
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (FileInputStream fileInput = new FileInputStream(file)) {
      int read;
      while ((read = fileInput.read(readBuffer)) > 0) {
        bos.write(readBuffer, 0, read);
      }
      return new String(bos.toByteArray(), UTF_8);
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

  private static void recursiveDelete(@Nullable File file) {
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
