# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SSHellSpace is a Kotlin/JVM desktop SSH client built with Compose Multiplatform for Desktop. It has a terminal, a vault (SSH keys and credentials), settings, and a tabbed UI. It targets the JVM only, with no KMP targets: sources live in `src/main/kotlin` and tests in `src/test/kotlin`.

## Commands

- `./gradlew run` launches the app. `./gradlew build`, `./gradlew check` (tests + detekt), `./gradlew clean`.
- Per module: `./gradlew :feature:vault:data:test`, `./gradlew :core:data:check`, `./gradlew :core:data:detekt`.
- Single test: `./gradlew :core:data:test --tests "com.sshellspace.core.data.impl.MinaSshKeyServiceTest"` (append `.methodName`, or use a `*` pattern for backtick-named tests).
- `./gradlew graphUpdate` regenerates each module's `DependencyGraph.md` (Mermaid). The task comes from the `project.root` plugin (`build-logic/src/main/kotlin/utils/Graph.kt`).
- The project requires the JDK 25 toolchain (`jvmToolchain(25)`), which the foojay resolver auto-provisions.
- `gradle.properties` turns on both the build cache and the **configuration cache**, so build-logic changes must stay configuration-cache compatible.
- If the Kotlin compiler still runs out of memory, run `./gradlew --stop` and rebuild. Heap sizes are set in `gradle.properties`.

## Build structure

- `build-logic/` is a composite build of convention plugins: `project.root`, `project.kotlin.library`, `project.compose.library` (applies the kotlin library plugin plus Compose and the compose-unstyled components), and `project.sqldelight`. New modules apply a convention plugin instead of wiring plugins and dependencies by hand. The plugins already add coroutines, Koin, kotlinx-serialization, kotlinx-datetime, the Koin compiler plugin, detekt, and the test stack (JUnit Jupiter via `useJUnitPlatform()`, kotlin-test, mockk, coroutines-test).
- Detekt is configured from the root `detekt.yml`.
- Type-safe project accessors are enabled, so reference modules as `projects.feature.vault.ui`, never as string paths.
- All dependency versions live in `gradle/libs.versions.toml`.
- `settings.gradle.kts` also lists JetBrains' proxy of Google's repository for `androidx.*`, because Google's can return 404 from some networks.
- **After changing a dependency,** refresh the lockfile and checksums, then review the diff: `./gradlew :app:dependencies --configuration runtimeClasspath --write-locks` and `./gradlew -I gradle/resolve-all.init.gradle --write-verification-metadata sha256 resolveAllForVerification --no-configuration-cache`. The init script resolves every configuration, because plain `help` misses ones such as the hot reload agent. A build fails on any artifact missing from `gradle/verification-metadata.xml`.

## Architecture

- Clean architecture modules: `core/{common,data,domain,tabs,ui}` and `feature/{vault,terminal,settings,newtab}/{data,domain,ui}`. `app/` is the assembly root (Koin wiring, SQLite driver, tab registration, packaging).
- `core/domain` holds shared models and service interfaces (for example `SshKeyService`). `core/data` implements them with SQLDelight datasources and Apache MINA sshd (`MinaSshKeyService`). Feature `data` modules build repositories on top of the core datasources and services (for example `SshKeyRepositoryImpl`).
- UI pattern: each page has a `ViewModel` that exposes a single `uiState: StateFlow<XxxUiState>` (assembled with `combine(...).stateIn(...)`) and an `execute(action: XxxAction)` entry point that takes a sealed action type.
- The design system lives in `core/ui/.../designsystem`: theme and color tokens (`theme/AppTheme.kt`, `theme/ColorTokens.kt`) and wrapped components built on compose-unstyled.

### DI (Koin 4 + Koin compiler plugin)

- Modules are hand-written `module { }` blocks that use the DSL from `org.koin.plugin.module.dsl` (`single<T>()`, `viewModel<T>()`, `create(::fn)`), plus `bind` for interface bindings. The codebase does not use annotation-style Koin.
- Each Gradle module exposes an `XxxModule` val, and **every** one must be added to `includes(...)` in `AppModule` (`app/src/main/kotlin/com/sshellspace/KoinInit.kt`).

