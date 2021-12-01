// Copyright 2021 Google LLC
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

package com.google.firebase.gradle.bomgenerator.tagging;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;

public class GitClient {

  private final String mRelease;
  private final String rcCommit;
  private final ShellExecutor executor;
  private final Consumer<String> logger;

  private final List<String> newTags;
  private final Consumer<List<String>> handler; // handles shell outputs of git commands

  public GitClient(
      String mRelease, String rcCommit, ShellExecutor executor, Consumer<String> logger) {
    this.mRelease = mRelease;
    this.rcCommit = rcCommit;
    this.executor = executor;
    this.logger = logger;

    this.newTags = new ArrayList<>();
    this.handler = this.deriveGitCommandOutputHandlerFromLogger(logger);
    this.configureSshCommand();
  }

  public void tagReleaseVersion() {
    this.tag(mRelease);
  }

  public void tagBomVersion(String version) {
    String tag = "bom@" + version;
    this.tag(tag);
  }

  public void tagProductVersion(String product, String version) {
    String tag = product + "@" + version;
    this.tag(tag);
  }

  public void pushCreatedTags() {
    if (!this.onProw() || newTags.isEmpty()) {
      return;
    }

    StringJoiner joiner = new StringJoiner(" ");
    newTags.forEach(joiner::add);
    String tags = joiner.toString();

    logger.accept("Tags to be pushed: " + tags);

    logger.accept("Pushing tags to FirebasePrivate/firebase-android-sdk ...");
    String command = String.format("git push origin %s", tags);
    executor.execute(command, handler);
  }

  private boolean onProw() {
    return System.getenv().containsKey("FIREBASE_CI");
  }

  private Consumer<List<String>> deriveGitCommandOutputHandlerFromLogger(Consumer<String> logger) {
    return outputs -> outputs.stream().map(output -> "[git] " + output).forEach(logger);
  }

  private void tag(String tag) {
    logger.accept("Creating tag: " + tag);
    newTags.add(tag);
    String command = String.format("git tag %s %s", tag, rcCommit);
    executor.execute(command, handler);
  }

  private void configureSshCommand() {
    if (!this.onProw()) {
      return;
    }
    // TODO(yifany):
    //   - Change to use environment variables according to the Git doc:
    //       https://git-scm.com/docs/git-config#Documentation/git-config.txt-coresshCommand
    //   - Call of `git config core.sshCommand` in prow/config.yaml will become redundant as well
    String ssh =
        "ssh -i /etc/github-ssh-key/github-ssh"
            + " -o IdentitiesOnly=yes"
            + " -o UserKnownHostsFile=/dev/null"
            + " -o StrictHostKeyChecking=no";
    String command = String.format("git config core.sshCommand \"%s\"", ssh);
    executor.execute(command, handler);
  }
}
