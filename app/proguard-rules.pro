-keepattributes *Annotation*
-keep class com.example.ai.** { *; }
-keep class com.example.control.** { *; }
-keep class com.example.omega.** { *; }
-keep class com.example.security.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class ai.onnxruntime.** { *; }

# WorkManager instantiates this worker by class name.
-keep class com.example.omega.DailyRoutineWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
