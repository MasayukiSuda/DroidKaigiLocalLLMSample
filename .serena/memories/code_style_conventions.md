# Code Style and Conventions

## Kotlin Style
- **Official Kotlin Code Style** (kotlin.code.style=official)
- This follows the official Kotlin coding conventions as defined by JetBrains

## Project Structure
- Standard Android project structure
- Main source: `app/src/main/java/com/daasuu/llmsample/`
- UI components in `ui/` subdirectory
- Theme definitions in `ui/theme/` (Color.kt, Theme.kt, Type.kt)

## Compose Conventions
- Using Material3 design system
- Theme: DroidKaigiLocalLLMSampleTheme
- Composable functions use @Composable annotation
- Preview functions use @Preview annotation (e.g., GreetingPreview)

## Build Configuration
- Gradle properties use Kotlin DSL (.kts files)
- Version catalogs for dependency management (libs.* references)
- AndroidX enabled (android.useAndroidX=true)
- Non-transitive R class enabled for smaller APK size

## Naming Conventions
- Activities: *Activity (e.g., MainActivity)
- Composables: PascalCase functions (e.g., Greeting)
- Package structure follows domain hierarchy