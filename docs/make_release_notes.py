#!/usr/bin/env python3

# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Converts GitHub flavored markdown changelogs to release notes.
"""

import argparse
import configparser
import json
import re
import os
import string
from dataclasses import dataclass, field

# TODO(b/279610345) - Replace this with a Gradle task
@dataclass
class Changelog:
    path: str
    target_path: str
    alt_name: str
    version_name: str
    has_ktx: bool = True
    ktx_placeholder: str = None
    version: str = field(init=False)

    def __post_init__(self):
        self.version = self._get_version()

    def get_header(self):
        version_str = f'{{: #{self.version_name}_v{self.version.replace(".", "-")}}}'
        return f'### {self.alt_name} version {self.version} {version_str}\n'

    def get_ktx_header(self):
        if not self.has_ktx:
            return ''
        version_str = f'{{: #{self.version_name}-ktx_v{self.version.replace(".", "-")}}}'
        return f'#### {self.alt_name} Kotlin extensions version {self.version} {version_str}\n'

    def _get_version(self):
        properties = os.path.join(os.path.dirname(self.path),
                                  'gradle.properties')
        if not os.path.exists(properties):
            return PLACEHOLDER_VERSION

        with open(properties, 'r') as fd:
            for line in fd:
                if line.startswith('version='):
                    return line.removeprefix('version=').strip()
        return PLACEHOLDER_VERSION


REPO = 'firebase/firebase-android-sdk'
CHANGE_TYPE_MAPPING = {'added': 'feature'}
PLACEHOLDER_VERSION = 'xx.x.x'
PRODUCTS = {
    'firebase-abt':
        Changelog(path='firebase-abt/CHANGELOG.md',
                  target_path='firebase-abt',
                  has_ktx=False,
                  alt_name='{{ab_testing}}',
                  version_name='ab-testing'),
    'firebase-appdistribution':
        Changelog(path='firebase-appdistribution/CHANGELOG.md',
                  target_path='firebase-appdistribution',
                  has_ktx=False,
                  alt_name='{{appdistro}}',
                  version_name='app-distro'),
    'firebase-appdistribution-api':
        Changelog(path='firebase-appdistribution-api/CHANGELOG.md',
                  target_path='firebase-appdistribution-api',
                  alt_name='{{appdistro}} API',
                  ktx_placeholder='firebase-appdistribution-api',
                  version_name='app-distro-api'),
    'firebase-config':
        Changelog(path='firebase-config/CHANGELOG.md',
                  target_path='firebase-config',
                  alt_name='{{remote_config}}',
                  ktx_placeholder='firebase-config',
                  version_name='remote-config'),
    'firebase-crashlytics':
        Changelog(path='firebase-crashlytics/CHANGELOG.md',
                  target_path='firebase-crashlytics',
                  alt_name='{{crashlytics}}',
                  ktx_placeholder='firebase-crashlytics',
                  version_name='crashlytics'),
    'firebase-crashlytics-ndk':
        Changelog(path='firebase-crashlytics-ndk/CHANGELOG.md',
                  target_path='firebase-crashlytics-ndk',
                  has_ktx=False,
                  alt_name='{{crashlytics}} NDK',
                  version_name='crashlytics-ndk'),
    'firebase-database':
        Changelog(path='firebase-database/CHANGELOG.md',
                  target_path='firebase-database',
                  alt_name='{{database}}',
                  ktx_placeholder='firebase-database',
                  version_name='realtime-database'),
    'firebase-dynamic-links':
        Changelog(path='firebase-dynamic-links/CHANGELOG.md',
                  target_path='firebase-dynamic-links',
                  alt_name='{{ddls}}',
                  ktx_placeholder='firebase-dynamic-links',
                  version_name='dynamic-links'),
    'firebase-firestore':
        Changelog(path='firebase-firestore/CHANGELOG.md',
                  target_path='firebase-firestore',
                  alt_name='{{firestore}}',
                  ktx_placeholder='firebase-firestore',
                  version_name='firestore'),
    'firebase-functions':
        Changelog(path='firebase-functions/CHANGELOG.md',
                  target_path='firebase-functions',
                  alt_name='{{functions_client}}',
                  ktx_placeholder='firebase-functions',
                  version_name='functions-client'),
    'firebase-dynamic-module-support':
        Changelog(
            path=
            'firebase-components/firebase-dynamic-module-support/CHANGELOG.md',
            target_path='firebase-dynamic-module-support',
            has_ktx=False,
            alt_name='Dynamic feature modules support',
            version_name='dynamic-feature-modules-support'),
    'firebase-inappmessaging':
        Changelog(path='firebase-inappmessaging/CHANGELOG.md',
                  target_path='firebase-inappmessaging',
                  alt_name='{{inappmessaging}}',
                  ktx_placeholder='firebase-inappmessaging',
                  version_name='inappmessaging'),
    'firebase-inappmessaging-display':
        Changelog(path='firebase-inappmessaging-display/CHANGELOG.md',
                  target_path='firebase-inappmessaging-display',
                  alt_name='{{inappmessaging}} Display',
                  ktx_placeholder='firebase-inappmessaging-display',
                  version_name='inappmessaging-display'),
    'firebase-installations':
        Changelog(path='firebase-installations/CHANGELOG.md',
                  target_path='firebase-installations',
                  alt_name='{{firebase_installations}}',
                  ktx_placeholder='firebase-installations',
                  version_name='installations'),
    'firebase-messaging':
        Changelog(path='firebase-messaging/CHANGELOG.md',
                  target_path='firebase-messaging',
                  alt_name='{{messaging_longer}}',
                  ktx_placeholder='firebase-messaging',
                  version_name='messaging'),
    'firebase-messaging-directboot':
        Changelog(path='firebase-messaging-directboot/CHANGELOG.md',
                  target_path='firebase-messaging-directboot',
                  has_ktx=False,
                  alt_name='Cloud Messaging Direct Boot',
                  version_name='messaging-directboot'),
    'firebase-ml-modeldownloader':
        Changelog(path='firebase-ml-modeldownloader/CHANGELOG.md',
                  target_path='firebase-ml-modeldownloader',
                  alt_name='{{firebase_ml}}',
                  ktx_placeholder='firebase-ml-modeldownloader',
                  version_name='firebaseml-modeldownloader'),
    'firebase-perf':
        Changelog(path='firebase-perf/CHANGELOG.md',
                  target_path='firebase-perf',
                  alt_name='{{perfmon}}',
                  ktx_placeholder='firebase-performance',
                  version_name='performance'),
    'firebase-storage':
        Changelog(path='firebase-storage/CHANGELOG.md',
                  target_path='firebase-storage-api',
                  alt_name='{{firebase_storage_full}}',
                  ktx_placeholder='firebase-storage',
                  version_name='storage'),
    'appcheck:firebase-appcheck':
        Changelog(path='appcheck/firebase-appcheck/CHANGELOG.md',
                  target_path='firebase-appcheck',
                  alt_name='{{app_check}}',
                  ktx_placeholder='firebase-appcheck',
                  version_name='appcheck'),
    'appcheck:firebase-appcheck-debug':
        Changelog(path='appcheck/firebase-appcheck-debug/CHANGELOG.md',
                  target_path='firebase-appcheck-debug',
                  has_ktx=False,
                  alt_name='{{app_check}} Debug',
                  version_name='appcheck-debug'),
    'appcheck:firebase-appcheck-debug-testing':
        Changelog(path='appcheck/firebase-appcheck-debug-testing/CHANGELOG.md',
                  target_path='firebase-appcheck-debug-testing',
                  has_ktx=False,
                  alt_name='{{app_check}} Debug Testing',
                  version_name='appcheck-debug-testing'),
    'appcheck:firebase-appcheck-playintegrity':
        Changelog(path='appcheck/firebase-appcheck-playintegrity/CHANGELOG.md',
                  target_path='firebase-appcheck-playintegrity',
                  has_ktx=False,
                  alt_name='{{app_check}} Play integrity',
                  version_name='appcheck-playintegrity')
}
KTX_PLACEHOLDER_TEXT = """
The Kotlin extensions library transitively includes the updated
`PLACEHOLDER_NAME` library. The Kotlin extensions library has no additional
updates.
"""


def read_release_cfg(release_cfg_path):
    with open(release_cfg_path) as fd:
        config = json.load(fd)
    return config


def main():
    parser = argparse.ArgumentParser(description='Create release notes.')
    parser.add_argument('--releasecfg',
                        default='release.json',
                        required=False,
                        help='Path to the release.cfg file to use')
    parser.add_argument('--products',
                        required=False,
                        help='Comma separated list of products to process')
    parser.add_argument('--generated_name',
                        required=False,
                        default=None,
                        help='Name for generated files, without extension.')
    args = parser.parse_args()

    release_cfg = None
    if os.path.exists(args.releasecfg):
        release_cfg = read_release_cfg(args.releasecfg)

    if args.products:
        products = args.products.split(',')
    else:
        products = list(release_cfg['libraries'])

    if args.generated_name:
        generated_name = args.generated_name
    else:
        generated_name = release_cfg['name'].lower().strip()

    for product in products:
        if product.startswith(':'):
            product = product.removeprefix(':')
        if not product in PRODUCTS:
            print(f'Ignored: {product}')
            continue

        changelog = PRODUCTS[product]
        renderer = Renderer(changelog)
        translator = Translator(renderer)
        path = f'build/changelog/android/client/{changelog.target_path}/_releases'
        os.makedirs(path, exist_ok=True)
        with open(f'{path}/{generated_name}.md', 'w') as fd:
            fd.write(
                translator.translate(
                    read_changelog_section(changelog, 'Unreleased')))


class Renderer(object):

    def __init__(self, changelog):
        self.changelog = changelog

    def heading(self, heading):
        return heading

    def bullet(self, spacing):
        """Renders a bullet in a list.

    All bulleted lists in devsite are '*' style.
    """
        return f'{spacing}* '

    def change_type(self, tag):
        """Renders a change type tag as the appropriate double-braced macro.

    That is "[fixed]" is rendered as "{{fixed}}".
    """
        tag = CHANGE_TYPE_MAPPING.get(tag, tag)
        return '{{%s}}' % tag

    def url(self, url):
        m = re.match(r'^(?:https:)?(//github.com/(.*)/issues/(\d+))$', url)
        if m:
            link = m.group(1)
            repo = m.group(2)
            issue = m.group(3)

            if repo == REPO:
                text = f'#{issue}'
            else:
                text = f'{repo}#{issue}'

            return f'[{text}]({link})'

        return url

    def local_issue_link(self, issues):
        """Renders a local issue link as a proper markdown URL.

    Transforms (#1234, #1235) into
    ([#1234](//github.com/firebase/firebase-android-sdk/issues/1234),
    [#1235](//github.com/firebase/firebase-android-sdk/issues/1235)).
    """
        issue_link_list = []
        issue_list = issues.split(', ')
        translate = str.maketrans('', '', string.punctuation)
        for issue in issue_list:
            issue = issue.translate(translate)
            link = f'//github.com/{REPO}/issues/{issue}'
            issue_link_list.append(f'[#{issue}]({link})')
        return '(' + ', '.join(issue_link_list) + ')'

    def text(self, text):
        """Passes through any other text."""
        return text


class Translator(object):

    def __init__(self, renderer):
        self.renderer = renderer

    def translate(self, text):
        result = ''
        while text:
            for key in self.rules:
                rule = getattr(self, key)
                m = rule.match(text)
                if not m:
                    continue

                callback = getattr(self, 'parse_' + key)
                callback_result = callback(m)
                result += callback_result

                text = text[len(m.group(0)):]
                break

        return result

    heading = re.compile(r'^#{1,6} .*')

    def parse_heading(self, m):
        return self.renderer.heading(m.group(0))

    bullet = re.compile(r'^(\s*)[*+-] ')

    def parse_bullet(self, m):
        return self.renderer.bullet(m.group(1))

    change_type = re.compile(
        r'\['  # opening square bracket
        r'(\w+)'  # tag word (like "feature" or "changed")
        r'\]'  # closing square bracket
        r'(?!\()'  # not followed by opening paren (that would be a link)
    )

    def parse_change_type(self, m):
        return self.renderer.change_type(m.group(1))

    url = re.compile(r'^(https?://[^\s<]+[^<.,:;"\')\]\s])')

    def parse_url(self, m):
        return self.renderer.url(m.group(1))

    local_issue_link = re.compile(
        r'\('  # opening paren
        r'(#(\d+)(, )?)+'  # list of hash and issue number, comma-delimited
        r'\)'  # closing paren
    )

    def parse_local_issue_link(self, m):
        return self.renderer.local_issue_link(m.group(0))

    text = re.compile(r'^[\s\S]+?(?=[(\[\n]|https?://|$)')

    def parse_text(self, m):
        return self.renderer.text(m.group(0))

    rules = [
        'heading', 'bullet', 'change_type', 'url', 'local_issue_link', 'text'
    ]


def read_changelog_section(changelog, single_version=None):
    """Reads a single section of the changelog from the given filename.

  If single_version is None, reads the first section with a number in its
  heading. Otherwise, reads the first section with single_version in its
  heading.

  Args:
    - single_version: specifies a string to look for in headings.

  Returns:
    A string containing the heading and contents of the heading.
  """
    with open(changelog.path, 'r') as fd:
        # Discard all lines until we see a heading that either has the version the
        # user asked for or any version.
        if single_version:
            initial_heading = re.compile(r'^#{1,6} .*%s' %
                                         re.escape(single_version))
        else:
            initial_heading = re.compile(r'^#{1,6} ([^\d]*)\d')

        heading = re.compile(r'^#{1,6} ')

        initial = True
        result = []
        for line in fd:
            if initial:
                if initial_heading.match(line):
                    initial = False
                    result.append(f'{changelog.get_header()}\n')

            else:
                if heading.match(line):
                    break

                result.append(line)

        # Prune extra newlines
        while result and result[-1] == '\n':
            result.pop()

        # Append ktx section
        if changelog.has_ktx:
            result.append('\n')
            result.append(changelog.get_ktx_header())
            result.append(
                KTX_PLACEHOLDER_TEXT.replace('PLACEHOLDER_NAME',
                                             changelog.ktx_placeholder))

        return ''.join(result)


if __name__ == '__main__':
    main()
