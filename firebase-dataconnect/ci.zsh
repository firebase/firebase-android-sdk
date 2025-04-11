#!/bin/zsh

setopt ERR_EXIT
setopt NO_UNSET
setopt PIPE_FAIL

function main {
  if [[ $# -eq 0 ]] {
    echo "ERROR: no subcommand specified" >&2
    exit 2
  }

  local -r subcommand_name="$1"
  shift

  case "$subcommand_name" {
    install_firebase_tools) subcommand/install_firebase_tools "$@" ;;
    print_tool_versions) subcommand/print_tool_versions "$@" ;;
    gradle_assemble) subcommand/gradle_assemble "$@" ;;
    gradle_connected_check) subcommand/gradle_connected_check "$@" ;;
    kvm_setup) subcommand/kvm_setup "$@" ;;
    start_firebase_emulators) subcommand/start_firebase_emulators "$@" ;;
    start_logcat_capture) subcommand/start_logcat_capture "$@" ;;
    verify_connected_check_success) subcommand/verify_connected_check_success "$@" ;;
    send_notifications) subcommand/send_notifications "$@" ;;
    *)
      echo "ERROR: no subcommand with the given name: $subcommand_name" >&2
      exit 2
  }
}

function subcommand/install_firebase_tools {
  zparseopts -F -A args - install_dir: firebase_tools_version:
  fail_if_missing_arg -install_dir
  fail_if_missing_arg -firebase_tools_version

  local -r install_dir="${args[-install_dir]}"
  local -r firebase_tools_version="${args[-firebase_tools_version]}"

  set -xv
  mkdir -p "$install_dir"
  cd "$install_dir"
  echo '{}' > package.json
  npm install --fund=false --audit=false --save --save-exact "firebase-tools@${firebase_tools_version}"
}

function run_command_for_print_tool_versions {
  echo "==============================================================================="
  echo "Running Command: $*"
  ("$@" || echo "WARNING: command failed with non-zero exit code $?: $*") 2>&1
}

function subcommand/print_tool_versions {
  zparseopts -F -A args - firebase_command:
  fail_if_missing_arg -firebase_command

  local -r firebase_command="${args[-firebase_command]}"

  run_command_for_print_tool_versions uname -a
  run_command_for_print_tool_versions which java
  run_command_for_print_tool_versions java -version
  run_command_for_print_tool_versions which javac
  run_command_for_print_tool_versions javac -version
  run_command_for_print_tool_versions which node
  run_command_for_print_tool_versions node --version
  run_command_for_print_tool_versions "$firebase_command" --version
  run_command_for_print_tool_versions ./gradlew --version
}

function subcommand/gradle_assemble {
  local -r gradle_args=(
    ./gradlew
    --profile
    --configure-on-demand
    "$@"
    :firebase-dataconnect:assembleDebugAndroidTest
  )

  set -xv
  "${gradle_args[@]}"
}

function subcommand/gradle_connected_check {
  local -r gradle_args=(
    ./gradlew
    --profile
    --configure-on-demand
    "$@"
    :firebase-dataconnect:connectedCheck
    :firebase-dataconnect:connectors:connectedCheck
  )

  set -xv
  "${gradle_args[@]}"
}

