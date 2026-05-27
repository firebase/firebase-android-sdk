#!/usr/bin/env zsh

setopt errexit nounset pipefail

path+=("$ANDROID_HOME/cmdline-tools/latest/bin")

sdkmanager --install 'system-images;android-24;google_apis;x86_64' 'system-images;android-36;google_apis;x86_64'

avdmanager --verbose create avd --device small_phone --name SmallPhoneAPI24 --package 'system-images;android-24;google_apis;x86_64'
avdmanager --verbose create avd --device small_phone --name SmallPhoneAPI36 --package 'system-images;android-36;google_apis;x86_64'

headless_android_emulator_run.zsh SmallPhoneAPI36 -- -memory 8192 -cores 4 -read-only -ports 5554,5555
headless_android_emulator_run.zsh SmallPhoneAPI36 -- -memory 8192 -cores 4 -read-only -ports 5556,5557
headless_android_emulator_run.zsh SmallPhoneAPI36 -- -memory 8192 -cores 4 -read-only -ports 5558,5559
headless_android_emulator_run.zsh SmallPhoneAPI36 -- -memory 8192 -cores 4 -read-only -ports 5560,5561
