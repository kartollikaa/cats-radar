# Glance instantiates action callbacks by class name; its own rule keeps the class but not the constructor.
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
