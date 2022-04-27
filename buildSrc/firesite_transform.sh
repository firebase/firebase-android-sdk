# Copyright 2021 Google LLC
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

set -x
#!/bin/sh -x
grep -rl 'http://' | xargs sed -i 's|http://|//|g'
grep -rl 'https://' | xargs sed -i 's|https://|//|g'
grep -rl 'reference/com/google/' | xargs sed -i 's|reference/com/google/|/docs/reference/android/com/google/|g'
grep -rl '//developers.google.com/android//docs/reference/android/' | xargs sed -i 's|//developers.google.com/android//docs/reference/android/|//developers.google.com/android/reference/|g'
grep -rl '//developers.google.com/android/reference/com/google/firebase/' | xargs sed -i 's|//developers.google.com/android/reference/com/google/firebase/|/docs/reference/android/com/google/firebase/|g'
grep -rl 'href="reference' | xargs sed -i 's|href="reference|href="/docs/reference/android/|g'
grep -rl '/docs/reference/android///developers.google.com/android/reference/' | xargs sed -i 's|/docs/reference/android///developers.google.com/android/reference/|/docs/reference/android/|g'
grep -rl '/docs/reference/android///developer.android.com/reference/' | xargs sed -i 's|/docs/reference/android///developer.android.com/reference/|//developer.android.com/reference/|g'
grep -rl '/docs/reference/android//docs/reference/android/' | xargs sed -i 's|/docs/reference/android//docs/reference/android/|/docs/reference/android/|g'
find . -name '*.html' | xargs sed -i 's/[ \t]*$//' "$@"
# TODO(b/37810114): Remove this hack once b/64612004 is fixed and propagated to our javadoc.
find . -name '*.html' | xargs perl -0777 -p -i -e 's|.+<div class="jd-tagdata">\n(.*\n){1,5}?(?:.+<tr>\n.+\n.+<td><!-- no parameter comment --></td>\n.+</tr>\n)+?(.+\n){1,5}?.*</div>||gm'
find . -name '_toc.yaml' | xargs sed -i 's|"com\.google\.firebase|"firebase|g'
find . -name '_toc.yaml' | xargs sed -i 's|"com\.goo  gle\.android\.gms\.|"|g'
find . -name "_toc.yaml" | xargs sed -i 's|path: reference/|path: /docs/reference/android/|g'

#Delete blank lines
find . -name "_toc.yaml" | xargs sed -i 's/[ \t]*$//'

#Delete newline after section:
find . -name "_toc.yaml" | xargs sed -i -z 's/section:\n\n+/section:\n/g'

#Delete whitestapes after toc sections
find . -name "_toc.yaml" | xargs sed -i 's|section: |section:|g'

#Delete blank lines at the top of a file
find . -name "_toc.yaml" | xargs sed -i '/./,$!d'

#Delete all kotlin javadoc
rm -rf "ktx/"
