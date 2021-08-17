# Conformance Tests

These tested are copied from an internal test suite
(cs/apphosting/datastore/testing/query_conformance/firestore_v1_traces) and should not be modified.
The files were converted from text to binary encoding. Some files were split to reduce overall
memory consumption during the test run.

The Protobuf definition is at 
`firebase-firestore/src/proto/google/apphosting/datastore/testing/datastore_test_trace.proto`.

These tests are run as part of `:connectedCheck`.