package com.handoff.cover;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Cover screen widget with two buttons:
 *  - "One UI": opens Samsung's One UI home screen on the cover screen.
 *  - "Apps":   opens this app's app grid on the cover screen.
 */
public class CoverWidgetProvider extends AppWidgetProvider {

    private static PendingIntent onCover(Context ctx, int requestCode, Intent intent) {
        ActivityOptions o = ActivityOptions.makeBasic();
        o.setLaunchDisplayId(LauncherActivity.COVER_DISPLAY_ID);
        return PendingIntent.getActivity(ctx, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT, o.toBundle());
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .setComponent(LauncherActivity.ONE_UI_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent apps = new Intent(context, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent piHome = onCover(context, 1, home);
        PendingIntent piApps = onCover(context, 2, apps);

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.cover_widget);
            rv.setOnClickPendingIntent(R.id.btn_oneui, piHome);
            rv.setOnClickPendingIntent(R.id.btn_apps, piApps);
            manager.updateAppWidget(id, rv);
        }
    }
}
