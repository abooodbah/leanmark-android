# The app has no reflection or serialization. Native and JavaScript entry
# points get their keep rules next to the code that needs them.

# Created and thrown from native code (leanmark_jni.cpp).
-keep class io.github.abooodbah.leanmark.MarkdownException {
    <init>(java.lang.String, boolean);
}
