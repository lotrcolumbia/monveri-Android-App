# Phase 1: stay close to defaults. The retained-class rules for kotlinx.serialization come
# from :core:model's consumer-rules.pro. Add app-specific keeps as later phases need them.

# Keep generic signatures for Retrofit + kotlinx.serialization reflection.
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit needs to read method-level annotations.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
