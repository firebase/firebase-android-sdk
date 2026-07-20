#!/usr/bin/env bash
#
# Copyright 2026 Google LLC
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

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOK_PATH="$REPO_ROOT/.git/hooks/pre-commit"

echo "Installing Git pre-commit hook at $HOOK_PATH..."

cat << EOF > "$HOOK_PATH"
#!/usr/bin/env bash
set -e

echo "Running pre-commit hook: formatting and API generation..."

PRE_STATUS="\$(git status --porcelain)"

python3 "$REPO_ROOT/tools/firebase_checks.py"

POST_STATUS="\$(git status --porcelain)"

if [ "\$PRE_STATUS" != "\$POST_STATUS" ]; then
    echo ""
    echo "=========================================================================="
    echo "ERROR: Code formatting (sApp) or API generation (generateApiTxtFile)"
    echo "modified one or more files in your repository."
    echo "The commit has been ABORTED so you can review the changes and try again."
    echo "=========================================================================="
    echo ""
    git status --short
    echo ""
    exit 1
fi

echo ""
echo "Firebase specific checks succeeded!"
EOF

chmod +x "$HOOK_PATH"
echo "Pre-commit hook installed successfully!"
