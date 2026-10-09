package com.weihe.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import com.weihe.schedule.design.Palette;

/**
 * Window-level setup that the theme cannot express on its own.
 *
 * <p>Two things live here: matching the system bars to the surface drawn next to
 * them, and keeping the system bar icons legible against that colour. Both have
 * to be done in code because the light/dark icon flags became available at
 * different API levels ({@code windowLightStatusBar} at 23, its navigation bar
 * sibling only at 27) while minSdk is 26.</p>
 */
public final class Insets {

    private Insets() {
    }

    /**
     * Paints the system bars to match the app bar and the page.
     *
     * <p>Both bars take a light colour, so both need dark icons. That is the
     * whole of the "seamless header" trick here: the header is
     * {@link Palette#HEADER_SURFACE} and the status bar is painted the same
     * value, so there is no boundary to see and nothing that can drift.</p>
     *
     * <p>What is deliberately <em>not</em> done here: drawing behind the bars. A
     * transparent status bar without
     * {@code WindowCompat.setDecorFitsSystemWindows(false)} shows the window
     * background as a strip instead of the header, and that helper lives in
     * androidx.core, which this project cannot depend on. Matching the colour
     * gets the same seamless look with none of the risk.</p>
     */
    public static void applyBrandedSystemBars(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Palette.HEADER_SURFACE);
        window.setNavigationBarColor(Palette.SURFACE);
        applySystemBarIconFlags(window, true, true);
    }

    /** The status bar height in pixels, for spacing that must clear it. */
    public static int statusBarHeight(View view) {
        // NOT android.R.dimen.status_bar_height: that is an internal resource and
        // relying on it is unsupported. The window insets report the same number
        // through the public API, which minSdk 26 always provides.
        android.view.WindowInsets insets = view.getRootWindowInsets();
        return insets == null ? 0 : insets.getSystemWindowInsetTop();
    }

    /**
     * Sets the light/dark system icon flags, skipping the ones this API lacks.
     *
     * @param lightNavigationBar true when the app paints a light colour behind
     *                           the navigation bar
     */
    public static void applySystemBarIconFlags(Window window,
            boolean lightStatusBar, boolean lightNavigationBar) {
        int flags = window.getDecorView().getSystemUiVisibility();
        flags = setFlag(flags, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR, lightStatusBar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            flags = setFlag(flags, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
                    lightNavigationBar);
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private static int setFlag(int flags, int flag, boolean on) {
        return on ? (flags | flag) : (flags & ~flag);
    }
}
