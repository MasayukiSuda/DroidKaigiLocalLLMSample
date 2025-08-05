# Technology Stack

## Core Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle with Kotlin DSL
- **Package**: com.daasuu.llmsample

## Android Configuration
- Compile SDK: 36
- Target SDK: 36
- Min SDK: 24
- JVM Target: Java 11

## Dependencies
### Core Android
- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.activity:activity-compose

### Compose
- androidx.compose:compose-bom (platform)
- androidx.compose.ui:ui
- androidx.compose.ui:ui-graphics
- androidx.compose.ui:ui-tooling-preview
- androidx.compose.material3:material3

### Testing
- junit:junit (unit tests)
- androidx.test.ext:junit (Android tests)
- androidx.test.espresso:espresso-core
- androidx.compose.ui:ui-test-junit4

### Debug Only
- androidx.compose.ui:ui-tooling
- androidx.compose.ui:ui-test-manifest