### SQLDelight

- `.sq` files live under each module's `src/main/sqldelight/`. Most of the schema is in `core/data`.
- The `project.sqldelight` convention sets the generated package to `com.sshellspace.<module.path.with.dots>.generated` (for example `com.sshellspace.core.data.generated`). `app` applies the raw plugin with package `com.sshellspace.generated`.
- The SQLite DB lives in the OS user-data dir (appdirs, `SSHellSpace/sshellspace.db`). The schema comes from `Database.Schema`, with `foreign_keys=true` (`app/.../config/Database.kt`).

### Tabs

- `core/tabs` (package `com.sshellspace.core.ui.tabs`) defines `Tab`, which owns a `ViewModelStore` so its ViewModels are scoped per tab, along with `TabManager`, `TabController`, and `TabsState`. `TabManagerImpl` lives in `app`.
- Fixed tabs are registered in `app/src/main/kotlin/com/sshellspace/config/Tabs.kt` (`fixedTabItems`). New-tab flows go through `NewTabDialogEvent`.

### UI conventions (core/ui)

- Use the design-system wrappers in `designsystem/components` (`TextField`, `Dropdown`, `MenuPanel`/`MenuItem`, `Tooltip`, `Button`, `IconButton`) rather than raw compose-unstyled components. The raw components draw nothing: `DropdownMenuPanel` has no background, and the `UnstyledDropdownMenu` anchor isn't clickable (only the keyboard opens it), so a custom menu needs a `Button` anchor that toggles `expanded`. Input styling is shared through `Modifier.fieldContainer(...)`.
- **Colors:**
  - Read them as `Theme[colors][token]`. Tokens live in `theme/ColorTokens.kt`; `theme/ColorSchemes.kt` (`AppColorScheme`) overrides the surface and brand tokens per scheme.
  - Use `primary` for selection and focus accents. Otherwise the color scheme has no visible effect.
- **Dark mode:**
  - `SSHellSpaceTheme` resolves `SYSTEM_DEFAULT` with Nucleus's `isSystemInDarkMode()`. Don't use Compose's `isSystemInDarkTheme()`, because it doesn't report the real OS appearance under the Tao backend.
  - The theme also installs the `TooltipHost` that `Tooltip` needs.
- **Adaptive layout** (`designsystem/adaptive/WindowClass.kt`):
  - The window minimum size is `MinWindowSize` (720×480dp).
  - `ProvideWindowClass` in `Main.kt` exposes `currentWindowClass`: `Compact` below 960dp wide, `Expanded` at 960dp and above.
  - In Compact, the vault nav shows icons only and side panels float over the content. Tall content must scroll, so it still fits at 480dp height.
- **Shortcuts:**
  - Global shortcuts are `AppShortcut` entries (`core/ui/.../shortcuts`), with default chords from `primaryChord()` (Cmd on macOS, Ctrl elsewhere).
  - Actions are registered in `app/.../config/Shortcuts.kt` (`Koin.registerShortcuts()`).
  - `ShortcutHandler` is wired into `DecoratedWindow(onPreviewKeyEvent = ...)`. It reads user overrides from `CorePreferencesRepository`, so shortcuts can be rebound in Settings.

### Preferences

- `CorePreferencesRepository` (theme, color scheme, shortcuts) and `TerminalPreferencesRepository` (default shell) each store one `@Serializable` object as JSON in the SQLDelight preferences table, keyed by its qualified class name.
- Implementations keep a **single** `stateIn` StateFlow and build updates from `preference.value.copy(...)`. If you create a new `stateIn` per `get()` call, it returns the defaults, not the stored value.
- The user-facing Settings UI is the **Settings page inside the Vault tab** (`feature/vault/ui/.../settings`). `feature/settings` is currently only a placeholder tab.

