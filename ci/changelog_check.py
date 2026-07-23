#!/usr/bin/env python3
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

import os
import sys
import subprocess
import json
import re
import urllib.request

# Ignore changes in these directories
EXCLUDE_DIRECTORIES = [
  '.github/',
  'plugins/',
  'ci/',
  'encoders/',
  'firebase-annotations/',
  'firebase-components/',
  'firebase-datatransport/',
  'gradle/',
  'health-metrics/',
  'integ-testing/',
  'protolite-well-known-types/',
  'smoke-tests/',
  'third_party/',
  'tools/',
  'transport/'
]

SIGNATURE = "<!-- danger-python-changelog-warning -->"

def should_file_be_excluded(file, exclude_paths):
    return any(dir_path in file for dir_path in exclude_paths)

def has_changes_in(paths, modified_files):
    for path in paths:
        regex = re.compile(path)
        if any(regex.search(file) for file in modified_files):
            return True
    return False

def has_sdk_changes(modified_files):
    return any(not should_file_be_excluded(f, EXCLUDE_DIRECTORIES) for f in modified_files)

def get_modified_files():
    try:
        # Compare HEAD with its first parent.
        # In GitHub Actions pull_request event, HEAD is the merge commit,
        # and HEAD^1 is the base branch (target of PR).
        result = subprocess.run(
            ['git', 'diff', '--name-only', 'HEAD^1'],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True
        )
        files = result.stdout.strip().split('\n')
        return [f for f in files if f]
    except subprocess.CalledProcessError as e:
        print(f"Warning: git diff HEAD^1 failed: {e.stderr.strip()}", file=sys.stderr)
        print("Attempting fallback to diff against origin/main", file=sys.stderr)
        # Fallback for local testing or different checkout configurations
        for base in ['origin/main', 'origin/master', 'main', 'master']:
            try:
                result = subprocess.run(
                    ['git', 'diff', '--name-only', f'{base}...HEAD'],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=True
                )
                files = result.stdout.strip().split('\n')
                return [f for f in files if f]
            except subprocess.CalledProcessError:
                continue
        print("Error: Could not determine modified files via git.", file=sys.stderr)
        return []

def get_pr_details():
    event_path = os.environ.get('GITHUB_EVENT_PATH')
    if not event_path:
        print("GITHUB_EVENT_PATH not set. Running in non-CI mode?", file=sys.stderr)
        return None, [], ""

    try:
        with open(event_path, 'r') as f:
            event_data = json.load(f)
            pull_request = event_data.get('pull_request')
            if pull_request:
                pr_number = pull_request.get('number')
                labels = [l['name'] for l in pull_request.get('labels', [])]
                body = pull_request.get('body') or ""
                return pr_number, labels, body
            else:
                print("Event is not a pull request.", file=sys.stderr)
                return None, [], ""
    except Exception as e:
        print(f"Error reading GITHUB_EVENT_PATH: {e}", file=sys.stderr)
        return None, [], ""

def post_or_update_comment(repo, pr_number, token, message):
    comments_url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "Python-urllib"
    }
    
    # List comments to find existing one
    req = urllib.request.Request(comments_url, headers=headers)
    try:
        with urllib.request.urlopen(req) as response:
            comments = json.loads(response.read().decode())
    except Exception as e:
        print(f"Error fetching comments: {e}", file=sys.stderr)
        return

    existing_comment = None
    for comment in comments:
        if SIGNATURE in comment.get('body', ''):
            existing_comment = comment
            break

    body = f"{message}\n{SIGNATURE}"
    data = json.dumps({"body": body}).encode('utf-8')

    if existing_comment:
        comment_id = existing_comment['id']
        # If the message is the same, do nothing
        if existing_comment.get('body') == body:
            print(f"Comment {comment_id} is up to date.")
            return
        
        update_url = f"https://api.github.com/repos/{repo}/issues/comments/{comment_id}"
        req = urllib.request.Request(update_url, data=data, headers=headers, method='PATCH')
        action = f"updating existing comment {comment_id}"
    else:
        req = urllib.request.Request(comments_url, data=data, headers=headers, method='POST')
        action = "creating new comment"

    print(f"Action: {action}")
    try:
        with urllib.request.urlopen(req) as response:
            print("Comment posted/updated successfully.")
    except Exception as e:
        print(f"Error posting/updating comment: {e}", file=sys.stderr)

def delete_comment_if_exists(repo, pr_number, token):
    comments_url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "Python-urllib"
    }
    
    req = urllib.request.Request(comments_url, headers=headers)
    try:
        with urllib.request.urlopen(req) as response:
            comments = json.loads(response.read().decode())
    except Exception as e:
        print(f"Error fetching comments: {e}", file=sys.stderr)
        return

    for comment in comments:
        if SIGNATURE in comment.get('body', ''):
            comment_id = comment['id']
            delete_url = f"https://api.github.com/repos/{repo}/issues/comments/{comment_id}"
            req = urllib.request.Request(delete_url, headers=headers, method='DELETE')
            try:
                with urllib.request.urlopen(req) as response:
                    print(f"Deleted existing comment {comment_id}")
            except Exception as e:
                print(f"Error deleting comment: {e}", file=sys.stderr)
            break

def main():
    modified_files = get_modified_files()
    print(f"Modified files: {modified_files}")

    pr_number, labels, body = get_pr_details()
    print(f"PR number: {pr_number}, Labels: {labels}")
    if body:
        print(f"PR body preview: {body[:100]}...")
    else:
        print("PR body is empty or not available.")

    declared_trivial = "no-changelog" in labels or "NO_RELEASE_CHANGE" in body
    has_changelog_changes = has_changes_in(["CHANGELOG"], modified_files)
    sdk_changes = has_sdk_changes(modified_files)

    print(f"SDK changes: {sdk_changes}")
    print(f"Changelog changes: {has_changelog_changes}")
    print(f"Declared trivial: {declared_trivial}")

    warning_needed = sdk_changes and not has_changelog_changes and not declared_trivial

    token = os.environ.get('GITHUB_TOKEN')
    repo = os.environ.get('GITHUB_REPOSITORY')

    warning_message = (
        "Did you forget to add a changelog entry? "
        "(Add the 'no-changelog' label to the PR or 'NO_RELEASE_CHANGE' to the description to silence this warning.)"
    )

    if warning_needed:
        print(f"WARNING: {warning_message}")
        # Also output as GitHub Actions warning annotation
        print(f"::warning::{warning_message}")
        
        if token and repo and pr_number:
            post_or_update_comment(repo, pr_number, token, warning_message)
        else:
            print("Missing token, repo, or PR number. Skipping PR comment.", file=sys.stderr)
    else:
        print("No warning needed.")
        if token and repo and pr_number:
            delete_comment_if_exists(repo, pr_number, token)

if __name__ == "__main__":
    main()
