// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License")
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

package com.google.firebase.gradle.plugins.license

import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.examples.HtmlToPlainText
import org.jsoup.nodes.Document

/**
 * Parse licenses from remote urls*/
abstract class RemoteLicenseFetcher implements Serializable {
  private static final HtmlToPlainText TEXT_FORMATTER = new HtmlToPlainText()

  private final String remoteUrl

  protected RemoteLicenseFetcher(String remoteUrl) {
    this.remoteUrl = remoteUrl
  }

  public final String getRemoteUrl() {
    return remoteUrl
  }

  /**
   * Downloads and returns the text of the license.
   *
   * <p>This method will try at most three times to download the license. Failures will be silently
   * ignored if the license can be successfully downloaded on the second or third attempt. This
   * method performs a simple, linear backoff in the case of failures to improve chances of
   * successful connections.
   */
  public final String getText() {
    IOException storedEx = null

    for (int i = 0; i < 3; ++i) {
      try {
        if (i > 0) {
          Thread.sleep(i * 1000)
        }

        return getTextAttempt()
      } catch (IOException ex) {
        if (storedEx == null) {
          storedEx = ex
        } else {
          storedEx.addSuppressed(ex)
        }
      }
    }

    throw storedEx
  }

  /** Attempts to download and extract the license exactly once. */
  abstract String getTextAttempt()

  static final class AndroidSdkTermsFetcher extends RemoteLicenseFetcher {

    AndroidSdkTermsFetcher() {
      super("https://developer.android.com/studio/terms.html")
    }

    @Override
    String getTextAttempt() {
      // TODO(vkryachko, allisonbm92): Fix this silent failure.
      // This evaluates to an empty string. The HTML for this page must have changed since this
      // filter was original written. Interestingly, this is a hard-failure if run from Java.
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("#body-content > div.jd-descr > div")[0])
    }
  }

  static final class Apache2LicenseFetcher extends RemoteLicenseFetcher {

    Apache2LicenseFetcher() {
      super("http://www.apache.org/licenses/LICENSE-2.0.txt")
    }

    @Override
    String getTextAttempt() {
      return getRemoteUrl().toURL().getText()
    }
  }

  static final class AnotherApache2LicenseFetcher extends RemoteLicenseFetcher {

    AnotherApache2LicenseFetcher() {
      super("https://opensource.org/licenses/Apache-2.0")
    }

    @Override
    String getTextAttempt() {
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("#content-wrapper").get(0))
    }
  }

  static final class YetAnotherApache2LicenseFetcher extends RemoteLicenseFetcher {

    YetAnotherApache2LicenseFetcher() {
      super("http://www.apache.org/licenses/LICENSE-2.0")
    }

    @Override
    String getTextAttempt() {
      return getRemoteUrl().toURL().getText()
    }
  }

  static final class BSDLicenseFetcher extends RemoteLicenseFetcher {

    BSDLicenseFetcher() {
      super("http://www.opensource.org/licenses/bsd-license.php")
    }

    @Override
    String getTextAttempt() {
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("#content-wrapper").get(0))
    }
  }

  static final class CreativeCommonsLicenseFetcher extends RemoteLicenseFetcher {

    CreativeCommonsLicenseFetcher() {
      super("http://creativecommons.org/publicdomain/zero/1.0/")
    }

    @Override
    String getTextAttempt() {
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("#deed").get(0))
    }
  }

  static final class MITLicenseFetcher extends RemoteLicenseFetcher {

    MITLicenseFetcher() {
      super("http://www.opensource.org/licenses/mit-license.php")
    }

    @Override
    String getTextAttempt() {
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("#content-wrapper").get(0))
    }
  }

  static final class AnotherMITLicenseFetcher extends RemoteLicenseFetcher {

    AnotherMITLicenseFetcher() {
      super("http://opensource.org/licenses/MIT")
    }

    @Override
    String getTextAttempt() {
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("#content-wrapper").get(0))
    }
  }

  static final class GnuClasspathLicenseFetcher extends RemoteLicenseFetcher {

    // TODO(allisonbm92, vkryachko): Fetch the actual license. This only fetches the extension.
    GnuClasspathLicenseFetcher() {
      super("http://www.gnu.org/software/classpath/license.html")
    }

    @Override
    String getTextAttempt() {
      def doc = Jsoup.connect(getRemoteUrl()).get()
      return TEXT_FORMATTER.getPlainText(doc.select("body > table > tbody > tr:nth-child(2) > td:nth-child(2) > table > tbody > tr:nth-child(3) > td > en > blockquote").get(0))
    }
  }
}
