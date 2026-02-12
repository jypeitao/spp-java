### Build/Configuration Instructions

The project uses Gradle with Kotlin DSL. Key configuration files are:
- `build.gradle.kts`: Root-level build configuration.
- `settings.gradle.kts`: Project structure and repository definitions.
- `app/build.gradle.kts`: Application module configuration (namespace: `com.microlumin.xlink.spp.app`).
- `gradle/libs.versions.toml`: Centralized dependency management.

To build the project:
- Clean build: `./gradlew clean build`
- Assemble Debug APK: `./gradlew assembleDebug`

Minimum SDK: 28
Target SDK: 36
Java Version: 11

### Testing Information

#### Configuring and Running Tests
- **Local Unit Tests**: Located in `app/src/test/java`. These run on the JVM.
  - Run via CLI: `./gradlew test`
- **Instrumented Tests**: Located in `app/src/androidTest/java`. These require an Android device or emulator.
  - Run via CLI: `./gradlew connectedAndroidTest`

#### Adding New Tests
1. **Local Unit Tests**:
   - Create a Java class in `app/src/test/java/com/microlumin/xlink/spp/app/`.
   - Use JUnit 4 annotations (e.g., `@Test`).
   - Example:
     ```java
     package com.microlumin.xlink.spp.app;
     import org.junit.Test;
     import static org.junit.Assert.*;
     public class MyTest {
         @Test
         public void testAddition() {
             assertEquals(4, 2 + 2);
         }
     }
     ```
2. **Instrumented Tests**:
   - Create a class in `app/src/androidTest/java/com/microlumin/xlink/spp/app/`.
   - Use `@RunWith(AndroidJUnit4.class)` and `@Test`.

### Additional Development Information

#### Code Style
- Follows standard Android/Java coding conventions.
- Uses 4-space indentation.
- Import order: `android.*`, `androidx.*`, other imports.
- Use `EdgeToEdge` for UI activities where appropriate, as seen in `MainActivity.java`.

#### Dependency Management
Dependencies are managed in `gradle/libs.versions.toml`. When adding new libraries, update the `toml` file first, then reference them in `app/build.gradle.kts` using `libs.<alias>`.