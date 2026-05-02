# Gradle Wrapper

The gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`)
are NOT included in this scaffold because generating them requires a live Gradle installation.

## How to generate them

1. Open this project in Android Studio Giraffe or newer.
2. Android Studio will detect the missing wrapper and prompt you to generate it automatically.
3. Alternatively, if you have Gradle installed locally, run from the project root:

```
gradle wrapper --gradle-version 8.9
```

The wrapper will be created at `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties`.

After that, you can use `./gradlew` (Linux/Mac) or `gradlew.bat` (Windows) for all builds.
