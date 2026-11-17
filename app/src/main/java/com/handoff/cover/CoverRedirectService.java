package com.handoff.cover;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accessibility service to use the phone CLOSED from the cover screen:
 *
 *  1. Redirection: One UI's home screen launches apps on the inner display (which is off
 *     when folded). If an app appears on the inner display while folded, it is relaunched
 *     on the cover display; Android moves the existing task without restarting it.
 *
 *  2. Navigation on the cover screen (only when folded and unlocked):
 *     - Bar with Recents / Home (One UI) / Back / Apps (own launcher) buttons.
 *     - Gestures: swipe in from the left or right edge = Back;
 *                 swipe up from the bottom edge = Home.
 */
public class CoverRedirectService extends AccessibilityService implements DisplayManager.DisplayListener {

    private static final int COVER = LauncherActivity.COVER_DISPLAY_ID;

    /** Packages that are never redirected. */
    private static final Set<String> IGNORE = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.app.aodservice"));

    private final Map<String, Long> lastRedirect = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private DisplayManager displayManager;
    private KeyguardManager keyguard;

    private WindowManager coverWm;
    private final List<View> overlays = new ArrayList<>();
    private boolean overlaysShown;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        displayManager = getSystemService(DisplayManager.class);
        keyguard = getSystemService(KeyguardManager.class);
        displayManager.registerDisplayListener(this, main);
        main.post(poll);   // periodic check (the cover screen lock does not send any event)
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(poll);
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        removeOverlays();
        super.onDestroy();
    }

    @Override public void onInterrupt() {}
    @Override public void onDisplayAdded(int displayId) { updateOverlays(); }
    @Override public void onDisplayRemoved(int displayId) { updateOverlays(); }
    @Override public void onDisplayChanged(int displayId) { updateOverlays(); }

    /** Folded = inner display off and cover display on. */
    private boolean isFolded() {
        if (displayManager == null) return false;
        Display inner = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display cover = displayManager.getDisplay(COVER);
        return inner != null && cover != null
                && inner.getState() == Display.STATE_OFF
                && cover.getState() == Display.STATE_ON;
    }

    // ---------------------------------------------------------------- redirection

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) { updateOverlays(); return; }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        updateOverlays();
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (IGNORE.contains(pkg) || pkg.equals(getPackageName())) return;
        Recents.touch(this, pkg);   // own list of recently used apps
        if (event.getDisplayId() != Display.DEFAULT_DISPLAY) return;
        if (!isFolded()) return;

        // Avoid loops: at most one redirection per app every 2 s
        long now = SystemClock.elapsedRealtime();
        Long last = lastRedirect.get(pkg);
        if (last != null && now - last < 2000) return;

        PackageManager pm = getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;  // only apps with a launcher icon
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (startOnCover(launch)) lastRedirect.put(pkg, now);
    }

    private boolean startOnCover(Intent i) {
        ActivityOptions o = ActivityOptions.makeBasic();
        o.setLaunchDisplayId(COVER);
        try {
            startActivity(i, o.toBundle());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- actions

    private void goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void goHome() {
        LauncherActivity.openOneUiHome(this, COVER);
    }

    /** One UI's "Recent apps" view (the ||| button). */
    private static final android.content.ComponentName ONE_UI_RECENTS = new android.content.ComponentName(
            "com.sec.android.app.launcher", "com.android.quickstep.RecentsActivity");

    /** Opens One UI's Recents on the cover screen; falls back to the own list. */
    private void openRecents() {
        Intent r = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setComponent(ONE_UI_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        if (startOnCover(r)) return;
        startOnCover(new Intent(this, LauncherActivity.class)
                .putExtra(LauncherActivity.EXTRA_MODE, LauncherActivity.MODE_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void openApps() {
        startOnCover(new Intent(this, LauncherActivity.class)
                .putExtra(LauncherActivity.EXTRA_MODE, LauncherActivity.MODE_ALL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // ---------------------------------------------------------------- overlays

    /** Cover display metrics (its density differs from the inner display). */
    private android.util.DisplayMetrics coverMetrics;

    private int dp(float v) {
        android.util.DisplayMetrics m = coverMetrics != null ? coverMetrics : getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, m);
    }

    /**
     * True if an app is in the foreground on the cover screen. If the top application
     * window belongs to Samsung's UI (cover home or cover lock screen), returns false so
     * the bar is not drawn on top of it.
     */
    private boolean coverAppInFront() {
        try {
            android.util.SparseArray<List<android.view.accessibility.AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
            List<android.view.accessibility.AccessibilityWindowInfo> ws = all.get(COVER);
            if (ws == null) return false;
            for (android.view.accessibility.AccessibilityWindowInfo w : ws) {   // top to bottom
                if (w.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                android.view.accessibility.AccessibilityNodeInfo root = w.getRoot();
                CharSequence pkg = root != null ? root.getPackageName() : null;
                return pkg != null && !"com.android.systemui".contentEquals(pkg);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            updateOverlays();
            main.postDelayed(this, 1000);
        }
    };

    private void updateOverlays() {
        boolean want = isFolded() && (keyguard == null || !keyguard.isKeyguardLocked()) && coverAppInFront();
        if (want && !overlaysShown) showOverlays();
        else if (!want && overlaysShown) removeOverlays();
    }

    private void showOverlays() {
        Display cover = displayManager.getDisplay(COVER);
        if (cover == null) return;
        Context wc = createDisplayContext(cover)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null);
        coverWm = wc.getSystemService(WindowManager.class);
        coverMetrics = wc.getResources().getDisplayMetrics();
        // Area taken by the camera module (bottom-right cutout of the cover screen)
        int camLeft = Integer.MAX_VALUE, camTop = Integer.MAX_VALUE;
        android.view.DisplayCutout cut = cover.getCutout();
        if (cut != null) {
            for (android.graphics.Rect r : cut.getBoundingRects()) {
                if (r.isEmpty()) continue;
                camLeft = Math.min(camLeft, r.left);
                camTop = Math.min(camTop, r.top);
            }
        }
        android.graphics.Point size = new android.graphics.Point();
        cover.getRealSize(size);
        int bottomStripWidth = camLeft == Integer.MAX_VALUE ? size.x : camLeft;      // up to the cameras
        int rightStripHeight = camTop == Integer.MAX_VALUE ? size.y : camTop;        // above the cameras
        try {
            // Gesture edges (added first so the button bar stays on top)
            addOverlay(wc, edgeStrip(wc, Edge.LEFT), Gravity.START | Gravity.TOP, dp(10), WindowManager.LayoutParams.MATCH_PARENT);
            addOverlay(wc, edgeStrip(wc, Edge.RIGHT), Gravity.END | Gravity.TOP, dp(10), rightStripHeight);
            addOverlay(wc, edgeStrip(wc, Edge.BOTTOM), Gravity.BOTTOM | Gravity.START, bottomStripWidth, dp(10));
            // Button bar: bottom-left, outside the camera area
            WindowManager.LayoutParams lp = params(Gravity.BOTTOM | Gravity.START,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            android.content.SharedPreferences sp = getSharedPreferences("nav", MODE_PRIVATE);
            lp.x = sp.getInt("x", dp(6));   // default: left of the cameras
            lp.y = sp.getInt("y", dp(4));
            View bar = navBar(wc, lp);
            coverWm.addView(bar, lp);
            overlays.add(bar);
            overlaysShown = true;
        } catch (Exception e) {
            removeOverlays();
        }
    }

    private void removeOverlays() {
        if (coverWm != null) {
            for (View v : overlays) {
                try { coverWm.removeViewImmediate(v); } catch (Exception ignored) {}
            }
        }
        overlays.clear();
        overlaysShown = false;
    }

    private WindowManager.LayoutParams params(int gravity, int w, int h) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        return lp;
    }

    private void addOverlay(Context wc, View v, int gravity, int w, int h) {
        coverWm.addView(v, params(gravity, w, h));
        overlays.add(v);
    }

    private View navBar(Context c, WindowManager.LayoutParams lp) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        // No background, like One UI's bar: icons have a light halo to be visible on any content
        bar.setPadding(dp(2), 0, dp(2), 0);
        bar.addView(dragHandle(c, bar, lp));
        // Same order as One UI: Recents | Home | Back  (+ app drawer)
        bar.addView(navButton(c, R.drawable.ic_nav_recents, R.string.nav_recents, v -> openRecents()));
        bar.addView(navButton(c, R.drawable.ic_nav_home, R.string.nav_home, v -> goHome()));
        bar.addView(navButton(c, R.drawable.ic_nav_back, R.string.nav_back, v -> goBack()));
        bar.addView(navButton(c, R.drawable.ic_nav_apps, R.string.nav_apps, v -> openApps()));
        return bar;
    }

    private View navButton(Context c, int icon, int description, View.OnClickListener l) {
        android.widget.ImageView b = new android.widget.ImageView(c);
        b.setImageResource(icon);
        b.setContentDescription(c.getString(description));
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(6), dp(5), dp(6), dp(5));
        // Circular touch ripple, like One UI's buttons
        android.util.TypedValue tv = new android.util.TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        if (tv.resourceId != 0) b.setBackgroundResource(tv.resourceId);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(37), dp(34)));
        return b;
    }

    /** Handle to drag the bar (gravity BOTTOM|START: y grows upwards). Saves the position. */
    @SuppressLint("ClickableViewAccessibility")
    private View dragHandle(Context c, View bar, WindowManager.LayoutParams lp) {
        android.widget.ImageView h = new android.widget.ImageView(c);
        h.setImageResource(R.drawable.ic_nav_drag);
        h.setContentDescription(c.getString(R.string.nav_drag));
        h.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        h.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(34)));
        final float[] down = new float[4];
        h.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getRawX(); down[1] = ev.getRawY(); down[2] = lp.x; down[3] = lp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = Math.max(0, (int) (down[2] + ev.getRawX() - down[0]));
                    lp.y = Math.max(0, (int) (down[3] - (ev.getRawY() - down[1])));
                    try { coverWm.updateViewLayout(bar, lp); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    getSharedPreferences("nav", MODE_PRIVATE).edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                    return true;
                default:
                    return true;
            }
        });
        return h;
    }

    private enum Edge { LEFT, RIGHT, BOTTOM }

    /** Invisible edge strip that turns an inward swipe into Back / Home. */
    @SuppressLint("ClickableViewAccessibility")
    private View edgeStrip(Context c, Edge edge) {
        View v = new View(c);
        final float threshold = dp(36);
        final float[] start = new float[2];
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = ev.getRawX(); start[1] = ev.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = ev.getRawX() - start[0], dy = ev.getRawY() - start[1];
                    if (edge == Edge.LEFT && dx > threshold) goBack();
                    else if (edge == Edge.RIGHT && -dx > threshold) goBack();
                    else if (edge == Edge.BOTTOM && -dy > threshold) goHome();
                    return true;
                default:
                    return true;
            }
        });
        return v;
    }
}
