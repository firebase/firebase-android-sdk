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

package com.google.firebase.gradle

import groovy.json.JsonSlurper
import groovy.transform.Memoized
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class LicenseResolverPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    final String buildConfig = """
        buildscript {
            repositories {
                google()
                jcenter()
            }
        }

        plugins {
            id 'com.android.library'
            id 'LicenseResolverPlugin'
        }
        
        android.compileSdkVersion = 26

        repositories {
            jcenter()
            google()
        }
        dependencies {
            implementation 'com.squareup.picasso:picasso:2.71828'
            implementation 'com.squareup.okhttp:okhttp:2.7.5'
        }

        thirdPartyLicenses {
            add 'customLib1', "file:///${new File("src/test/fixtures/license.txt").absolutePath}"
            add 'customLib2', "http://www.apache.org/licenses/LICENSE-2.0.txt"
        }
        """

    def MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
    """

    def setup() {
        buildFile  = testProjectDir.newFile('build.gradle')
        testProjectDir.newFolder('src', 'main', 'java', 'com', 'example')
        testProjectDir.newFile('src/main/java/com/example/Foo.java') << "package com.example; class Foo {}"
        testProjectDir.newFile('src/main/AndroidManifest.xml') << MANIFEST
        buildFile << buildConfig
    }

    def "Generating licenses"() {
        when: "the generateLicenses task is invoked"
        def result = idempotentBuild("generateLicenses")

        def json = getLicenseJson()
        def txt = getLicenseText()
        def picassoIndex = new JsonSlurper().parseText(json).Picasso
        def customLibIndex1 = new JsonSlurper().parseText(json).customLib1
        def customLibIndex2 = new JsonSlurper().parseText(json).customLib2

        then: "task succeeds"
        result.task(":generateLicenses").outcome == TaskOutcome.SUCCESS

        then: "output contains license txt files"
        txt != null
        txt != ""

        and: "output contains license json files"
        json != null
        json != ""

        and: "license txt file contains dependency name"
        txt.contains("OkHttp")

        and: "license txt file contains license"
        txt.contains("Apache License")

        and: "license index leads us directly to the license"
        txt
            .substring(picassoIndex.start, picassoIndex.start + picassoIndex.length)
            .trim().startsWith("Apache License")

        txt
            .substring(picassoIndex.start, picassoIndex.start + picassoIndex.length)
            .trim().endsWith("limitations under the License.")

        and: "custom license declarations are present in the index"
        txt
            .substring(customLibIndex1.start, customLibIndex1.start + customLibIndex1.length)
            .trim().startsWith("Test license")

        txt
            .substring(customLibIndex1.start, customLibIndex1.start + customLibIndex1.length)
            .trim().endsWith("Test license")

        and: "explicit license declarations are present in the index"
        txt
            .substring(customLibIndex2.start, customLibIndex2.start + customLibIndex2.length)
            .trim().startsWith("Apache License")

        txt
            .substring(customLibIndex1.start, customLibIndex2.start + customLibIndex2.length)
            .trim().endsWith("limitations under the License.")
    }

    def "License tasks cache downloaded licenses"() {
        when:
        def result = idempotentBuild("generateLicenses")

        then: "Count the number of times the license was downloaded"
        result.output.count(
                'Downloading license from http://www.apache.org/licenses/LICENSE-2.0.txt') == 1

    }

    def "License tasks throw a useful exception when license is not parsable"() {
        given:
        buildFile.write """
            plugins {
                id 'com.android.library'
                id 'LicenseResolverPlugin'
            }
            android.compileSdkVersion = 26

            thirdPartyLicenses {
                add 'customLib', "http://www.unparsablelicense.com"
            }
        """

        when:
        build("generateLicenses")

        then:
        Exception e = thrown(Exception)
        e.getMessage().contains("Did you forget to add a custom RemoteLicenseFetcher?")
    }

    def "License tasks throw useful exception if file URI nor found"() {
        given:
        buildFile.write """
            plugins {
                id 'com.android.library'
                id 'LicenseResolverPlugin'
            }
            android.compileSdkVersion = 26

            thirdPartyLicenses {
                add 'customLib', "file:///${new File("non_existent_path.txt").absolutePath}"
            }
        """

        when:
        build("generateLicenses")

        then:
        Exception e = thrown(Exception)
        e.getMessage().contains("License file not found")
    }

    private String getLicenseText(module) {
        new File("${testProjectDir.root}/build/generated/third_party_licenses/",
                'third_party_licenses.txt').text
    }

    private String getLicenseJson(module) {
        new File("${testProjectDir.root}/build/generated/third_party_licenses/",
                'third_party_licenses.json').text
    }

    private BuildResult build(taskName) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(taskName)
                .withPluginClasspath()
                .build()
    }

    @Memoized
    private BuildResult idempotentBuild(taskName) {
        build(taskName)
    }
}
