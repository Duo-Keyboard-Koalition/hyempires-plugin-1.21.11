# Releasing HyEmpires

This document explains how to create releases for HyEmpires using GitHub Actions.

## Automatic Release (Recommended)

### Step 1: Create a Git Tag

Create a new tag with a version number:

```bash
git tag v1.0.0
```

Or for a pre-release:

```bash
git tag v1.0.0-beta
```

### Step 2: Push the Tag

Push the tag to GitHub:

```bash
git push origin v1.0.0
```

### Step 3: GitHub Actions Will Automatically:

1. Build the project using Maven
2. Compile the plugin JAR file
3. Create a GitHub Release with the tag name
4. Upload the compiled JAR file to the release
5. Generate release notes automatically

The release will be available at: `https://github.com/YOUR_USERNAME/hyempires-plugin-1.21.11/releases/tag/v1.0.0`

## Manual Release

If you need to create a release manually:

1. Go to the **Actions** tab in your GitHub repository
2. Select **Build and Release** workflow from the left sidebar
3. Click **Run workflow** button (top right)
4. Enter the version tag (e.g., `v1.0.0`)
5. Click **Run workflow**

The workflow will build and create a release with the specified version.

## Version Numbering

We recommend using [Semantic Versioning](https://semver.org/):

- **Major version** (v1.0.0): Breaking changes
- **Minor version** (v1.1.0): New features, backwards compatible
- **Patch version** (v1.0.1): Bug fixes, backwards compatible

Pre-releases can be marked with suffixes:
- `v1.0.0-alpha` - Early development
- `v1.0.0-beta` - Testing phase
- `v1.0.0-rc1` - Release candidate

## Development Builds

Every push to `main` or `master` branch automatically triggers a build:

- Build artifacts are available in the **Actions** tab
- Artifacts are kept for 7 days
- These are development builds (SNAPSHOT versions)
- Useful for testing before creating a release

## Downloading Releases

Users can download releases from:

1. **GitHub Releases Page**: 
   - Go to the repository's Releases page
   - Download the latest `HyEmpires-vX.X.X.jar` file

2. **Direct Link**:
   ```
   https://github.com/YOUR_USERNAME/hyempires-plugin-1.21.11/releases/latest/download/HyEmpires-v1.0.0.jar
   ```

3. **GitHub Actions Artifacts** (for development builds):
   - Go to the Actions tab
   - Click on a successful workflow run
   - Download the artifact from the bottom of the page

## Troubleshooting

### Build Fails

- Check that Java 21 is available
- Ensure Maven dependencies can be downloaded
- Check the Actions logs for specific error messages

### JAR File Not Found

- Verify the build completed successfully
- Check that the `target/` directory contains the JAR file
- Ensure the Maven shade plugin is configured correctly

### Release Not Created

- Verify the tag was pushed successfully
- Check that the workflow has permission to create releases
- Ensure `GITHUB_TOKEN` is available (automatically provided by GitHub Actions)
