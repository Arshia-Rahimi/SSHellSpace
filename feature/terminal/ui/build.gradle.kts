import com.github.gradle.node.npm.task.NpmTask

plugins {
    alias(libs.plugins.project.compose.library)
    alias(libs.plugins.node)
}

dependencies {
    implementation(projects.core.ui.tabs)
    implementation(projects.feature.terminal.domain)
    //
    implementation(libs.compose.webview)
    implementation(libs.pty4j)
}

node {
    version.set("24.19.0")
    npmVersion.set("12.0.2")
    download.set(true)
    nodeProjectDir.set(file("xterm"))
}

tasks.register<NpmTask>("npmCI") {
    description = "npm ci"
    args.set(listOf("ci"))
    inputs.file("xterm/package.json")
    inputs.file("xterm/package-lock.json")
    outputs.dir("xterm/node_modules")
}

tasks.register<NpmTask>("npmBuild") {
    description = "npm build"
    dependsOn(tasks.named("npmCI"))
    args.set(listOf("run", "build"))
    inputs.dir("xterm/src")
    inputs.file("xterm/package.json")
    inputs.file("xterm/package-lock.json")
    outputs.dir("xterm/dist")
}

tasks.register<Copy>("copyTerminalBundle") {
    description = "copyXtermBundle"
    dependsOn("npmBuild")
    from("xterm/dist")
    into("src/commonMain/composeResources/files/xterm")
}

tasks.named("generateComposeResClass") {
    dependsOn("copyTerminalBundle")
}
