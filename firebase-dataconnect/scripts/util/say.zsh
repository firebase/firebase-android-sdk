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

###############################################################################
# Function: say
#
# An "echo" replacement, with improved predictability and safety.
#
# Each argument is printed, separated by a space.
#
# This function is simply a shorthand for the following:
#   builtin print -r -- [arguments]
# This performs the same thing as a traditional "echo" command, but more
# reliably, reducing the chances of errors. By specifying -r to "print", there
# are no magical "escapes" done (e.g. "\n" prints "\n", not a newline
# character). By specifying -- it avoids accidentally specifying a flag to
# "print" when you just wanted to print a string that happens to start with the
# "-" character.
###############################################################################

say() {
  builtin print -r -- "$@"
}

###############################################################################
# Function: sayp
#
# Identical to "say" but with "prompt expansion" enabled.
#
# WARNING: Avoid using "sayp" with strings whose contents are out of your
# control, such as output from an external command. This is because the output
# may coincidentally contain '%' characters that would unexpectedly be subject
# to prompt expansion.
#
# Example:
#   sayp "%F{red}ERROR:%f something went wrong"
#
# This example prints "ERROR: something went wrong" with the text "ERROR:" in
# red.
#
# https://zsh.sourceforge.io/Doc/Release/Prompt-Expansion.html
###############################################################################

sayp() {
  builtin print -rP -- "$@"
}

###############################################################################
# Function: sayn
#
# Identical to "say" but suppresses the trailing newline.
###############################################################################

sayn() {
  builtin print -rn -- "$@"
}

###############################################################################
# Function: saypn
#
# Combines "sayp" with "sayn".
#
# This function is useful in combination with other "say" commands to apply
# prompt expansion only on a _subset_ of the printed text, especially if some
# of the text is out of the script's control, like output from an external
# command.
#
# WARNING: Like with the "sayp" function, avoid using "saypn" with strings whose
# contents are out of your control, such as output from an external command.
#
# Example:
#   command_output=$(cat somefile.txt)
#   saypn "The contents of %Ssomefile.txt%s are: "
#   say "${command_output}"
#
# This example prints "The contents of somefile.txt are: ", followed by the
# contents of the file, followed by a newline, with "somefile.txt" printed in
# "standout" mode.
###############################################################################

saypn() {
  builtin print -rPn -- "$@"
}

###############################################################################
# Function: say_args
#
# Prints a command and its arguments escaping whitespace in a manner that could
# be copied-and-pasted into a zsh shell to run it.
#
# This function is intended to be used to print a command and its arguments to
# stdout before actually running the command.
###############################################################################

say_args() {
  say "${(q)@}"
}

###############################################################################
# Function: say_error
#
# Prints an error message, prefixed by "ERROR:" printed in red.
#
# Each argument of this function makes up the error message.
###############################################################################

say_error() {
  saypn "%F{red}ERROR:%f "
  say "$@"
}
