plugins {
  id("quran.android.library.compose")
}

android {
  namespace = "com.quran.mobile.feature.tafsir"
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.okhttp)

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
  testImplementation(libs.junit.ktx)
}
