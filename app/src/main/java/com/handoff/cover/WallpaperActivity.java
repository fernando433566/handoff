package com.handoff.cover;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Sets the cover screen wallpaper (the one shown behind One UI when it runs on the Flex
 * Window). Reachable from:
 *  - the launcher's "Fondo" button (pick an image or copy the inner screen wallpaper),
 *  - sharing an image from the Gallery.
 *
 * Samsung keeps a separate wallpaper for the cover screen; in the API it is selected with
 * flag 16 (sub display) combined with FLAG_SYSTEM.
 */
public class WallpaperActivity extends Activity {

    /** Samsung flag for the cover (sub) display wallpaper. */
    static final int SAMSUNG_FLAG_SUB_DISPLAY = 16;
    /** Samsung flag for the main (inner) display wallpaper. */
    static final int SAMSUNG_FLAG_MAIN_DISPLAY = 4;
    private static final int REQ_PICK = 1;
    /** Extra to copy the inner screen wallpaper directly (no dialog). */
    static final String EXTRA_COPY_INNER = "copy_inner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        Uri shared = null;
        if (Intent.ACTION_SEND.equals(in.getAction())) shared = in.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        else if (Intent.ACTION_ATTACH_DATA.equals(in.getAction())) shared = in.getData();

        if (shared != null) {
            applyFromUri(shared);
            return;
        }
        if (in.getBooleanExtra(EXTRA_COPY_INNER, false)) {
            copyInnerWallpaper();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wp_title)
                .setItems(new CharSequence[]{getString(R.string.wp_pick), getString(R.string.wp_copy_inner)}, (d, which) -> {
                    if (which == 0) pickImage();
                    else copyInnerWallpaper();
                })
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void pickImage() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(pick, getString(R.string.wp_pick)), REQ_PICK);
        } catch (Exception e) {
            done(getString(R.string.wp_error, e.getMessage()));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            applyFromUri(data.getData());
        } else {
            finish();
        }
    }

    private void applyFromUri(Uri uri) {
        new Thread(() -> {
            String msg;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap b = BitmapFactory.decodeStream(is);
                msg = b == null ? getString(R.string.wp_error, "imagen no válida") : setCoverWallpaper(b);
            } catch (Exception e) {
                android.util.Log.w("CoverWallpaper", "failed to apply wallpaper", e);
                msg = getString(R.string.wp_error, e.getMessage());
            }
            final String m = msg;
            runOnUiThread(() -> done(m));
        }).start();
    }

    /** Copies the current inner screen wallpaper (may require the all-files storage permission). */
    private void copyInnerWallpaper() {
        new Thread(() -> {
            String msg;
            try {
                // Samsung returns the wallpaper of the context's display: use an inner display context
                Display inner = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
                android.content.Context innerCtx = inner != null ? createDisplayContext(inner) : this;
                WallpaperManager wmInner = WallpaperManager.getInstance(innerCtx);
                Drawable d;
                try {
                    // Samsung: 4 = main (inner) display. FLAG_SYSTEM|4 = inner home wallpaper
                    d = wmInner.getDrawable(WallpaperManager.FLAG_SYSTEM | SAMSUNG_FLAG_MAIN_DISPLAY);
                } catch (Exception e) {
                    d = wmInner.getDrawable(WallpaperManager.FLAG_SYSTEM);
                }
                msg = d == null ? getString(R.string.wp_error, "sin fondo") : setCoverWallpaper(toBitmap(d));
            } catch (Exception e) {
                android.util.Log.w("CoverWallpaper", "could not read the inner screen wallpaper", e);
                msg = getString(R.string.wp_copy_denied);
            }
            final String m = msg;
            runOnUiThread(() -> done(m));
        }).start();
    }

    private static Bitmap toBitmap(Drawable d) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) return ((BitmapDrawable) d).getBitmap();
        Bitmap b = Bitmap.createBitmap(Math.max(1, d.getIntrinsicWidth()), Math.max(1, d.getIntrinsicHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return b;
    }

    /** Center-crops the image to the cover screen size and applies it. */
    private String setCoverWallpaper(Bitmap src) throws Exception {
        Point size = new Point(748, 720);
        Display cover = getSystemService(DisplayManager.class).getDisplay(LauncherActivity.COVER_DISPLAY_ID);
        if (cover != null) cover.getRealSize(size);
        Bitmap b = centerCrop(src, size.x, size.y);

        WallpaperManager wm = WallpaperManager.getInstance(this);
        wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM | SAMSUNG_FLAG_SUB_DISPLAY);
        return getString(R.string.wp_done);
    }

    private static Bitmap centerCrop(Bitmap src, int w, int h) {
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(x, y, x + cw, y + ch), new Rect(0, 0, w, h), null);
        return out;
    }

    private void done(String msg) {
        android.util.Log.i("CoverWallpaper", msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }
}
