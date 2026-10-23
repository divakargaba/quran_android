plugins {
  id("quran.android.library.compose")
}

android {
  namespace = "com.quran.mobile.feature.qiraat"
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:ui:core"))
  implementation(project(":pages:common:madani"))
  implementation(project(":pages:data:madani"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.okhttp)

  // compose
  implementation(libs.compose.animation)
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
