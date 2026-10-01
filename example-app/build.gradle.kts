plugins { application }
dependencies {
    implementation(project(":modulith-core"))
    annotationProcessor(project(":modulith-processor"))
}
application { mainClass.set("dev.oreo.javamodulith.packagedexample.ExampleApplication") }
