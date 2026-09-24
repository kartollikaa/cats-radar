# Glance instantiates action callbacks by the class name a placed widget stored, through the no-arg constructor.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
