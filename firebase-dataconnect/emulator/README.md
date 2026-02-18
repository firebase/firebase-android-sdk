# Firebase Data Connect Emulator Scripts

This directory contains scripts for launching the Firebase Data Connect emulator for the purposes of
running the integration tests.

Here is a summary of the detailed steps from below:

1. Compile the emulator in google3 by running one of the following commands:
   - Linux: `blaze build //third_party/firebase/dataconnect/emulator/cli:cli`
   - macOS Intel:
     `blaze build --config=darwin_x86_64 //third_party/firebase/dataconnect/emulator/cli:cli_macos`
   - macOS Arm64:
     `blaze build --config=darwin_arm64 //third_party/firebase/dataconnect/emulator/cli:cli_macos`
2. Install `podman`, such as via homebrew: `brew install podman`
3. On macOS, initialize Podman's Linux VM: `podman machine init`
4. On macOS, start Podman's Linux VM: `podman machine start`
5. Start the Postgresql container: `./start_postgres_pod.sh`
6. Start the emulator: `./cli -alsologtostderr=1 -stderrthreshold=0 dev`

## Step 1: Compile Firebase Data Connect emulator

Compile the Firebase Data Connect Emulator in google3 using `blaze`. The build must be done in a
gLinux workstation or go/cloudtop instance; namely, building on a macOS host is not supported, even
though macOS _is_ supported as a _target_ platform.

The exact command-line arguments for blaze depend on the target platform. Supported targets
platforms are: gLinux workstations or CloudTop instances and Google-issued MacBooks (both intel and
arm64 architectures).

First, create a CITC or Fig workspace to perform the build. The instructions below use CITC because
it is simpler; the instructions, however, can be easily adapted for Fig.

1. `p4 citc dataconnect_emulator`
2. `cd /google/src/cloud/USERNAME/dataconnect_emulator/google3`

#### Compile for Linux

When building the emulator targetting a gLinux workstation or go/cloudtop instance, build the `cli`
target and do not specify `--config` (because the host is the default target).

```
blaze build //third_party/firebase/dataconnect/emulator/cli:cli
```

If successful, the emulator binary will be located at

```
blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli
```

#### Compile for macOS

When building the emulator targetting macOS, build the `cli_macos` target (instead of the `cli`
target) and make sure to specify `--config=darwin_x86_64` for an Intel MacBook or
`--config=darwin_arm64` for an ARM-based MacBook.

```
blaze build --config=x86_64 //third_party/firebase/dataconnect/emulator/cli:cli
blaze build --config=darwin_arm64 //third_party/firebase/dataconnect/emulator/cli:cli_macos
```

If successful, the emulator binary will be located at

```
blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli_macos
```

#### Copy emulator binary to target machine

If the machine used to build the emulator binary is the same as the target machine, then you are
done. Otherwise, you need to copy the binary to the target machine. There are two easy ways to do
this: `scp` and `x20`.

To use `scp`, run this command on the target machine to copy the binary into the current directory.
Replace `HOSTNAME` with the hostname of the build machine, `USERNAME` with your username, and `cli`
with `cli_macos` if the target machine is macOS:

```
scp MACHINE:/google/src/cloud/USERNAME/dataconnect_emulator/google3/blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli .
```

To use `x20`, run this command on the build machine to copy the emulator binary into your private
x20 directory. Replace `US` with the first two letter of your username, `USERNAME` with your
username, and `cli` with `cli_macos` if the target machine is macOS:

```
cp blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli /google/data/rw/users/US/USERNAME/
```

On the target machine, navigate to http://x20/ in a web browser and download the emulator binary by
clicking on it.

To share the compiled binary with others you will need to use a "teams" directory in x20. See
g3doc/company/teams/x20/user_documentation/sharing_files_on_x20.md for details.

In either case, you will likely need to change the permissions of the binary to make it executable:

```
chmod a+x cli
```

or

```
chmod a+x cli_macos
```

#### Precompiled emulator binaries

dconeybe maintains a directory with precompiled emulator binaries:

http://x20/teams/firestore-clients/DataConnectEmulator

At the time of writing, these builds incorporate the patch to remove vector support, as mentioned in
the "Troubleshooting" section below.

## Step 2: Start Postgresql server

The Firebase Data Connect emulator requires a real Postgresql server to talk to. Installing and
configuring a Postgresql server on a given platform is a tedious and non-standard process. Moreover,
it is not consistent how the database's data is cleared if you wanted to start afresh.

