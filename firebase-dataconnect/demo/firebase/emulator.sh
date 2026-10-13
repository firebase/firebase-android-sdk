#!/bin/sh

exec firebase -P fakeproject emulators:start --only dataconnect --debug
