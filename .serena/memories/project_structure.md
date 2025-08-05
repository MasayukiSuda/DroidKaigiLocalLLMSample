# Project Structure

```
DroidKaigiLocalLLMSample/
├── app/                          # Main application module
│   ├── build.gradle.kts         # App-level build configuration
│   ├── proguard-rules.pro       # ProGuard rules (currently disabled)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/daasuu/llmsample/
│       │   │   ├── MainActivity.kt      # Entry point with Compose setup
│       │   │   └── ui/
│       │   │       └── theme/          # Material3 theme definitions
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/                    # Android resources
│       │       ├── drawable/
│       │       ├── mipmap-*/           # App icons
│       │       ├── values/             # Colors, strings, themes
│       │       └── xml/                # Backup rules, data extraction
│       ├── test/                       # Unit tests
│       │   └── java/com/daasuu/llmsample/
│       │       └── ExampleUnitTest.kt
│       └── androidTest/                # Instrumentation tests
├── gradle/                             # Gradle wrapper files
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Project settings
├── gradle.properties                   # Gradle properties
├── gradlew / gradlew.bat              # Gradle wrapper scripts
└── README.md                          # Project documentation (Japanese)
```

## Key Observations
- Single module Android application
- Clean separation of concerns with UI in separate package
- Standard Android resource organization
- Test infrastructure set up but minimal tests currently
- No specific LLM implementation code yet (project appears to be in initial setup phase)