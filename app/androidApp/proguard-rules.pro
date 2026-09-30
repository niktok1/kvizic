# R8's rules for the release build. The libraries bring their own (kotlinx.serialization, Ktor, OkHttp,
# coroutines, Koin, Compose, AndroidX, Play services), so this holds only what this code needs beyond them.

# Keep the line numbers in a crash's stack trace, which the mapping file turns back into this code's names,
# and name every class's source file alike, so the trace gives away no more than it must.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
