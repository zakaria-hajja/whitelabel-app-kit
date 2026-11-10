# App-specific R8 rules. Libraries (kotlinx.serialization, Ktor, Hilt, Compose) ship their
# own consumer rules; only gaps go here.

# Ktor references JVM management APIs that do not exist on Android and are never called there.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
