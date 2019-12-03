// Copyright 2019 Google LLC
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

package com.google.firebase.gradle.plugins.publish

import groovy.text.SimpleTemplateEngine


class JavaLibraryProject implements HasBuild {
    static final String BUILD_TEMPLATE = '''
        plugins {
            id 'java-library'
        }
        group = '${group}'
        version = '${version}'

        repositories {
            google()
            jcenter()
	    }
        dependencies {
            <%externalDependencies.each { c ->
              c.value.each { d ->
                println "$c.key '$d'"
              }
            } %>
        }
        '''
    String name
    String group = 'com.example'
    String version = 'undefined'
    List<String> classNames = []

    Map<String, List<String>> externalDependencies = [:]

    @Override
    String generateBuildFile() {
        def text = new SimpleTemplateEngine().createTemplate(BUILD_TEMPLATE).make([
                name: name,
                group: group,
                version: version,
                externalDependencies: externalDependencies,
        ])

        return text
    }
}