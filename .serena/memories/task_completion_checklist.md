# Task Completion Checklist

When completing any development task in this project, ensure you:

## 1. Code Quality Checks
- [ ] Run `./gradlew lint` to check for Android-specific issues
- [ ] Fix any lint warnings with `./gradlew lintFix` (for safe fixes only)
- [ ] Ensure code follows official Kotlin style guide

## 2. Testing
- [ ] Run unit tests: `./gradlew test`
- [ ] If UI changes were made, run instrumentation tests: `./gradlew connectedAndroidTest`
- [ ] Verify the app builds successfully: `./gradlew build`

## 3. Manual Verification
- [ ] Install and run the app: `./gradlew installDebug`
- [ ] Test the implemented feature/fix manually on device/emulator
- [ ] Check for any runtime errors or crashes

## 4. Pre-commit
- [ ] Review all changes: `git diff`
- [ ] Ensure no sensitive information is being committed
- [ ] Verify imports are organized and unused ones removed
- [ ] Check that no debug/test code is left in production code

## 5. Documentation
- [ ] Update code comments if logic has changed significantly
- [ ] Update README.md if new features or setup steps were added

## Note
Since this project doesn't currently have:
- Specific formatting tools (ktlint, detekt, spotless)
- Pre-commit hooks
- CI/CD configuration

The above checklist focuses on available gradle tasks and manual verification. As the project evolves, additional tools may be added.