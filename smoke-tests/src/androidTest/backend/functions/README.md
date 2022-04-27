# Function definitions required for smoke tests

The tests assume that these functions are deployed to the project used by smoke tests.

To deploy the functions, run the following commands:

```bash
# if not already logged into firebase-cli run
firebase login
firebase use $PROJECT_ID

# build and deploy functions
echo $(cd functions && npm install)
firebase deploy --only functions
```

To verify that deployment is successful, run the following commands:

```bash
curl -X POST -H "Content-Type:application/json" https://us-central1-fireescape-smoke-tests.cloudfunctions.net/addNumbers -d '{"data":{"firstNumber":13,"secondNumber":17}}'
```

It should return back:

```bash
{"result":{"firstNumber":13,"secondNumber":17,"operator":"+","operationResult":30}}
```
