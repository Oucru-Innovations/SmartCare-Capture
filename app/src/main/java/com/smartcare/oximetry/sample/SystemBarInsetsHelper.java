package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps activity content inside the status bar and navigation bar safe areas.
 */
public final class SystemBarInsetsHelper {

    private SystemBarInsetsHelper() {
    }

    /**
     * Áp dụng window insets cho content view của {@code activity}: thêm padding theo status bar,
     * navigation bar và display cutout để nội dung không bị che bởi system bars (edge-to-edge safe).
     */
    public static void apply(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(window, true);

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }

        View root = content.getChildAt(0);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        boolean actionBarVisible = activity.getActionBar() != null
                && activity.getActionBar().isShowing();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());

            int topInset = actionBarVisible ? 0 : Math.max(statusBars.top, cutout.top);
            view.setPadding(
                    baseLeft + Math.max(navBars.left, cutout.left),
                    baseTop + topInset,
                    baseRight + Math.max(navBars.right, cutout.right),
                    baseBottom + Math.max(navBars.bottom, cutout.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
