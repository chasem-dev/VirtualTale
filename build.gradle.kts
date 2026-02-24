import org.gradle.api.file.DuplicatesStrategy

plugins {
    `maven-publish`
    idea
    id("hytale-mod") version "0.+"
}

group = "dev.chasem.hg"
version = findProperty("plugin_version")?.toString() ?: "0.1.0"
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
    maven("https://maven.hytale-mods.dev/") {
        name = "HytaleMods"
    }
    maven("https://maven.hytale.com/release") {
        name = "hytale-release"
    }
    maven("https://maven.hytale.com/pre-release") {
        name = "hytale-pre-release"
    }
    maven("https://jitpack.io")
}

dependencies {
    // coffee-gb (headless Game Boy emulator)
    implementation("com.github.trekawek:coffee-gb:coffee-gb-1.5.1")

    // Hytale Server API
    compileOnly(libs.hytale.server)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)
    implementation(libs.gson)

    // Testing
    testImplementation(libs.hytale.server)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "plugin_group" to findProperty("vt_plugin_group"),
        "plugin_maven_group" to project.group,
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to libs.versions.hytale.server.get(),

        "plugin_description" to findProperty("vt_plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("vt_plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = project.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

// Fat jar - exclude org.bson since Hytale provides it in the app classloader
val fatJar by tasks.registering(Jar::class) {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") && !it.path.contains("HytaleServer") }
            .map { zipTree(it) }
    }) {
        exclude("org/bson/**")
    }
}

tasks.named("build") {
    dependsOn(fatJar)
}

publishing {
    repositories {
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
