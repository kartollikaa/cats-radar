# Crashlytics: file names and line numbers in a release stack trace, and exception types kept by name.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Glance instantiates action callbacks by the class name a placed widget stored, through the no-arg constructor.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
