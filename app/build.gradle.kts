import java.util.zip.ZipFile
import java.security.MessageDigest
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Register generated Vosk assets through the AGP Variant Sources API.
abstract class ExtractOmegaModel : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val archive: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:javax.inject.Inject
  abstract val archiveOperations: org.gradle.api.file.ArchiveOperations

  @get:javax.inject.Inject
  abstract val fileSystemOperations: org.gradle.api.file.FileSystemOperations

  @TaskAction
  fun extract() {
    val aar = archive.singleFile
    val dest = outputDirectory.get().asFile
    fileSystemOperations.delete { delete(dest) }
    val model = dest.resolve("model-en-us")
    check(model.mkdirs() || model.isDirectory)
    ZipFile(aar).use { zip ->
      val files = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
      val candidates = files.filter { it.name.endsWith("/am/final.mdl") }
      val acoustic = if (candidates.isNotEmpty()) candidates.single()
        else files.singleOrNull { it.name.endsWith("/final.mdl") }
          ?: error("No acoustic model in Vosk AAR. Archive entries: " +
            files.take(40).joinToString { it.name })
      val suffix = if (acoustic.name.endsWith("/am/final.mdl")) "am/final.mdl" else "final.mdl"
      val prefix = acoustic.name.removeSuffix(suffix)
      check(prefix.isNotEmpty() && prefix.startsWith("assets/")) {
        "Unexpected Vosk model location: " + acoustic.name
      }
      logger.lifecycle("Extracting Vosk model from " + prefix)
      for (entry in files.filter { it.name.startsWith(prefix) }) {
        val relative = entry.name.removePrefix(prefix)
        val out = model.resolve(relative).canonicalFile
        check(out.toPath().startsWith(model.canonicalFile.toPath())) { "Unsafe archive entry" }
        out.parentFile.mkdirs()
        zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
      }
      check(model.resolve(suffix).length() > 0L)
    }
    val uuid = model.resolve("uuid")
    if (!uuid.isFile || uuid.length() == 0L) {
      val digest = MessageDigest.getInstance("SHA-256")
      aar.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          digest.update(buffer, 0, count)
        }
      }
      uuid.writeText(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    logger.lifecycle("Packaged Vosk model files: " + model.walkTopDown().count { it.isFile })
  }
}
val omegaModelArchive = configurations.create("omegaModelArchive") {
  isCanBeConsumed = false
  isCanBeResolved = true
  isTransitive = false
}
androidComponents {
  onVariants(selector().all()) { variant ->
    val extractModel = tasks.register<ExtractOmegaModel>(
      "extract" + variant.name.replaceFirstChar { it.uppercaseChar() } + "OmegaVoskModel"
    ) {
      archive.from(omegaModelArchive)
      outputDirectory.set(layout.buildDirectory.dir("generated/omegaVoskAssets/" + variant.name))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(extractModel, ExtractOmegaModel::outputDirectory)
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.pandubabu.kxmpzq"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation("net.java.dev.jna:jna:5.18.1@aar")
  implementation("com.alphacephei:vosk-android:0.3.75@aar")
  add(omegaModelArchive.name, "com.alphacephei:vosk-model-en:0.3.75@aar")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
