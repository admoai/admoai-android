# Contributing to AdMoai Android SDK

Thank you for considering contributing to the AdMoai Android SDK! This document outlines the process and guidelines for contributing.

## Development Workflow

1. **Fork the repository** and clone it locally
2. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. **Make your changes** following our coding standards
4. **Test your changes** thoroughly
5. **Commit using Conventional Commits** (see below)
6. **Push to your fork** and create a Pull Request

## Commit Message Convention

We use [Conventional Commits](https://www.conventionalcommits.org/) for automated changelog generation and semantic versioning.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

- **feat**: A new feature (triggers MINOR version bump)
- **fix**: A bug fix (triggers PATCH version bump)
- **perf**: Performance improvements (triggers PATCH version bump)
- **refactor**: Code changes that neither fix bugs nor add features
- **chore**: Changes to build process, dependencies, or tooling
- **docs**: Documentation only changes
- **style**: Code style changes (formatting, missing semicolons, etc.)
- **test**: Adding or updating tests
- **build**: Changes to build system or dependencies
- **ci**: Changes to CI/CD configuration

### Breaking Changes

To indicate a breaking change (triggers MAJOR version bump):

```
feat!: remove deprecated API methods

BREAKING CHANGE: The old `requestAd()` method has been removed. Use `requestAds()` instead.
```

### Examples

**Good commit messages:**

```bash
feat: add geofencing support for location targeting
fix: resolve crash on API 24 devices when initializing SDK
perf: optimize ad request caching mechanism
docs: update integration guide with Compose examples
refactor: simplify targeting builder API
test: add unit tests for custom targeting attributes
chore: update Kotlin to 1.9.0
```

**Bad commit messages:**

```bash
Update code                    # Too vague
Fixed bug                      # Missing type and description
FEAT: Add feature             # Type should be lowercase
feat: Added new feature.      # Description should be imperative mood
```

## Testing Requirements

Before submitting a PR:

1. **Run all tests:**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

2. **Ensure code coverage** doesn't decrease significantly

3. **Test on multiple Android versions:**
   - Minimum: API 24 (Android 7.0)
   - Target: API 35 (Android 15)

4. **Verify sample app** still works:
   ```bash
   ./gradlew :sample:assembleDebug
   ./gradlew :sample:installDebug
   ```

## Pull Request Guidelines

### PR Title

PR titles must follow Conventional Commits format (enforced by CI):

```
feat: add video ad support
fix: resolve memory leak in ad tracking
docs: update README with installation steps
```

### PR Description

Include:
- **What**: Summary of changes
- **Why**: Motivation and context
- **How**: Technical approach (if non-trivial)
- **Testing**: How you tested the changes
- **Screenshots**: If UI changes (from sample app)
- **Breaking Changes**: If applicable

### Checklist

- [ ] Code follows project style guidelines
- [ ] Self-reviewed the code
- [ ] Commented complex/non-obvious code
- [ ] Updated documentation (if needed)
- [ ] Added/updated tests
- [ ] All tests pass locally
- [ ] No new warnings or errors
- [ ] PR title follows Conventional Commits

## Code Style

We follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

- **Line length**: 120 characters max
- **Indentation**: 4 spaces (no tabs)
- **Naming**:
  - Classes: `PascalCase`
  - Functions/variables: `camelCase`
  - Constants: `SCREAMING_SNAKE_CASE`
- **Organize imports**: Remove unused, group by Android/Kotlin/Third-party

ktlint enforces this in CI (the `Lint, test & build` job fails on `ktlintCheck`). The rules,
and the reason for each deviation from the defaults, live in `.editorconfig`.

Run the linter before committing:
```bash
./gradlew ktlintCheck
./gradlew ktlintFormat  # Auto-fix issues
```

## When the API check fails

`sdk/api/sdk.api` is the SDK's public API, one member per line with its JVM signature, and CI
(`Public API check (:sdk)`) fails when the code no longer matches it. That is deliberate: a
removed or changed public symbol breaks every integrator's build on their next Gradle refresh,
so the change has to appear as a diff in the PR, where a reviewer who does not read Kotlin can
still see it.

1. Run `./gradlew :sdk:apiDump` and commit the updated `sdk/api/sdk.api`.
2. Read the diff. Only `+` lines (new API) is a `feat:`. Any `-` or changed line (removed,
   renamed, a signature or visibility change) is a **breaking change**: mark the PR `feat!:`
   (or add a `BREAKING CHANGE:` footer) so release-please bumps the major version, and say in
   the PR what integrators must change.
3. If the surface changed by accident (a helper that should have been `internal`), fix the
   visibility instead of updating the baseline.

## Security

If you discover a security vulnerability:

1. **DO NOT** open a public issue
2. Email security@admoai.com with details
3. Wait for acknowledgment before disclosing publicly

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

## Need Help?

- **Documentation**: [README.md](README.md)
- **Issues**: [GitHub Issues](https://github.com/admoai/admoai-android/issues)
- **Discussions**: [GitHub Discussions](https://github.com/admoai/admoai-android/discussions)

---

Thank you for contributing!

