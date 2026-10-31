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
- If the Kotlin compiler fails with "Java heap space", run `./gradlew --stop` and rebuild.

## Build structure

- `build-logic/` is a composite build of convention plugins: `project.root`, `project.kotlin.library`, `project.compose.library` (applies the kotlin library plugin plus Compose and the compose-unstyled components), and `project.sqldelight`. New modules apply a convention plugin instead of wiring plugins and dependencies by hand. The plugins already add coroutines, Koin, kotlinx-serialization, kotlinx-datetime, the Koin compiler plugin, detekt, and the test stack (JUnit Jupiter via `useJUnitPlatform()`, kotlin-test, mockk, coroutines-test).
- Detekt is configured from the root `detekt.yml`.
- Type-safe project accessors are enabled, so reference modules as `projects.feature.vault.ui`, never as string paths.
- All dependency versions live in `gradle/libs.versions.toml`.

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

### SSH connections

- Saved connections (`SshConnection` in core/domain) live in the `ssh_connections` table. `auth` and `options` are stored as JSON columns, and the Vault page (`feature/vault/ui/.../connections`) edits them.
- **Schema changes need a `.sqm` migration.** Users already have databases, so a new table also needs a numbered migration such as `core/data/.../1.sqm`.
- `MinaSshConnectionService` (feature/terminal/data) connects to a saved connection:
  - **Jump hosts:** it resolves the jump-host chain and tunnels each hop through the previous one with a local port forward.
  - **Per-connection settings:** a single shared `SshClient` from `createSshClient()` serves every connection. Each connection's settings go into a `ConnectionContext`, attached as the MINA connection context. `SessionConfigurer`, a `SessionListener`, applies that context to the session in `sessionCreated`, which runs before key exchange.
  - **Auth method choice:** `PREFERRED_AUTHS` can't be set per session, because MINA reads it in the session constructor. Auth methods are limited instead through the identities and prompts each session gets.
  - **Host keys:** host keys are checked against `~/.ssh/known_hosts` by `PromptingKnownHostsVerifier`. It matches by the saved host name, even when going through a tunnel.
  - **SSH agent:** the agent client (`ssh/agent/SystemSshAgent.kt`) uses JDK Unix sockets or Windows named pipes. MINA's `agent.unix` client needs Tomcat's native APR library, so it isn't used.
  - **`~/.ssh/config` is ignored on purpose.**
- User questions (host key, password, passphrase, 2FA) go through `SshPrompter`.
  - `SshTerminalTabViewModel` implements it by showing a prompt state and waiting for the answer.
  - MINA's callbacks are blocking, so `ConnectionContext.ask` uses `runBlocking` on the MINA thread.
- The SSH tab hands BossTerm a custom `PlatformServices` (`SshPlatformServices`) whose `spawnProcess` opens a shell channel in place of a local PTY.

### Port forwarding

- Saved forwards (`PortForward` in core/domain, `port_forwards` table) reference a connection by id. The table has **no foreign key**, because the `ssh_connections` upsert is `INSERT OR REPLACE` and a cascade would delete forwards on every connection edit. `SshConnectionRepositoryImpl.delete` removes them instead.
- `MinaPortForwardingService` (feature/terminal/data, Koin `single`) runs forwards via `ShellSession.Ssh.startForward`. Forwards on the same connection share one session, which closes when the last forward stops. A dropped session marks its forwards failed, and deleted forwards are stopped.
- The client's `forwardingFilter` must allow TCP. MINA's default rejects the server's `forwarded-tcpip` channels, which breaks remote (`-R`) forwards.
- The Port Forwarding page (`feature/vault/ui/.../portforwarding`) is the `SshPrompter` for starting forwards. Its login questions render with the shared cards in `feature/terminal/ui/.../ssh/SshPromptCards.kt`. Stopping a forward mid-login answers `null`, because cancelling the coroutine doesn't unblock the MINA thread.
- `MinaSshConnectionServiceTest` runs real connections against an embedded MINA `SshServer`. The agent test uses a real `ssh-agent` and is skipped when OpenSSH isn't installed.

### Terminal

- Terminal emulators implement the `Terminal` interface (`feature/terminal/ui/.../screen/emulators/Terminal.kt`). BossTerm is the concrete emulator. `BossTermSshProcessHandleAdapter` bridges it to Apache MINA sshd (ssh-common/sftp/scp) for remote sessions, and pty4j handles local PTYs.

### Packaging

- The nucleus plugin packages the app (`app/build.gradle.kts`): main class `com.sshellspace.MainKt`, Tao backend, GraalVM native image enabled, and Dmg/Msi/AppImage target formats.

## Tests

- Tests exist in `core/data`, `feature/vault/data`, `feature/vault/ui`, `feature/terminal/data` and `feature/terminal/ui`. They use `kotlin.test` assertions, mockk, and `runTest`, with backtick-named test functions.
