package com.handoff.cover;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lanzador pensado para la pantalla exterior (Flex Window) del Galaxy Z Flip.
 * Muestra las apps instaladas y las abre en la MISMA pantalla en la que se esta
 * mostrando el lanzador. Si una app ya estaba abierta en la pantalla interior,
 * Android la trae a la exterior.
 */
public class LauncherActivity extends Activity {

    /** Id de la pantalla exterior en el Z Flip 5 (pantalla interior = 0). */
    static final int COVER_DISPLAY_ID = 1;

    /** Modo de la lista: todas las apps o solo las usadas recientemente. */
    static final String EXTRA_MODE = "mode";
    static final int MODE_ALL = 0;
    static final int MODE_RECENTS = 1;
    private int mode = MODE_ALL;
    private EditText search;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private TextView status;

    static final class AppEntry {
        final String label;
        final ComponentName component;
        final Drawable icon;
        AppEntry(String label, ComponentName component, Drawable icon) {
            this.label = label; this.component = component; this.icon = icon;
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int currentDisplayId() {
        Display d = getDisplay();
        return d != null ? d.getDisplayId() : Display.DEFAULT_DISPLAY;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_ALL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));

        // Barra superior: buscador + boton para mover el lanzador a la otra pantalla
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bar.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button oneUi = new Button(this);
        oneUi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        oneUi.setText(R.string.one_ui);
        oneUi.setOnClickListener(v -> openOneUiHome(this, currentDisplayId()));
        bar.addView(oneUi, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button wall = new Button(this);
        wall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        wall.setText(R.string.wallpaper);
        wall.setOnClickListener(v -> startActivity(new Intent(this, WallpaperActivity.class)));
        bar.addView(wall, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button swap = new Button(this);
        swap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        swap.setText(currentDisplayId() == COVER_DISPLAY_ID ? R.string.to_inner : R.string.to_cover);
        swap.setOnClickListener(v -> moveSelfToOtherDisplay());
        bar.addView(swap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bar);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        status.setTextColor(0xFF9E9E9E);
        status.setText(R.string.loading);
        root.addView(status);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(76));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(6));
        grid.setHorizontalSpacing(dp(4));
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(shownApps.get(position)));
        // En Recientes, mantener pulsado quita la app de la lista
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (mode != MODE_RECENTS) return false;
            Recents.remove(this, shownApps.get(position).component.getPackageName());
            filter(search.getText().toString());
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        loadApps();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mode = intent.getIntExtra(EXTRA_MODE, mode);
        if (search != null) search.setText("");
        filter("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!allApps.isEmpty()) filter(search != null ? search.getText().toString() : "");
        updateStatus();
    }

    private void updateStatus() {
        if (status == null) return;
        int id = currentDisplayId();
        String where = id == COVER_DISPLAY_ID ? getString(R.string.on_cover) : getString(R.string.on_inner);
        int fmt = mode == MODE_RECENTS ? R.string.status_recents_fmt : R.string.status_fmt;
        status.setText(getString(fmt, where, shownApps.size()));
    }

    private void loadApps() {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(q, 0);
            List<AppEntry> list = new ArrayList<>();
            for (ResolveInfo ri : infos) {
                if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
                list.add(new AppEntry(
                        String.valueOf(ri.loadLabel(pm)),
                        new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                        ri.loadIcon(pm)));
            }
            Collator c = Collator.getInstance(new Locale("es", "ES"));
            list.sort((a, b) -> c.compare(a.label, b.label));
            main.post(() -> {
                allApps.clear();
                allApps.addAll(list);
                filter("");
            });
        }).start();
    }

    private void filter(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        if (mode == MODE_RECENTS) {
            // Orden de uso reciente; una entrada por app
            for (String pkg : Recents.get(this)) {
                for (AppEntry e : allApps) {
                    if (e.component.getPackageName().equals(pkg)) {
                        if (t.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(t)) shownApps.add(e);
                        break;
                    }
                }
            }
        } else {
            for (AppEntry e : allApps) {
                if (t.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(t)) shownApps.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    /** Abre la app en la pantalla donde esta ahora el lanzador. */
    private void launch(AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        ActivityOptions o = ActivityOptions.makeBasic();
        o.setLaunchDisplayId(currentDisplayId());
        try {
            startActivity(i, o.toBundle());
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.launch_error, ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** Componente de la pantalla de inicio de One UI (Samsung). */
    static final ComponentName ONE_UI_HOME = new ComponentName(
            "com.sec.android.app.launcher", "com.sec.android.app.launcher.activities.LauncherActivity");

    /** Abre la pantalla de inicio de One UI en la pantalla indicada. */
    static void openOneUiHome(android.content.Context ctx, int displayId) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(ONE_UI_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions o = ActivityOptions.makeBasic();
        o.setLaunchDisplayId(displayId);
        try {
            ctx.startActivity(i, o.toBundle());
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.launch_error, ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** Vuelve a abrir el lanzador en la otra pantalla (interior <-> exterior). */
    private void moveSelfToOtherDisplay() {
        int target = currentDisplayId() == COVER_DISPLAY_ID ? Display.DEFAULT_DISPLAY : COVER_DISPLAY_ID;
        Intent i = new Intent(this, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityOptions o = ActivityOptions.makeBasic();
        o.setLaunchDisplayId(target);
        try {
            startActivity(i, o.toBundle());
            finish();
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.launch_error, ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int p) { return shownApps.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(LauncherActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, dp(4), 0, dp(4));
                icon = new ImageView(LauncherActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
                label = new TextView(LauncherActivity.this);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                label.setTextColor(0xFFFFFFFF);
                label.setSingleLine(true);
                label.setGravity(Gravity.CENTER_HORIZONTAL);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            AppEntry e = shownApps.get(position);
            icon.setImageDrawable(e.icon);
            label.setText(e.label);
            return cell;
        }
    }
}
