# Unreleased
* [feature] Enable response generation in multiple modalities. (#6901)
* [changed] **Breaking Change**: Removed the `LiveContentResponse.Status` class, and instead have nested the status
  fields as properties of `LiveContentResponse`. (#6906)
* [changed] **Breaking Change**: Removed the `LiveContentResponse` class, and instead have provided subclasses
  of `LiveServerMessage` that match the responses from the model. (#6910)
* [feature] Added support for the `id` field on `FunctionResponsePart` and `FunctionCallPart`. (#6910)
