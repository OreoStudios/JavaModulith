dependencies {
    api(project(":modulith-core"))
    compileOnly("com.badlogicgames.gdx:gdx:1.14.2")
    testImplementation("com.badlogicgames.gdx:gdx:1.14.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
