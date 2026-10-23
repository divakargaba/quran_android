plugins {
  id("quran.android.library.compose")
}

android {
  namespace = "com.quran.mobile.feature.mutashabihat"
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":common:ui:core"))

  // compose
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
}
