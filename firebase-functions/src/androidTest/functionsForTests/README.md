# Function definitions required for integration tests

The tests assume that these functions are deployed to the project used by integration tests.

To deploy the functions, run the following commands:

```bash
# if not already logged into firebase-cli run
firebase login
firebase use $PROJECT_ID

# build and deploy functions
echo $(cd functions && npm install)
firebase deploy --only functions
```