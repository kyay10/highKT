plugins {
  id("io.github.gradle-nexus.publish-plugin")
}

allprojects {
  group = "io.github.kyay10.highkt"
  version = "0.0.1"
}

nexusPublishing {
  repositories {
    // see https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
    sonatype {
      nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
      snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
      stagingProfileId.set("io.github.kyay10")
    }
  }
}