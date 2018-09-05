// Copyright 2018 Google LLC
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

package com.google.firebase.gradle.plugins.license

import org.jsoup.Jsoup
import org.jsoup.examples.HtmlToPlainText

/**
 * Parse licenses from remote urls*/
interface RemoteLicenseFetcher extends Serializable {
  static final TEXT_FORMATTER = new HtmlToPlainText()

  URI getServiceUri()

  String get()

  static final class AndroidSdkTermsFetcher implements RemoteLicenseFetcher {
    private URI ANDROID_SDK_TERMS_URI = URI.create("https://developer.android.com/studio/terms.html")

    @Override
    URI getServiceUri() {
      ANDROID_SDK_TERMS_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(ANDROID_SDK_TERMS_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('#body-content > div.jd-descr > div')[0])
    }
  }

  static final class Apache2LicenseFetcher implements RemoteLicenseFetcher {
    private URI APACHE_2_LICENSE_URI = URI.create("http://www.apache.org/licenses/LICENSE-2.0.txt")

    @Override
    URI getServiceUri() {
      APACHE_2_LICENSE_URI
    }

    @Override
    String get() {
      APACHE_2_LICENSE_URI.toURL().getText()
    }
  }

  static final class BSDLicenseFetcher implements RemoteLicenseFetcher {
    private URI BSD_LICENSE_URI = URI.create("http://www.opensource.org/licenses/bsd-license.php")

    @Override
    URI getServiceUri() {
      BSD_LICENSE_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(BSD_LICENSE_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('#content-wrapper')[0])
    }
  }

  static final class AnotherApache2LicenseFetcher implements RemoteLicenseFetcher {
    private URI APACHE_2_LICENSE_URI = URI.create("https://opensource.org/licenses/Apache-2.0")

    @Override
    URI getServiceUri() {
      APACHE_2_LICENSE_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(APACHE_2_LICENSE_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('#content-wrapper'))
    }
  }

  static final class CreativeCommonsLicenseFetcher implements RemoteLicenseFetcher {
    private URI CREATIVE_COMMONS_LICENSE_URI = URI.create("http://creativecommons.org/publicdomain/zero/1.0/")

    @Override
    URI getServiceUri() {
      CREATIVE_COMMONS_LICENSE_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(CREATIVE_COMMONS_LICENSE_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('#deed'))
    }
  }

  static final class MITLicenseFetcher implements RemoteLicenseFetcher {
    private URI MIT_LICENSE_URI = URI.create("http://www.opensource.org/licenses/mit-license.php")

    @Override
    URI getServiceUri() {
      MIT_LICENSE_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(MIT_LICENSE_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('#content-wrapper'))
    }
  }

  static final class AnotherMITLicenseFetcher implements RemoteLicenseFetcher {
    private URI MIT_LICENSE_URI = URI.create("http://opensource.org/licenses/MIT")

    @Override
    URI getServiceUri() {
      MIT_LICENSE_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(MIT_LICENSE_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('#content-wrapper'))
    }
  }

  static final class GnuClasspathLicenseFetcher implements RemoteLicenseFetcher {
    private URI GNU_CLASSPATH_LICENSE_URI = URI.create("http://www.gnu.org/software/classpath/license.html")

    @Override
    URI getServiceUri() {
      GNU_CLASSPATH_LICENSE_URI
    }

    @Override
    String get() {
      def doc = Jsoup.connect(GNU_CLASSPATH_LICENSE_URI.toString()).get()

      TEXT_FORMATTER.getPlainText(doc.select('body > table > tbody > tr:nth-child(2) > td:nth-child(2) > table > tbody > tr:nth-child(3) > td > en > blockquote'))
    }
  }
}