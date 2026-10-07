import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

allprojects {
    group = providers.gradleProperty("group").get()
    version = if (System.getenv("JITPACK") == "true" && !System.getenv("VERSION").isNullOrBlank()) System.getenv("VERSION") else providers.gradleProperty("version").get()
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        // Compile with the running JDK targeting Java 21 bytecode (no separate toolchain needed).
        withSourcesJar()
        withJavadocJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    if (name != "example-app") {
        apply(plugin = "maven-publish")
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    pom {
                        name.set("JavaModulith - ${project.name}")
                        description.set("Platform-independent Java 21 modular application framework")
                        url.set("https://github.com/el211/JavaModulith")
                        licenses { license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                        } }
                        scm {
                            url.set("https://github.com/OreoStudios/JavaModulith")
                            connection.set("scm:git:https://github.com/OreoStudios/JavaModulith.git")
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "oreostudioslib"
                    url = uri("https://maven.oreostudios.fr/oreostudioslib")
                    credentials {
                        username = System.getenv("OREO_REPO_USER")
                        password = System.getenv("OREO_REPO_PASS")
                    }
                }
            }
        }
    }
}
