# The JNA and bindings rules come with the :termoak module
# (core/bindings/kotlin/consumer-rules.pro).

# UnifiedPush's connector decrypts with Tink, which refers to annotations that
# are not on the classpath (compile-time only).
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
