# Suggested Commands for Development

## Building and Running
```bash
# Build the project
./gradlew build

# Install debug build on connected device
./gradlew installDebug

# Run debug build (after installation)
adb shell am start -n com.daasuu.llmsample/.MainActivity

# Clean build
./gradlew clean

# Assemble debug APK
./gradlew assembleDebug
```

## Testing
```bash
# Run unit tests
./gradlew test

# Run instrumentation tests on connected device
./gradlew connectedAndroidTest

# Run all checks (includes lint)
./gradlew check
```

## Code Quality
```bash
# Run Android Lint
./gradlew lint

# Run Lint and auto-fix safe issues
./gradlew lintFix

# Generate lint report for debug variant
./gradlew lintDebug
```

## Debugging and Analysis
```bash
# Show project dependencies
./gradlew dependencies

# Show signing configuration
./gradlew signingReport

# Check if Jetifier is needed
./gradlew checkJetifier
```

## Git Commands (macOS/Darwin)
```bash
# Standard git commands work normally on macOS
git status
git diff
git add .
git commit -m "message"
git push
```

## System Utilities (macOS specific)
```bash
# File operations
ls -la              # List files with hidden files
find . -name "*.kt" # Find Kotlin files
grep -r "pattern"   # Recursive search

# Process management
ps aux | grep gradle    # Check running gradle processes
killall gradle          # Kill all gradle processes

# File viewing
cat file.txt           # View file contents
less file.txt          # Page through file
head -n 20 file.txt    # View first 20 lines
tail -f logfile        # Follow log file
```