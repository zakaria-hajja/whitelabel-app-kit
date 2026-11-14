package com.whitelabel.core.shell

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.WindowManager.LayoutParams
import android.view.inspector.WindowInspector
import java.lang.ref.WeakReference

/** The app's non-activity windows, as the content gate's lifecycle covers and uncovers them. */
internal interface AppWindows {
    fun cover()
    fun uncover()
}

/**
 * Hides the app's own windows **besides** its activities' (Compose `Dialog`s, M3 `ModalBottomSheet`s,
 * popups, View dialogs) while the app is in the background, so none of them can draw before the
 * content gate decides on return.
 *
 * Why: when the app returns, the window manager shows every window of the activity and asks each to
 * draw for the opening transition. That happens **before** the activity's `onStart`, so before the
 * process ON_START reaches the gate. The platform hides the activity's own window while it is stopped
 * (its decor view is INVISIBLE until `onResume`), but not the other windows: a dialog open at background
 * draws its old content in the first frames of the return (Tend card rQTcDKFu, BUG-1).
 *
 * [cover] runs at process ON_STOP; [uncover] runs once the gate is OPEN again (a return in the grace
 * period, or the unlock). A relock removes the composition's windows meanwhile, so they never show.
 * Main thread only.
 *
 * Not covered yet (tracked as a follow-up): a window created after [cover] ran, for example a dialog
 * a ViewModel event opens while the app is in the background, and a covered window something shows
 * again while the app is in the background (`Dialog.show()` on a showing dialog). Either can still
 * draw before the gate decides. API 26–28 is best effort: the window list comes from reflection, and
 * if that fails nothing is hidden (the behaviour before this fix).
 */
internal class AppWindowCover(private val windowRoots: () -> List<View> = ::appWindowRoots) : AppWindows {

    private val covered = mutableListOf<WeakReference<View>>()

    override fun cover() {
        for (root in windowRoots()) {
            val type = (root.layoutParams as? LayoutParams)?.type ?: continue
            if (!coversWindow(type, root.visibility)) continue
            root.visibility = View.INVISIBLE
            covered += WeakReference(root)
        }
    }

    /** Shows again only what [cover] hid and nobody changed since. */
    override fun uncover() {
        covered.forEach { ref -> ref.get()?.takeIf { it.visibility == View.INVISIBLE }?.visibility = View.VISIBLE }
        covered.clear()
    }
}

/**
 * Whether [AppWindowCover] hides a window of [type] whose root has [visibility]: the activity's
 * windows that aren't the activity itself (application and sub-window types). The activity window and
 * its starting window are the platform's; overlays and toasts are meant to show outside the app.
 * A window that is already hidden stays the app's to show.
 */
internal fun coversWindow(type: Int, visibility: Int): Boolean {
    if (visibility != View.VISIBLE) return false
    val application = type in LayoutParams.FIRST_APPLICATION_WINDOW..LayoutParams.LAST_APPLICATION_WINDOW &&
        type != LayoutParams.TYPE_BASE_APPLICATION && type != LayoutParams.TYPE_APPLICATION_STARTING
    val subWindow = type in LayoutParams.FIRST_SUB_WINDOW..LayoutParams.LAST_SUB_WINDOW
    return application || subWindow
}

/** The root views of this process's windows. API 29+ has a public API; below it, the one Espresso reads. */
private fun appWindowRoots(): List<View> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WindowInspector.getGlobalWindowViews() else legacyWindowRoots()

/** API 26–28: `WindowManagerGlobal.mViews` (no hidden-API enforcement before 28, greylisted on 28). Best effort. */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
private fun legacyWindowRoots(): List<View> = runCatching {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
    views.filterIsInstance<View>()
}.getOrDefault(emptyList())
