FireMAT PostgreSQL Emulator Helper

This directory hosts some scripts useful for setting up a PostgreSQL server
and corresponding pgAdmin web UI in containers. By using containers, the
database is completely isolated from your local host and can be easily
reset. The idea is that the FireMAT emulator, which will eventually need to
talk to a _real_ PostgreSQL database, can launch these containers rather than
requiring PostgreSQL to be installed on your local machine.

Rather than using Docker, which generally requires root privileges, the scripts
in this directory use `podman` (https://podman.io/), which natively supports
rootless operation. Googlers see go/dont-install-docker and go/rootless-podman.

