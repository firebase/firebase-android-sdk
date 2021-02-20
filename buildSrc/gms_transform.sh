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
grep -rl '/docs/reference/android///developers.google.com/' | xargs sed -i 's|/docs/reference/android///developers.google.com||g'
grep -rl '/docs/reference/android/reference' | xargs sed -i 's|/docs/reference/android/reference|/android/reference|g'
grep -rl '//developers.google.com/android/reference/com/google/firebase/' | xargs sed -i 's|//developers.google.com/android/reference/com/google/firebase/|/android/reference/com/google/firebase/|g'
grep -rl '/docs/reference/android///developer.android.com/reference/' | xargs sed -i 's|/docs/reference/android///developer.android.com/reference/|//developer.android.com/reference/|g'
find . -name '*.html' | xargs sed -i 's/[ \t]*$//' "$@"
find . -name '*.html' | xargs perl -0777 -p -i -e 's|.+<div class="jd-tagdata">\n(.*\n){1,5}?(?:.+<tr>\n.+\n.+<td><!-- no parameter comment --></td>\n.+</tr>\n)+?(.+\n){1,5}?.*</div>||gm'
find . -name '_toc.yaml' | xargs sed -i 's|"com\.google\.firebase|"firebase|g'
find . -name '_toc.yaml' | xargs sed -i 's|"com\.google\.android\.gms\.|"|g'i
find . -name "_toc.yaml" | xargs sed -i 's|path: reference/|path: /android/reference/|g'
find . -name "_toc.yaml" | xargs sed -i 's|path: /docs/reference/android/|path: /android/reference/|g'

#Delete blank lines
find . -name "_toc.yaml" | xargs sed -i 's/[ \t]*$//'

#Path substitutions after cl/273770926
find . -name '*.html' | xargs sed -i 's/\/_project.yaml/\/android\/_project.yaml/'

#Delete newline after section:
find . -name "_toc.yaml" | xargs sed -i -z 's/section:\n\n+/section:\n/g'

#Path substitution after cl/273770926
find . -name '*.html' | xargs sed -i 's|/docs/reference/_book.yaml|/android/_book.yaml|g'

#Delete whitespaces after section
find . -name "_toc.yaml" | xargs sed -i 's|section: |section:|g'

#Delete blank lines at top of file
find . -name "_toc.yaml" | xargs sed -i '/./,$!d'

rm -rf "ktx/"
