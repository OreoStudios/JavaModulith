dependencies {
    api(project(":modulith-core"))

    // Database drivers are intentionally NOT part of this artifact's runtime dependencies.
    // Consumers provide only the JDBC driver they actually need.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    testRuntimeOnly("org.postgresql:postgresql:42.7.5")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    testRuntimeOnly("com.mysql:mysql-connector-j:9.2.0")
}
