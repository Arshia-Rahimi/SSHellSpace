# SSHellSpace

[![CI](https://github.com/Arshia-Rahimi/SSHellSpace/actions/workflows/ci.yml/badge.svg)](https://github.com/Arshia-Rahimi/SSHellSpace/actions/workflows/ci.yml)

A desktop SSH client for macOS, Windows and Linux, built with Kotlin and Compose Multiplatform.

## Features

- Tabbed terminals, both SSH and local shells
- Encrypted vaults for connections, SSH keys, snippets and known hosts
- Jump hosts, port forwarding (local and remote) and SSH agent support
- An SFTP file browser for remote servers and this computer
- Quick connect, rebindable shortcuts and custom themes
- Signed automatic updates

## Download

Get the latest build from [Releases](https://github.com/Arshia-Rahimi/SSHellSpace/releases).

## Building

You need JDK 17 or newer to run Gradle. The build downloads JDK 25 for itself.

```sh
./gradlew run      # launch the app
./gradlew check    # tests and detekt
```

Package for the current OS with `./gradlew :app:packageDmg`, `:app:packageNsis` or `:app:packageAppImage`. The output goes to `app/build/compose/binaries/main`.

## License

[GPL-3.0](LICENSE)