### Vaults

- Connections, SSH keys and port forwards belong to an encrypted vault. The app starts on `VaultGateScreen` (`feature/vault/ui/.../gate`) and shows tabs only once `VaultManager.activeVault` is set. Preferences (theme, shortcuts, terminal) stay in the unencrypted `sshellspace.db`, because the theme has to apply to the gate.
- Each vault is one file, `vaults/<id>.sshv`, in the user-data dir (`core/data/.../vault/VaultFile.kt` documents the format). A random vault key encrypts the SQLite image with AES-256-GCM, and each `KeySlot` wraps that key for one way of authenticating. Password slots use Argon2id. A vault with no password has a `KeySlot.Unprotected`, with the vault key in the header in the clear, so its file protects nothing. Password strength only ever warns (`NewPasswordFields`); only a mismatched confirmation blocks. **To add biometric unlock**, add a `VaultCredential` case and a `KeySlot` case, with no change to the data encryption.
- An unlocked vault's database exists only in memory (`SQLiteConnection.serialize`/`deserialize`). `VaultSqlDriver` re-encrypts it to disk after every write, from `notifyListeners`. Vault datasources are singletons reading through that driver, which throws while locked, so code touching vault data must only run while a vault is unlocked. Reconnecting therefore waits for an unlock.
- A lock normally only hides terminals and port forwards: `VaultSessions` (app) clears the fixed tabs' ViewModels, which hold vault data, and leaves sessions running. Opening a different vault, or deleting this one, closes them. `TerminalPreferences.keepSessionsWhenLocked` turns this off, so every lock closes them.
- Schema migrations run per vault when it unlocks, in `OpenVault.load`. The same `Database` schema is used for vaults and the app database, so each has empty tables it doesn't use.
- Export copies the already encrypted file. Import treats the file as hostile even when the password is right: `VaultFile` caps its size, key slots and Argon2 costs, and `loadImage` refuses a damaged database or one with triggers or views.
- `VaultAutoLock` locks after the unlocked vault's idle time and on waking from sleep. `VaultPreferencesRepository` keeps it per vault as `VaultPreference.autoLockMinutes` (0 turns it off), inside the vault. A vault with none, which includes every imported one since import clears that table, uses `CorePreference.autoLockMinutes` (default 15), so a crafted file can't turn its own auto-lock off. Only key and pointer input from `Main.kt` count as activity. The JVM has no portable way to detect the OS screen locking.
- The gate (`VaultGateScreen`) only unlocks, creates and imports. Changing a password, export and delete live in Settings > Vault, so they need the vault unlocked. Forgetting a password therefore means the vault can't be deleted from the app.
- Changing a password (`changeCredential`) re-encrypts under a new vault key. Saved passwords in the connection form can't be revealed; only newly typed ones can.
- `SessionRestore` (app) saves a vault's open tabs in that vault's `preferences_table` through `VaultPreferencesDatasource`, and reopens them when a vault unlocks with no tabs open, such as after a restart. Import deletes that table's contents, and a saved local shell only reopens if this computer offers that shell: otherwise a crafted vault file could run a program on unlock with no click.
- Tests must never point `VaultManagerImpl` at the real user-data dir: creating a vault with `moveUnencryptedData` erases those tables from `sshellspace.db`.

### Hosts, snippets and quick connect

- `SshConnectionOptions.group` and `tags` organize the Vault page. They live in the `options` JSON column, so they needed no migration.
- Snippets (`Snippet`, `snippets` table, Snippets page) are per vault. `QuickConnectPalette` (feature/newtab, `AppShortcut.QuickConnect`) searches hosts and snippets. A snippet goes to the current tab through `TabController.setTextInput`, which each terminal ViewModel sets while it has a live terminal.
- Dialog ViewModels are scoped to one unlock in `Main.kt`, because they hold vault data.
- `SshConfigImporter` reads `~/.ssh/config` only when the user asks to import. The app still never reads it while connecting.

### Custom themes

