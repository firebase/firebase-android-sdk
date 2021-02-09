const functions = require("firebase-functions");

// // Create and Deploy Your First Cloud Functions
// // https://firebase.google.com/docs/functions/write-firebase-functions
//
// exports.helloWorld = functions.https.onRequest((request, response) => {
//   functions.logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });

exports.addNumbers = functions.https.onCall((data) => {
  const firstNumber = data.firstNumber;
  const secondNumber = data.secondNumber;

  if (!Number.isFinite(firstNumber) || !Number.isFinite(secondNumber)) {
    throw new functions.https.HttpsError(
        "invalid-argument",
        "The function must be called with " +
        "two arguments \"firstNumber\" and \"secondNumber\" " +
        "which must both be numbers.");
  }

  return {
    firstNumber: firstNumber,
    secondNumber: secondNumber,
    operator: "+",
    operationResult: firstNumber + secondNumber,
  };
});
