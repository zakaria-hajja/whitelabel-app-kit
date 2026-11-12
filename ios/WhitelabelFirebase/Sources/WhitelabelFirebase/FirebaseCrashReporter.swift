import Foundation
import Shared

/**
 * The shared `CrashReporter` recording to Crashlytics through CrashKiOS, so a report keeps its
 * Kotlin frames. Consent gating is applied around it in Kotlin; needs `installKotlinCrashHook()`.
 */
final class FirebaseCrashReporter: NSObject, CrashReporter {
    func record(throwable: KotlinThrowable) {
        KotlinCrashesKt.recordKotlinException(throwable: throwable)
    }
}