- A theme file is JSON with `name`, `dark` and `light` (`ThemePalette`, the app tokens) and `terminal` (`TerminalColors`); every token is required. `core/data/src/test/resources/custom-theme.json` is a complete example. `parseUserTheme` accepts `#RRGGBB`, `#AARRGGBB` and `0xAARRGGBB`, and stores `0xAARRGGBB` only: BossTerm parses a six-digit value as fully transparent.
- Imported themes live in `CorePreference`. A theme's name is its identity, so re-importing a name replaces that theme.
- Terminal colors resolve in `resolveTerminalColors`: the theme picked in `TerminalPreferences.terminalThemeName`, else the active imported theme's `terminal`, else colors derived from the app tokens (`deriveTerminalColors`).
- Themes have to stay readable: `parseUserTheme` requires opaque surfaces, text and error colors, plus minimum contrast, so a theme can't hide the host key warning.
- BossTerm renders from its global `ThemeManager`, not from the settings passed to `EmbeddableTerminal`, so `ApplyTerminalTheme` goes through it. That persists the colors to `~/.bossterm/settings.json`.

### SSH connections

- Saved connections (`SshConnection` in core/domain) live in the `ssh_connections` table. `auth` and `options` are stored as JSON columns, and the Vault page (`feature/vault/ui/.../connections`) edits them.
- **Schema changes need a `.sqm` migration.** Users already have databases, so a new table also needs a numbered migration such as `core/data/.../1.sqm`.
- `MinaSshConnectionService` (feature/terminal/data) connects to a saved connection:
  - **Jump hosts:** it resolves the jump-host chain and tunnels each hop through the previous one with a local port forward.
  - **Per-connection settings:** a single shared `SshClient` from `createSshClient()` serves every connection. Each connection's settings go into a `ConnectionContext`, attached as the MINA connection context. `SessionConfigurer`, a `SessionListener`, applies that context to the session in `sessionCreated`, which runs before key exchange.
  - **Auth method choice:** `PREFERRED_AUTHS` can't be set per session, because MINA reads it in the session constructor. Auth methods are limited instead through the identities and prompts each session gets.
  - **Host keys:** host keys are checked against `~/.ssh/known_hosts` by `PromptingKnownHostsVerifier`. It matches by the saved host name, even when going through a tunnel. A changed key needs the host name typed to accept, removes the saved password from that attempt, and is refused outright with `strictHostKeyChecking`.
  - **Algorithms:** `createSshClient` removes CBC ciphers, SHA-1 MACs and `ssh-rsa` signatures, so very old servers can't connect.
  - **SSH agent:** the agent client (`ssh/agent/SystemSshAgent.kt`) uses JDK Unix sockets or Windows named pipes. MINA's `agent.unix` client needs Tomcat's native APR library, so it isn't used.
  - **`~/.ssh/config` is ignored on purpose** when connecting. Importing from it is a separate, one-time copy.
  - **Reconnecting:** `SshTerminalTabViewModel` retries when `waitForExit` returns `SESSION_LOST_EXIT_CODE` (no exit status), never after a normal exit. A replaced terminal's late exit callback is ignored.
- User questions (host key, password, passphrase, 2FA) go through `SshPrompter`. Text a server supplies goes through `fromServer` and is marked as coming from the server, since it could otherwise pose as the app asking for the vault password.
  - `SshTerminalTabViewModel` implements it by showing a prompt state and waiting for the answer.
  - MINA's callbacks are blocking, so `ConnectionContext.ask` uses `runBlocking` on the MINA thread.
- The SSH tab hands BossTerm a custom `PlatformServices` (`SshPlatformServices`) whose `spawnProcess` opens a shell channel in place of a local PTY.

### Port forwarding

