plugins {
  `maven-publish`
  signing
}

publishing {
  // Configure all publications
  publications.withType<MavenPublication> {
    // Provide artifacts information required by Maven Central
    pom {
      name.set("HighKT")
      description.set("A Kotlin Compiler Plugin enabling the usage of Higher-Kinded Types without resorting to inelegant encodings")
      url.set("https://github.com/kyay10/highkt")

      licenses {
        license {
          name.set("Apache-2.0")
          url.set("https://opensource.org/license/apache-2-0")
        }
      }
      developers {
        developer {
          id.set("kyay10")
          name.set("Youssef Shoaib")
        }
      }
      scm {
        url.set("https://github.com/kyay10/highkt")
      }
    }
  }
}

signing {
  if (project.hasProperty("signing.gnupg.keyName")) {
    useGpgCmd()
    sign(publishing.publications)
  }
}
