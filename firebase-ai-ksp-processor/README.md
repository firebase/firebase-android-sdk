## To Build
run `./gradlew :firebase-ai-ksp-processor:publishToMavenLocal` 

## To Integrate
add the following to your app's gradle file:

```kotlin
plugins {
    id("com.google.devtools.ksp")
}
dependencies {
    implementation("com.google.firebase:firebase-bom:<latest-version>")
    implementation("com.google.firebase:firebase-ai")
    ksp("com.google.firebase:firebase-ai-ksp-processor:1.0.0")
}
```
## To Use

1. Create a data class that expresses the object you want the model to return
2. Make sure that this data class is @Serializable and @Generable
3. Ensure that any classes nested within this data class are also @Serializable data classes
4. Ensure that the data class has a companion object
5. Add documentation to the class, either with kDocs or the @Guide annotation, to help the model 
   understand it.
6. You may now access the schema using `<classname>.firebaseAISchema()`
7. You can use this schema to call `GenerativeModel#generateObject()`

## Example

```kotlin
/**
 * A Design for a new car
 * @property doors number of doors for the car
 * @property isElectric should the car be electric or gas powered
 * @property power how much power should the car have (in horsepower)
 * @property range how far should the car be able to go on one tank of gas or one charge
 * @property efficiency in either miles per gallon or miles or kilowatt-hour
 */
@Serializable
@Generable
public data class CarDesign(
    public val doors: Int,
    public val isElectric: Boolean,
    public val power: Int,
    public val range: Int,
    public val efficiency: Double
){
    companion object
}

// elsewhere
val model = Firebase.ai.generativeModel()
val objectResponse = model.generateObject(
    CarDesign.firebaseAISchema(), 
    "Design the future of transportation.")

val carDesign = objectResponse.getObject()
```