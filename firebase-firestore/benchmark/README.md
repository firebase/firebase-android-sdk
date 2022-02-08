# firebase-firestore:benchmark

This module contains benchmarks for Firebase Firestore. Benchmarks can be run by invoking
`./gradlew :firabase-firestore:benchmark:connectedCheck`.

The benchmarks use Jetpack's Benchmarking Library for microbenchmarks and are configured to
run both on device and on emulator.

At the end of a run, the results are written to /storage/emulated/0/Download on the test device.
You may extract these results using the following JavaScript snippet:

```javascript

// Helper script to convert then JSON output from Jetpack Benchmark to CSV
// Invoke as such: $ node convertToCSV.js ./result.json

/** Extracst the parameter names from the paramemetized test name. */
function extractPropNames(name) {
  const propPortion = name.substring(name.indexOf("-") + 2, name.length - 1);
  return propPortion.split(/\ |,\ /).filter((_, i) => i % 2 === 1);
}

/** Extracst the parameter values from the paramemetized test name. */
function extractPropValues(name) {
  const propPortion = name.substring(name.indexOf("-") + 2, name.length - 1);
  return propPortion.split(/\ |,\ /).filter((_, i) => i % 2 === 0);
}

/** Extracst the original test name from the paramemetized test name. */
function extractName(name) {
  return name.substring(0, name.indexOf("["));
}

/** Checks if two benchmark results share the same configuration. */
function matches(row1, row2) {
  if (row1.length !== row2.length) {
    return false;
  }

  for (let i = 0; i < row1.length; ++i) {
    if (row1[i] === row2[i]) {
      continue;
    } else if (row1[i] === "") {
      continue;
    } else if (row2[i] === "") {
      continue;
    }
    return false;
  }
  return true;
}

/** Merges the data from two benchmark results that share the same configuration. */
function merge(row1, row2) {
  let result = [];
  for (let i = 0; i < row1.length; ++i) {
    if (row1[i] === row2[i]) {
      result.push(row1[i]);
    } else if (row1[i] !== "") {
      result.push(row1[i]);
    } else {
      result.push(row2[i]);
    }
  }
  return result;
}

const filename = process.argv[2];
const json = require(filename);

let csv = "";

// Create the CSV header
const props = extractPropNames(json.benchmarks[0].name);
const names = json.benchmarks
  .map((b) => b.name)
  .map((n) => extractName(n))
  .filter((v, i, arr) => arr.indexOf(v) === i);

csv += [...props, ...names].join(",") + "\n";

// Convert all test cases to CSV
let testCases = [];
for (const benchmark of json.benchmarks) {
  const values = extractPropValues(benchmark.name);
  testCases.push([
    ...values,
    ...names.map((n) =>
      n === extractName(benchmark.name) ? benchmark.metrics.timeNs.median : ""
    ),
  ]);
}

// Go through all test cases and merge results for tests with the same configuration
testCases = testCases.reduce((previousValue, currentValue) => {
  if (
    previousValue.length !== 0 &&
    matches(previousValue[previousValue.length - 1], currentValue)
  ) {
    previousValue[previousValue.length - 1] = merge(
      previousValue[previousValue.length - 1],
      currentValue
    );
  } else {
    previousValue.push(currentValue);
  }
  return previousValue;
}, []);

csv += testCases
  .map((v) => JSON.stringify(v))
  .map((v) => v.substring(1, v.length - 1))
  .join("\n");

console.log(csv);
```