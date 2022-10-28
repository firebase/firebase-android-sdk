#!/bin/sh +v

errorOnInvalidDirectory() {
  local directory=$1
  local errorIfNotFound="${2:-Missing directory: $directory}"

  if [ ! -d "$directory" ]; then
    echo "[ERROR]: $errorIfNotFound" >&2
    exit 1
  fi
}

ROOT_DIRECTORY=../..
BUILD_DIRECTORY=$ROOT_DIRECTORY/build
OLD_DIRECTORY=firebase-kotlindoc-old
NEW_DIRECTORY=firebase-kotlindoc

errorOnInvalidDirectory $BUILD_DIRECTORY "Missing build directory. Please ensure you run the script from the scripts directory."
errorOnInvalidDirectory $BUILD_DIRECTORY/$OLD_DIRECTORY "Missing '$OLD_DIRECTORY'. These should be the docs you are comparing from."
errorOnInvalidDirectory $BUILD_DIRECTORY/$NEW_DIRECTORY "Missing '$NEW_DIRECTORY'. These should be the docs you are comparing against."

diff  --recursive $BUILD_DIRECTORY/$OLD_DIRECTORY $BUILD_DIRECTORY/$NEW_DIRECTORY