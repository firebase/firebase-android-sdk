#!/bin/bash

set -euo pipefail

if [[ $# -gt 0 ]] ; then
  echo "ERROR: no command-line arguments are supported, but got $*" >&2
  exit 2
fi

readonly PROJECT_ROOT_DIR="$(dirname "$0")/../.."

readonly args=(
  "${PROJECT_ROOT_DIR}/gradlew"
  "-p"
  "${PROJECT_ROOT_DIR}"
  "--configure-on-demand"
  ":firebase-dataconnect:dokkaGfm"
)

echo "${args[*]}"
"${args[@]}"

set -xv

cd "${PROJECT_ROOT_DIR}/firebase-dataconnect/build"
if [[ ! -d firebase-android-sdk ]] ; then
  git clone -b dataconnect_apidocs --reference "${PROJECT_ROOT_DIR}/.git" git@github.com:FirebasePrivate/firebase-android-sdk.git
fi

git -C firebase-android-sdk reset --hard
git -C firebase-android-sdk clean -dffx
git -C firebase-android-sdk pull

rm -rf firebase-android-sdk/apidocs
mv dokka/gfm firebase-android-sdk/apidocs

cd firebase-android-sdk
./cleanup.sh

if git diff --quiet ; then
  readonly APIDOCS_UPDATED=0
else
  git commit -am "updated apidocs on $(date)"
  readonly APIDOCS_UPDATED=1
fi

set +xv

echo
echo "Dokka GitHub-Flavored Markup API documentation has been generated."

if [[ "$APIDOCS_UPDATED" == "0" ]] ; then
  echo "No changes to the documentation occurred."
else
  echo "Changes to the documentation occurred."
  echo "To push the apidocs to GitHub, run this command:"
  echo "git -C \"${PROJECT_ROOT_DIR}/firebase-dataconnect/build/firebase-android-sdk\" push"
fi