function subcommand/kvm_setup {
  if [[ $# -ne 0 ]] {
    echo "ERROR: no arguments expected, but got $#: $*" >&2
    exit 2
  }

  set -xv
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
    | sudo tee /etc/udev/rules.d/99-kvm4all.rules
  sudo udevadm control --reload-rules
  sudo udevadm trigger --name-match=kvm
}

function subcommand/start_firebase_emulators {
  zparseopts -F -A args - firebase_command: postgres_connection_url: log_file: enable_debug_logs:
  fail_if_missing_arg -firebase_command
  fail_if_missing_arg -postgres_connection_url
  fail_if_missing_arg -log_file
  fail_if_missing_arg -enable_debug_logs

  local -r firebase_command="${args[-firebase_command]}"
  local -r postgres_connection_url="${args[-postgres_connection_url]}"
  local -r log_file="${args[-log_file]}"
  local -r enable_debug_logs="${args[-enable_debug_logs]}"

  local firebase_args=("$firebase_command")

  if [[ $enable_debug_logs == '1' ]] {
    firebase_args=+("--debug")
  } elif [[ $enable_debug_logs != '0' ]] {
    echo "ERROR: invalid value for -enable_debug_logs: $enable_debug_logs (must be 0 or 1)" >&2
    exit 2
  }

  firebase_args+=(
    emulators:start
    --only auth,dataconnect
  )

  set -xv
  cd firebase-dataconnect/emulators
  export FIREBASE_DATACONNECT_POSTGRESQL_STRING="$postgres_connection_url"
  "${firebase_args[@]}" >"$log_file" 2>&1 &
}

function subcommand/start_logcat_capture {
  zparseopts -F -A args - adb_command: log_file:
  fail_if_missing_arg -adb_command
  fail_if_missing_arg -log_file

  local -r adb_command="${args[-adb_command]}"
  local -r log_file="${args[-log_file]}"

  set -xv
  "$adb_command" logcat >"$log_file" 2>&1 &
}

function subcommand/verify_connected_check_success {
  zparseopts -F -A args - connected_check_step_outcome:
  fail_if_missing_arg -connected_check_step_outcome

  local -r outcome="${args[-connected_check_step_outcome]}"

  echo "The outcome of the \"Gradle connectedCheck\" task was: $outcome"

  if [[ $outcome != "success" ]] {
    echo "Failing because the outcome of the \"Gradle connectedCheck\" step was not \"success\": $outcome"
    exit 1
  }
}

function subcommand/send_notifications {
  zparseopts -F -A args - \
    github_notification_issue: github_workflow_name: github_repository: \
    job_status: git_commit_hash: run_url: \
    run_id: run_number: run_attempt:

    fail_if_missing_arg -github_notification_issue
    fail_if_missing_arg -github_workflow_name
    fail_if_missing_arg -github_repository
    fail_if_missing_arg -job_status
    fail_if_missing_arg -git_commit_hash
    fail_if_missing_arg -run_url
    fail_if_missing_arg -run_id
    fail_if_missing_arg -run_number
    fail_if_missing_arg -run_attempt

    local -r github_notification_issue="${args[-github_notification_issue]}"
    local -r github_workflow_name="${args[-github_workflow_name]}"
    local -r github_repository="${args[-github_repository]}"
    local -r job_status="${args[-job_status]}"
    local -r git_commit_hash="${args[-git_commit_hash]}"
    local -r run_url="${args[-run_url]}"
    local -r run_id="${args[-run_id]}"
    local -r run_number="${args[-run_number]}"
    local -r run_attempt="${args[-run_attempt]}"

    local -r message_file=$(mktemp -t message.jpbbdtrx2f.XXXXXX)
    echo "Result of workflow $github_workflow_name at $git_commit_hash: $job_status" >>"$message_file"
    echo "$run_url" >>"$message_file"
    echo "run_id=$run_id run_number=$run_number run_attempt=$run_attempt" >>"$message_file"

    echo "Posting comment on GitHub Issue: https://github.com/firebase/firebase-android-sdk/issues/$github_notification_issue:"
    cat "$message_file"
    set -xv
    gh issue comment "$github_notification_issue" --body-file "$message_file" -R "$github_repository"
}

function fail_if_missing_arg {
  if [[ $# -ne 1 ]] {
    echo "INTERNAL ERROR qsvchnvaq7: invalid number of arguments: $# ($*)" >&2
    exit 1
  }
  if [[ ! -v args[$1] ]] {
    echo "ERROR: $1 must be specified (got ${(k)args})" >&2
    exit 2
  }
}

main "$@"
