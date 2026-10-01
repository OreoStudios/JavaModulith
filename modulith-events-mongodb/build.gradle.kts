dependencies {
    api(project(":modulith-core"))
    api("org.mongodb:mongodb-driver-sync:5.13.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
