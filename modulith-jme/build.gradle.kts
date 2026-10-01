dependencies {
    api(project(":modulith-core"))
    compileOnly("org.jmonkeyengine:jme3-core:3.7.0-stable")
    testImplementation("org.jmonkeyengine:jme3-core:3.7.0-stable")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
