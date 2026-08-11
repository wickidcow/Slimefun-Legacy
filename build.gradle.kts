import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gorylenko.GitPropertiesPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.language.jvm.tasks.ProcessResources
import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
    java
    `maven-publish`
    alias(libs.plugins.spotless)
    alias(libs.plugins.shadow)
    alias(libs.plugins.git.properties)
}
group = "com.github.slimefun"
version = resolveVersion()

java {
    // Build on Java 25 for Paper/MockBukkit 26.x compatibility while still
    // emitting Java 21 bytecode for existing addon/server deployments.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(21)
    if (providers.gradleProperty("slimefunDeprecationReport").isPresent) {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}
tasks.compileTestJava {
    // Keep test compilation on the same bytecode/API level as production.
    // This also avoids the Java 25 compiler edge case seen while completing
    // Paper classes whose signatures contain external nullability annotations.
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    // Phase 1L: release artifacts must not depend on filesystem traversal order
    // or the wall-clock timestamps of files on the build runner.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.norain.city/snapshots")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi")
    maven("https://nexus.neetgames.com/repository/maven-public")
    maven("https://repo.walshy.dev/public")
    maven("https://repo.codemc.io/repository/maven-public/")
}
val supportedPaperApiVersion = libs.versions.paperApi.get()
val selectedPaperApiVersion = providers.gradleProperty("paperApiVersion").orElse(supportedPaperApiVersion)

dependencies {
    compileOnly("io.papermc.paper:paper-api:${selectedPaperApiVersion.get()}")
    compileOnly(libs.jsr305)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    compileOnly(libs.log4j.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockbukkit)
    testImplementation("io.papermc.paper:paper-api:${selectedPaperApiVersion.get()}")
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.jsr305)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(libs.dough.api)
    implementation(libs.unirest.java) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly(libs.worldedit.core) { exclude(group = "*", module = "*") }
    compileOnly(libs.worldedit.bukkit) { exclude(group = "*", module = "*") }
    compileOnly(libs.mcmmo) { exclude(group = "*", module = "*") }
    compileOnly(libs.placeholderapi) { exclude(group = "*", module = "*") }
    compileOnly(libs.clearlag.core) { exclude(group = "*", module = "*") }
    compileOnly(libs.itemsadder.api) { exclude(group = "*", module = "*") }
    compileOnly(libs.orebfuscator.api) { exclude(group = "*", module = "*") }
    compileOnly(libs.vault.api) { exclude(group = "*", module = "*") }
    compileOnly(libs.authlib) { exclude(group = "*", module = "*") }
    implementation(libs.commons.lang)
    implementation(libs.slimefun.comp.lib)
    implementation(libs.guizhanlib.updater)
    implementation(libs.guizhanlib.minecraft)
}

tasks.jar {
    enabled = false
}

sourceSets.main {
    java.exclude("**/package-info.java")
}
tasks.test {
    useJUnitPlatform()
    // SQLite JDBC loads a native library during storage tests. Java 25 requires explicit native access.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    findProperty("slimefunRealDatabase")?.toString()?.let {
        systemProperty("slimefun.realDatabase", it)
    }
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}

spotless {
    java {
        palantirJavaFormat("2.90.0").style("PALANTIR")
        removeUnusedImports()
        formatAnnotations()
    }
}
val buildVersion = version.toString()
// SOURCE_DATE_EPOCH is set to the source commit timestamp by release CI. Local
// builds use the Unix epoch rather than the current wall clock so git.properties
// cannot make otherwise identical JARs differ.
val sourceDateEpoch = providers.environmentVariable("SOURCE_DATE_EPOCH").orElse("0").get().toLongOrNull() ?: 0L
val sourceCommit = providers.environmentVariable("SOURCE_COMMIT").orElse("unknown").get()
val gitBuildTime: String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(sourceDateEpoch))
configure<GitPropertiesPluginExtension> {
    keys = listOf(
        "git.build.time",
        "git.build.version",
        "git.source.commit",
        "git.commit.id.abbrev",
        "git.commit.id",
        "git.branch",
    )
    customProperty("git.build.version", buildVersion)
    customProperty("git.build.time", gitBuildTime)
    customProperty("git.source.commit", sourceCommit)
    gitPropertiesName = "git.properties"
    gitPropertiesResourceDir = layout.buildDirectory.dir("generated/git-properties").get().asFile
}
tasks.named<ProcessResources>("processResources") {
    dependsOn(tasks.named("generateGitProperties"))
    val pluginVersion = buildVersion
    inputs.property("version", pluginVersion)
    from("compatibility/release-baselines.json") {
        into("compatibility")
    }
    filesMatching("plugin.yml") {
        expand(mapOf("version" to pluginVersion))
    }
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("generateGitProperties"))
}
tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("Slimefun")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    relocate("io.github.bakedlibs.dough", "io.github.thebusybiscuit.slimefun4.libraries.dough")
    relocate("io.papermc.lib", "io.github.thebusybiscuit.slimefun4.libraries.paperlib")
    relocate("kong.unirest", "io.github.thebusybiscuit.slimefun4.libraries.unirest")
    relocate("org.apache.commons.lang", "io.github.thebusybiscuit.slimefun4.libraries.commons.lang")
    relocate("net.guizhanss.guizhanlib", "io.github.thebusybiscuit.slimefun4.libraries.guizhanlib")
    relocate("org.bstats", "io.github.thebusybiscuit.slimefun4.libraries.bstats")
    /**exclude {
        it.path == "META-INF" || it.path.startsWith("META-INF/")
    }*/
}
tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("shadowJar"))
            artifact(tasks.named("sourcesJar"))
            groupId = project.group.toString()
            artifactId = "Slimefun"
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            url = if (project.version.toString().contains("SNAPSHOT")) {
                uri("https://maven.norain.city/snapshots")
            } else {
                uri("https://maven.norain.city/releases")
            }
            credentials {
                username = System.getenv("MAVEN_ACCOUNT")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
fun Project.resolveVersion(): String {
    findProperty("projectVersion")?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    return "Legacy-SNAPSHOT"
}