- Saved forwards (`PortForward` in core/domain, `port_forwards` table) reference a connection by id. The table has **no foreign key**, because the `ssh_connections` upsert is `INSERT OR REPLACE` and a cascade would delete forwards on every connection edit. `SshConnectionRepositoryImpl.delete` removes them instead.
- `MinaPortForwardingService` (feature/terminal/data, Koin `single`) runs forwards via `ShellSession.Ssh.startForward`. Forwards on the same connection share one session, which closes when the last forward stops. A dropped session marks its forwards failed, and deleted forwards are stopped.
- The client's `forwardingFilter` must allow TCP. MINA's default rejects the server's `forwarded-tcpip` channels, which breaks remote (`-R`) forwards.
- The Port Forwarding page (`feature/vault/ui/.../portforwarding`) is the `SshPrompter` for starting forwards. Its login questions render with the shared cards in `feature/terminal/ui/.../ssh/SshPromptCards.kt`. Stopping a forward mid-login answers `null`, because cancelling the coroutine doesn't unblock the MINA thread.
- `MinaSshConnectionServiceTest` runs real connections against an embedded MINA `SshServer`, and `MinaSftpSessionTest` does the same for file operations. The agent test uses a real `ssh-agent` and is skipped when OpenSSH isn't installed.

### SFTP browser

- A file browser is its own tab type (`SftpParams`, `SftpTabFactory`), opened from a connection's folder button in the Vault page or the new tab dialog. It lives in the terminal feature because it needs `ShellSession.Ssh`, and `ShellSession.Ssh.openSftp()` returns the `SftpSession` that `MinaSftpSession` (feature/terminal/data) implements. `SftpBrowserViewModel` opens its own SSH session, so each tab logs in separately, and closing the tab stops its transfers. Tabs are saved and restored like terminals (`SavedTab.Sftp`).
- **Everything a server sends is hostile.** `readDirectory` drops names that could leave the folder (a listed `..` would make a recursive delete remove the parent). `isSafeLocalName` guards every local path, including names that only do damage on Windows (`a\b`, `a:b`, `CON`). Names on screen go through `printableRemoteText`, because bidirectional overrides can disguise a file type. Listings, tree depth and file counts are capped.
- **Links aren't followed when walking a folder**, in either direction or when deleting: a link could reach outside what the user chose. A link the user picked directly is followed, using `stat`, since a listing only gives the link's own size and mode.
- **Modes stay private.** Downloads arrive in a private temp file beside the target, then move to the real name once whole, with the remote mode masked by 755. New uploads get the local mode before any data is written, because the server's default would make a private key readable. MINA's `open` can't send a mode, hence the open, `setStat`, then write sequence.
- Conflicts are decided by the ViewModel (`SftpDialog.Conflict`), and `replace = false` still fails rather than overwriting if a file appears in the meantime.
- Rows use the desktop `Modifier.onClick`. Each handler must match only its own modifier keys: one matching any keys takes every click from the others.

### Terminal

- **BossTerm's defaults are for its own app, not this one.** `EmbeddedTerminalSettings` turns off kitty file reads, AI assistant interception, MCP, the daemon and voice. `handleTerminalLink` only opens http(s) links, because BossTerm would pass link text to `cmd /c start`, `open` or `xdg-open`. Re-check both, and that nothing starts listening on a port, when upgrading BossTerm.
- Terminal emulators implement the `Terminal` interface (`feature/terminal/ui/.../screen/emulators/Terminal.kt`). BossTerm is the concrete emulator. `BossTermSshProcessHandleAdapter` bridges it to Apache MINA sshd (ssh-common/sftp/scp) for remote sessions, and pty4j handles local PTYs.

### Packaging

- Release signing isn't configured. It needs your own certificates.
- The nucleus plugin packages the app (`app/build.gradle.kts`): main class `com.sshellspace.MainKt`, Tao backend, GraalVM native image enabled, and Dmg/Msi/AppImage target formats.

## Tests

- Tests exist in `core/data`, `feature/vault/data`, `feature/vault/ui`, `feature/terminal/domain`, `feature/terminal/data` and `feature/terminal/ui`. They use `kotlin.test` assertions, mockk, and `runTest`, with backtick-named test functions.
