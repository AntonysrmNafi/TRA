# Keep Room-generated classes and OkHttp/okio internals that reflection touches.
-keep class com.blockveil.tracker.remover.data.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
