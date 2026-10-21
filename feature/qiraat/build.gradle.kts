plugins {
  id("quran.android.library.compose")
}

android.namespace = "com.quran.mobile.feature.qiraat"

dependencies {
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.media3.exoplayer)
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
}