Therefore, the instructions here document using a "Docker image" and its containerization technology
to run a Postgresql server in an isolated environment that is easily started, stopped, and reset to
a fresh state. Using Docker (https://www.docker.com) would definitely work; however, Docker is
strongly discourgaged becuase it requires its daemon to run as root, a massive security loophole. To
work around this, a competing product named "Podman" (https://podman.io) was born, and the
instructions here use Podman instead of Docker to avoid the unnecessary root daemon. See
go/dont-install-docker for more details on this.

The instructions to setup and run podman are quite simple on Linux, and a little mor involved on
macOS. However, once setup, launching the container is as easy as launching any other emulator.

#### Install Podman (Linux)

Installing Podman on gLinux workstations or CloudTop instances is as easy as running this script:
http://google3/experimental/users/superdanby/install-podman

#### Install Podman (macOS)

Installing Podman on MacBooks is easiest done via a package manager like Homebrew. Since
containerization technology is a Linux-specific feature, Podman needs to start a Linux virtual
machine in the background to actually _run_ the containers. As such there are some additional steps
required for macOS.

To install Podman, run these commands:

1. `brew install podman`
2. `podman machine init`
3. `podman machine start`

The "machine" commands create and start the Linux virtual machine, respectively.

#### Launch the Postgresql containers

A handy helper script is all that is needed to start the Postgresql server:

```
./start_postgres_pod.sh
```

It is safe to run this command if the containers are already running (they will just continue to run
unaffected).

The final output of the script shows some additional commands that can be run to, for example, stop
the Postgresql server and delete the Postgresql server's database.

There is also a Web UI called "pgadmin4" that can be used to visually interact with the database.
The URL and login credentials are included in the final lines of output from the script.

#### Launch the Data Connect emulator

With the Postgresql containers running, launch the Data Connect emulator with this command:

```
./cli -alsologtostderr=1 -stderrthreshold=0 dev -local_connection_string='postgresql://postgres:postgres@localhost:5432/emulator?sslmode=disable'
```

You will likely see some errors in the log output, but most of them can be safely ignored. At the
time of writing, these errors are safe to ignore:

- Anything from `codegen.go`, such as "ERROR - reading folder" and "ERROR - error loading schema"
- "unable to walk dir"

You definitely want to see lines like this:

```
UpdateSchema(): succeeds!
ClearConnectors(): succeeds!
UpdateConnector(person): succeeds!
UpdateConnector(posts): succeeds!
UpdateConnector(alltypes): succeeds!
```

Note that these log lines may change over time. Just know that some errors are "normal" and others,
especially those pertaining to loading the `.gql` files, could indicate a real problem.

## Troubleshooting

#### Error: python@3.12: the bottle needs the Apple Command Line Tools...

On macOS, if `brew install podman` gives an error like "Error: python@3.12: the bottle needs the
Apple Command Line Tools..." then use the mitigation it suggests. At the time of writing, the
preferred mitigation was to install the missing package by running `xcode-select --install`. If you
don't have Xcode installed at all, download the latest version from go/xcode and install it.

#### Unable to load "vector" or "google_ml_integration" pogstresql extensions.

Update (Mar 12, 2024): This should be fixed by cl/615215810, removing the need for the workaround
documented below.

If you get an error like this in the Data Connect Emulator's output:

```
E0311 11:22:53.381764       1 load.go:45] Could not deploy schema: failed to force migrate SQL database: failed to execute extension installation: pq: extension "vector" is not available
SQL: CREATE EXTENSION IF NOT EXISTS "vector"
```

or

```
E0311 11:26:50.893660       1 load.go:45] Could not deploy schema: failed to force migrate SQL database: failed to execute extension installation: pq: extension "google_ml_integration" is not available
SQL: CREATE EXTENSION IF NOT EXISTS "google_ml_integration" CASCADE
```

then the workaround is to comment out the lines in the emulator's source code that try to load these
extensions. This will, obviously, preclude using vector types, but as long as that is acceptable
then this workaround works.

To do this, comment out these lines from
`third_party/firebase/dataconnect/core/schema/migrate/plan.go`:

```
// TODO: b/319967793 - install vector extension only when db schema warrants it.
{Cmd: `CREATE EXTENSION IF NOT EXISTS "vector"`},
// install google_ml_integration extension
{Cmd: `CREATE EXTENSION IF NOT EXISTS "google_ml_integration" CASCADE`},
```

(http://google3/third_party/firebase/dataconnect/core/schema/migrate/plan.go;l=25-28;rcl=613618147)

Then, recompile the emulator, as described above.

See https://chat.google.com/room/AAAAdvEjzno/6pk_Mz7Hm5o and b/319967793 for details.
