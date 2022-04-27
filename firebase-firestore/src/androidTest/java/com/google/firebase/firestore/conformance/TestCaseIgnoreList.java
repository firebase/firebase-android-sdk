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

package com.google.firebase.firestore.conformance;

import com.google.firebase.firestore.conformance.model.TestCase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Filters testcases by name.
 *
 * <p>The ignore list contains regular expressions matching testcase names. There should be one
 * expression per line. Comments are supported at the beginning or end of lines using the {@code #}
 * symbol. Empty lines are ignored.
 */
public final class TestCaseIgnoreList implements Predicate<TestCase> {
  // Note: This code is copied from Google3.

  private final Pattern pattern;

  public TestCaseIgnoreList(InputStream inputStream) throws IOException {
    this.pattern = parseIgnoreList(inputStream);
  }

  @Override
  public boolean test(TestCase testCase) {
    return !pattern.matcher(testCase.getName()).find();
  }

  private static Pattern parseIgnoreList(InputStream inputStream) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      // Create a single regular expression that contains each expression as a non-capturing group.
      // Remove comments, trim leading/trailing whitespace, and remove empty lines before joining.
      String blacklist =
          reader
              .lines()
              .map(
                  line -> {
                    int commentPosition = line.indexOf('#');
                    if (commentPosition == -1) {
                      return line.trim();
                    } else {
                      return line.substring(0, commentPosition).trim();
                    }
                  })
              .filter(line -> !line.isEmpty())
              .map(line -> "(?:" + line + ")")
              .collect(Collectors.joining("|"));

      return Pattern.compile(blacklist);
    } catch (UncheckedIOException x) {
      throw x.getCause();
    }
  }
}
