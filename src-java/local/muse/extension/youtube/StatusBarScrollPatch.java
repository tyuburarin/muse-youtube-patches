package local.muse.extension.youtube;

import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Hides the system status bar while a feed list is scrolled down, and shows it
 * again when the list is scrolled up, following the bottom navigation bar.
 *
 * Standalone counterpart of the Morphe "Hide status bar on scroll" change:
 * the hook is RecyclerView.onScrolled (the same method Morphe's Navigation
 * bar patch hooks), so this runs for every list. Hiding is limited to large
 * lists with no video player or Shorts in front, mirroring the guards of
 * Morphe's NavigationBarPatch, which are read reflectively from Morphe's own
 * extension classes when they are present in the app.
 */
public final class StatusBarScrollPatch {

    private static final String TAG = "MuseStatusBarScroll";

    /**
     * Distance the lists scrolled in the same direction since the last change,
     * accessed only on the main thread.
     */
    private static int scrollDistance;

    /**
     * Distance in pixels a touch can move before it scrolls, or -1 if not yet read.
     */
    private static int touchSlop = -1;

    /**
     * If the status bar is hidden by scrolling, accessed only on the main thread.
     */
    private static boolean hidden;

    /**
     * The views the app draws behind the status bar, recorded whenever the
     * app shows one, so they can be hidden and shown together with the
     * status bar. Accessed only on the main thread.
     */
    private static final java.util.List<WeakReference<View>> statusBarBackgroundViews =
            new java.util.ArrayList<>();

    private StatusBarScrollPatch() {
    }

    /**
     * Injection point. Called when any RecyclerView scrolls.
     *
     * @param list The list that scrolled.
     * @param dy   The vertical distance scrolled, more than zero when scrolling down.
     */
    public static void onListScrolled(View list, int dy) {
        if (dy == 0) {
            return;
        }

        try {
            if ((dy > 0) != (scrollDistance > 0)) {
                scrollDistance = 0;
            }
            scrollDistance += dy;
            if (touchSlop < 0) {
                touchSlop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
            }
            if (Math.abs(scrollDistance) < touchSlop) {
                return;
            }

            final boolean hide = scrollDistance > 0;
            scrollDistance = 0;
            if (hide == hidden) {
                // The app can show the status bar again on its own, for example
                // after fullscreen. Re-apply if the system state disagrees.
                if (isStatusBarVisible(list) == hide) {
                    setStatusBarHidden(list, hide);
                }
                return;
            }
            if (hide && !canHide(list)) {
                return;
            }
            setStatusBarHidden(list, hide);
        } catch (Throwable ex) {
            Log.e(TAG, "onListScrolled failure", ex);
        }
    }

    /**
     * Only large lists hide the status bar, and not while a video player
     * (other than the miniplayer or an inline feed player) or the Shorts
     * player is in front, the same conditions Morphe's navigation bar uses.
     */
    private static boolean canHide(View list) {
        if (!list.isShown()) {
            return false;
        }
        View root = list.getRootView();
        if (root == null || root.getHeight() <= 0) {
            return false;
        }
        // Small lists (shelves, menus, panels) do not fill the screen.
        if (list.getHeight() * 2 < root.getHeight()) {
            return false;
        }
        return !isPlayerBlocking() && !isShortsOpen();
    }

    /**
     * Reads Morphe's PlayerType reflectively, as this bundle does not depend
     * on Morphe's patches. Without them, nothing blocks hiding.
     */
    private static boolean isPlayerBlocking() {
        try {
            Class<?> playerTypeClass = Class.forName("app.morphe.extension.youtube.shared.PlayerType");
            Object current = playerTypeClass.getMethod("getCurrent").invoke(null);
            if (current == null) {
                return false;
            }
            boolean noneHiddenOrMinimized = (Boolean) playerTypeClass
                    .getMethod("isNoneHiddenOrMinimized").invoke(current);
            if (noneHiddenOrMinimized) {
                return false;
            }
            return !"INLINE_MINIMAL".equals(String.valueOf(current));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isShortsOpen() {
        try {
            Class<?> shortsStateClass = Class.forName("app.morphe.extension.youtube.shared.ShortsPlayerState");
            Method isOpen = shortsStateClass.getMethod("isOpen");
            return (Boolean) isOpen.invoke(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isStatusBarVisible(View view) {
        try {
            View root = view.getRootView();
            WindowInsets insets = root == null ? null : root.getRootWindowInsets();
            if (insets == null) {
                return !hidden;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return insets.isVisible(WindowInsets.Type.statusBars());
            }
        } catch (Throwable ignored) {
        }
        return !hidden;
    }

    /**
     * Injection point. Called after the app shows the view it draws behind
     * the status bar and sets its color. That view keeps the height of the
     * hidden status bar and is drawn over the top bars as a translucent
     * black bar, so it is hidden while the status bar is hidden.
     */
    public static void hideStatusBarBackground(View view) {
        if (view == null) {
            return;
        }
        // Always recorded, not only while hidden: the app shows this view
        // before the status bar is hidden by scrolling, and may not show it
        // again until long after, so the recorded views are hidden directly
        // when the status bar is hidden.
        boolean known = false;
        for (int i = statusBarBackgroundViews.size() - 1; i >= 0; i--) {
            View recorded = statusBarBackgroundViews.get(i).get();
            if (recorded == null) {
                statusBarBackgroundViews.remove(i);
            } else if (recorded == view) {
                known = true;
            }
        }
        if (!known) {
            statusBarBackgroundViews.add(new WeakReference<>(view));
        }
        if (hidden) {
            view.setVisibility(View.INVISIBLE);
        }
    }

    private static void setStatusBarBackgroundsVisible(boolean visible) {
        for (int i = statusBarBackgroundViews.size() - 1; i >= 0; i--) {
            View background = statusBarBackgroundViews.get(i).get();
            if (background == null) {
                statusBarBackgroundViews.remove(i);
            } else {
                background.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private static void setStatusBarHidden(View view, boolean hide) {
        hidden = hide;
        setStatusBarBackgroundsVisible(!hide);
        View root = view.getRootView();
        if (root == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = root.getWindowInsetsController();
            if (controller != null) {
                if (hide) {
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.statusBars());
                } else {
                    controller.show(WindowInsets.Type.statusBars());
                }
            }
            return;
        }
        if (hide) {
            root.setSystemUiVisibility(root.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            root.setSystemUiVisibility(root.getSystemUiVisibility()
                    & ~View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }
}
