# bug-replication

![Build](https://github.com/matan-dynatrace/bug-replication/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Adjust the [pluginGroup](./gradle.properties) and [pluginName](./gradle.properties), as well as the [id](./src/main/resources/META-INF/plugin.xml) and [sources package](./src/main/kotlin).
- [ ] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `MARKETPLACE_ID` in the above README badges. You can obtain it once the plugin is published to JetBrains Marketplace.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.
- [ ] Configure the [CODECOV_TOKEN](https://docs.codecov.com/docs/quick-start) secret for automated test coverage reports on PRs

<!-- Plugin description -->
This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas that you have.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections. 
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "bug-replication"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/matan-dynatrace/bug-replication/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


# JCEF Bug Reproduction Plugin

This IntelliJ IDEA plugin reproduces a critical NullPointerException bug in JCEF (Java Chromium Embedded Framework) that occurs when using JCEF webviews in dialog windows.

## Bug Description

**Issue**: NullPointerException: Cannot read field "objId" because "robj" is null

This bug occurs in the JCEF remote message router implementation when:
1. Creating JCEF webviews with message routers in dialog windows
2. Opening and closing dialogs multiple times in rapid succession
3. The error originates from `RemoteMessageRouterImpl.create()` at line 38

## Reproduction Steps

1. Install and run this plugin in IntelliJ IDEA 2025.1 or later
2. Open the "MyToolWindow" tool window (should appear in the IDE sidebar)
3. Click the "Open JCEF Dialog (Bug Reproduction)" button
4. Close the dialog that opens
5. **Repeat steps 3-4 rapidly 2-3 times**
6. The NullPointerException should occur, breaking all JCEF instances in the IDE

## Expected vs Actual Behavior

- **Expected**: Dialog should open and close cleanly without errors
- **Actual**: NullPointerException occurs, breaking all JCEF webviews in the IDE until restart

## Environment

- **IDE Version**: 2025.1+
- **Platform**: Cross-platform (originally reported on macOS Sequoia 15.3.1)
- **Plugin Type**: Uses JCEF webviews with message routers in DialogWrapper

## Workaround

Add the following JVM option to disable out-of-process JCEF:
```
-Dide.browser.jcef.out-of-process.enabled=false
```

This can be added via Help → Edit Custom VM Options in IntelliJ IDEA.

## Technical Details

The bug appears to be a race condition in the JCEF remote message router when:
- Creating new JCEF instances per dialog
- Rapid creation/disposal of JCEF browsers
- Message router registration/cleanup timing

The stack trace shows the issue originates in:
```
jcef/com.jetbrains.cef.remote.router.RemoteMessageRouterImpl.create(RemoteMessageRouterImpl.java:38)
```

## Plugin Structure

- `JCEFTestDialog.kt`: Dialog containing JCEF webview with message router
- `MyToolWindowFactory.kt`: Tool window with button to trigger the reproduction
- Simple HTML page with JavaScript that communicates via JCEF message router

## Build and Run

```bash
./gradlew runIde
```

This will start a new IntelliJ IDEA instance with the plugin installed.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
