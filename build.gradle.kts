/*
 * Copyright 2024 Karma Krafts & associates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.jvm.tasks.Jar
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

plugins {
    eclipse
    idea
    `maven-publish`
    alias(libs.plugins.forgeGradle)
    alias(libs.plugins.mixinGradle)
    alias(libs.plugins.librarian)
    alias(libs.plugins.curseforgeGradle)
    alias(libs.plugins.minotaur)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val projectPath: Path = project.projectDir.toPath()
val buildConfig: Properties = Properties().apply {
    (projectPath / "build.properties").inputStream(StandardOpenOption.READ).use(::load)
}
val modId: String = buildConfig["mod_id"] as String
val license: String = buildConfig["license"] as String
val mcVersion: String = libs.versions.minecraft.get()
val buildNumber: Int = System.getenv("CI_PIPELINE_IID")?.toIntOrNull() ?: 0
val buildTime: Instant = Instant.now()

version = "${libs.versions.unifyEverything.get()}.$buildNumber"
group = buildConfig["group"] as String
base.archivesName = "$modId-$mcVersion"

// Source sets
sourceSets.main {
    java.srcDirs(projectPath / "src" / "main" / "java", projectPath / "src" / "main" / "kotlin")
    resources.srcDirs(projectPath / "src" / "generated" / "resources", projectPath / "src" / "main" / "resources")
}
val mainSourceSet by sourceSets.main

configurations {
    val minecraft by getting
    annotationProcessor {
        extendsFrom(minecraft)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    google()
    maven("https://thedarkcolour.github.io/KotlinForForge")
    maven("https://maven.blamejared.com")
    maven("https://cursemaven.com")
    maven("https://dvs1.progwml6.com/files/maven")
}

dependencies {
    minecraft(libs.minecraftForge)

    //implementation(fg.deobf(libs.embeddium.get().toString()))
    //implementation(fg.deobf(libs.oculus.get().toString()))

    implementation(fg.deobf(libs.architectury.get().toString()))
    implementation(fg.deobf(libs.almostUnified.forge.get().toString()))

    compileOnly(fg.deobf(libs.jei.forge.api.get().toString()))
    runtimeOnly(fg.deobf(libs.jei.forge.core.get().toString()))
    runtimeOnly(fg.deobf(libs.cofhCore.get().toString()))
    runtimeOnly(fg.deobf(libs.thermal.foundation.get().toString()))
    runtimeOnly(fg.deobf(libs.railcraft.get().toString()))

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

minecraft {
    mappings(buildConfig["mappings_channel"] as String, "${libs.versions.mappings.get()}-$mcVersion")
    accessTransformer(projectPath / "src" / "main" / "resources" / "META-INF" / "accesstransformer.cfg")
    copyIdeResources = true
    runs {
        val client by creating {
            property("forge.enabledGameTestNamespaces", modId)
        }
        val server by creating {
            property("forge.enabledGameTestNamespaces", modId)
            args("--nogui")
        }
        val gameTestServer by creating {
            property("forge.enabledGameTestNamespaces", modId)
        }
        val data by creating
        val clientAlt by creating {
            parent(client)
            args("--username", "Dev2")
        }
        configureEach {
            workingDirectory(project.file("run"))
            properties(mapOf("forge.logging.markers" to "LOADING,CORE",
                "forge.logging.console.level" to "debug",
                "mixin.debug" to "true",
                "mixin.debug.dumpTargetOnFailure" to "true",
                "mixin.debug.verbose" to "true",
                "mixin.env.remapRefFile" to "true",
                "mixin.env.refMapRemappingFile" to (projectPath / "build" / "createSrgToMcp" / "output.srg").pathString))
            jvmArgs("-Xms512M", "-Xmx4096M")
            mods {
                create(modId) {
                    sources(mainSourceSet)
                }
            }
        }
    }
}

mixin {
    add(mainSourceSet, "mixins.$modId.refmap.json")
    config("mixins.$modId.common.json")
}

fun Manifest.applyCommonManifest() {
    attributes.apply {
        this["MixinConfigs"] = "mixins.$modId.client.json"
        this["Specification-Title"] = modId
        this["Specification-Vendor"] = "Karma Krafts"
        this["Specification-Version"] = version
        this["Implementation-Title"] = modId
        this["Implementation-Vendor"] = "Karma Krafts"
        this["Implementation-Version"] = version
        this["Implementation-Timestamp"] = SimpleDateFormat.getDateTimeInstance().format(Date.from(buildTime))
    }
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        val forgeVersion = libs.versions.forge.get()
        val properties = mapOf("minecraft_version" to mcVersion,
            "minecraft_version_range" to "[$mcVersion]",
            "forge_version" to forgeVersion,
            "forge_version_range" to "[$forgeVersion,)",
            "loader_version_range" to forgeVersion.substringBefore("."),
            "mod_id" to modId,
            "mod_name" to buildConfig["mod_name"] as String,
            "mod_license" to license,
            "mod_version" to version,
            "mod_authors" to "Karma Krafts",
            "mod_description" to "An addon for Almost Unified/Forge that adds inventory and loot table unification.")
        inputs.properties(properties)
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
            expand(properties)
        }
    }
}

System.getenv("CI_MODRINTH_TOKEN")?.let { token ->
    modrinth {
        this.token = token
        projectId = project.name
        versionType = "release"
        uploadFile = tasks.jar.get()
        changelog = "See changes until ${System.getenv("CI_PROJECT_URL")}/-/tree/${System.getenv("CI_COMMIT_SHA")}"
        gameVersions.add(libs.versions.minecraft.get())
        loaders.add("forge")
        dependencies {
            required.project("almost-unified")
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.release = 17
    }
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    System.getenv("CI_CURSEFORGE_TOKEN")?.let { token ->
        create<TaskPublishCurseForge>("publishToCurseForge") {
            apiToken = token
            upload(1119261, jar) {
                addJavaVersion("Java 17", "Java 18", "Java 19", "Java 20", "Java 21")
                addGameVersion(libs.versions.minecraft.get())
                addEnvironment("Client", "Server")
                addModLoader("Forge")
                addRelation("almost-unified", "requiredDependency")
                releaseType = "release"
                changelog = "See changes until ${System.getenv("CI_PROJECT_URL")}/-/tree/${System.getenv("CI_COMMIT_SHA")}"
            }
        }
    }

    val archiveName = project.base.archivesName.get()

    publishing {
        repositories {
            System.getenv("CI_API_V4_URL")?.let { apiUrl ->
                maven {
                    url = uri("$apiUrl/projects/${System.getenv("CI_PROJECT_ID")}/packages/maven")
                    name = "GitLab"
                    credentials(HttpHeaderCredentials::class) {
                        name = "Job-Token"
                        value = System.getenv("CI_JOB_TOKEN")
                    }
                    authentication {
                        create("header", HttpHeaderAuthentication::class)
                    }
                }
            }
        }

        publications {
            create<MavenPublication>(modId) {
                groupId = project.group as String
                artifactId = archiveName
                version = project.version as String

                artifact(jar)

                pom {
                    name = artifactId
                    url = "https://git.karmakrafts.dev/kk/mc-projects/$modId"
                    scm {
                        url = this@pom.url
                    }
                    issueManagement {
                        system = "gitlab"
                        url = "https://git.karmakrafts.dev/kk/mc-projects/$modId/issues"
                    }
                    licenses {
                        license {
                            name = license
                            distribution = "repo"
                        }
                    }
                    developers {
                        developer {
                            id = "kitsunealex"
                            name = "KitsuneAlex"
                            url = "https://git.karmakrafts.dev/KitsuneAlex"
                        }
                    }
                }
            }
        }
    }
}