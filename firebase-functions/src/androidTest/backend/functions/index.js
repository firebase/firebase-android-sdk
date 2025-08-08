// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

const assert = require('assert');
const functions = require('firebase-functions');
const functionsV2 = require('firebase-functions/v2');

/**
 * Pauses the execution for a specified amount of time.
 * @param {number} ms - The number of milliseconds to sleep.
 * @return {Promise<void>} A promise that resolves after the specified time.
 */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

exports.dataTest = functions.https.onRequest((request, response) => {
  assert.deepEqual(request.body, {
    data: {
      bool: true,
      int: 2,
      long: {
        value: '3',
        '@type': 'type.googleapis.com/google.protobuf.Int64Value',
      },
      string: 'four',
      array: [5, 6],
      'null': null,
    }
  });
  response.send({
    data: {
      message: 'stub response',
      code: 42,
      long: {
        value: '420',
        '@type': 'type.googleapis.com/google.protobuf.Int64Value',
      },
    }
  });
});

exports.scalarTest = functions.https.onRequest((request, response) => {
  assert.deepEqual(request.body, {data: 17});
  response.send({data: 76});
});

exports.tokenTest = functions.https.onRequest((request, response) => {
  assert.equal(request.get('Authorization'), 'Bearer token');
  assert.deepEqual(request.body, {data: {}});
  response.send({data: {}});
});

exports.instanceIdTest = functions.https.onRequest((request, response) => {
  assert.equal(request.get('Firebase-Instance-ID-Token'), 'iid');
  assert.deepEqual(request.body, {data: {}});
  response.send({data: {}});
});

exports.appCheckTest = functions.https.onRequest((request, response) => {
  assert.equal(request.get('X-Firebase-AppCheck'), 'appCheck');
  assert.deepEqual(request.body, {data: {}});
  response.send({data: {}});
});

exports.appCheckLimitedUseTest = functions.https.onRequest((request, response) => {
  assert.equal(request.get('X-Firebase-AppCheck'), 'appCheck-limited-use');
  assert.deepEqual(request.body, {data: {}});
  response.send({data: {}});
});

exports.nullTest = functions.https.onRequest((request, response) => {
  assert.deepEqual(request.body, {data: null});
  response.send({data: null});
});

exports.missingResultTest = functions.https.onRequest((request, response) => {
  assert.deepEqual(request.body, {data: null});
  response.send({});
});

exports.unhandledErrorTest = functions.https.onRequest((request, response) => {
  // Fail in a way that the client shouldn't see.
  throw 'nope';
});

exports.unknownErrorTest = functions.https.onRequest((request, response) => {
  // Send an http error with a body with an explicit code.
  response.status(400).send({
    error: {
      status: 'THIS_IS_NOT_VALID',
      message: 'this should be ignored',
    },
  });
});

exports.explicitErrorTest = functions.https.onRequest((request, response) => {
  // Send an http error with a body with an explicit code.
  response.status(400).send({
    error: {
      status: 'OUT_OF_RANGE',
      message: 'explicit nope',
      details: {
        start: 10,
        end: 20,
        long: {
          value: '30',
          '@type': 'type.googleapis.com/google.protobuf.Int64Value',
        },
      },
    },
  });
});

exports.httpErrorTest = functions.https.onRequest((request, response) => {
  // Send an http error with no body.
  response.status(400).send();
});

exports.timeoutTest = functions.https.onRequest((request, response) => {
  // Wait for longer than 500ms.
  setTimeout(() => response.send({data: true}), 500);
});

exports.headersTest = functions.https.onRequest((request, response) => {
  response.status(200).send({data: request.headers});
});

const streamData = ['hello', 'world', 'this', 'is', 'cool'];

/**
 * Generates chunks of text asynchronously, yielding one chunk at a time.
 * @async
 * @generator
 * @yields {string} A chunk of text from the data array.
 */
async function* generateText() {
  for (const chunk of streamData) {
    yield chunk;
    await sleep(100);
  }
}

exports.genStream = functionsV2.https.onCall(async (request, response) => {
  if (request.acceptsStreaming) {
    for await (const chunk of generateText()) {
      response.sendChunk(chunk);
    }
  }
  else {
      console.log("CLIENT DOES NOT SUPPORT STEAMING");
  }
  return streamData.join(' ');
});

exports.genStreamError = functionsV2.https.onCall(
    async (request, response) => {
      // Note: The functions backend does not pass the error message to the
      // client at this time.
      throw Error("BOOM")
    });

const weatherForecasts = {
  Toronto: { conditions: 'snowy', temperature: 25 },
  London: { conditions: 'rainy', temperature: 50 },
  Dubai: { conditions: 'sunny', temperature: 75 }
};

/**
 * Generates weather forecasts asynchronously for the given locations.
 * @async
 * @generator
 * @param {Array<{name: string}>} locations - An array of location objects.
 */
async function* generateForecast(locations) {
  for (const location of locations) {
    yield { 'location': location,  ...weatherForecasts[location.name] };
    await sleep(100);
  }
};

exports.genStreamWeather = functionsV2.https.onCall(
    async (request, response) => {
      const locations = request.data && request.data.data?
      request.data.data: [];
      const forecasts = [];
      if (request.acceptsStreaming) {
        for await (const chunk of generateForecast(locations)) {
          forecasts.push(chunk);
          response.sendChunk(chunk);
        }
      }
      return {forecasts};
    });

exports.genStreamEmpty = functionsV2.https.onCall(
  async (request, response) => {
    if (request.acceptsStreaming) {
      // Send no chunks
    }
    // Implicitly return null.
  }
);

exports.genStreamResultOnly = functionsV2.https.onCall(
  async (request, response) => {
    if (request.acceptsStreaming) {
      // Do not send any chunks.
    }
    return "Only a result";
  }
);

exports.genStreamLargeData = functionsV2.https.onCall(
  async (request, response) => {
    if (request.acceptsStreaming) {
      const largeString = 'A'.repeat(10000);
      const chunkSize = 1024;
      for (let i = 0; i < largeString.length; i += chunkSize) {
        const chunk = largeString.substring(i, i + chunkSize);
        response.sendChunk(chunk);
        await sleep(100);
      }
    } else {
      console.log("CLIENT DOES NOT SUPPORT STEAMING")
    }
    return "Stream Completed";
  }
);
