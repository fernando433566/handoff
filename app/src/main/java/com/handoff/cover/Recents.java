package com.handoff.cover;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Own list of recently used apps, used as a fallback when One UI's Recents view cannot be
 * opened on the cover screen. Fed by the accessibility service every time an app comes to
 * the foreground. Only package names are stored.
 */
final class Recents {
    private static final String PREFS = "recents";
    private static final String KEY = "list";
    private static final int MAX = 20;

    private Recents() {}

    static List<String> get(Context c) {
        String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split("\n")));
        return out;
    }

    /** Moves the package to the front of the list. */
    static void touch(Context c, String pkg) {
        List<String> l = get(c);
        if (!l.isEmpty() && l.get(0).equals(pkg)) return;
        l.remove(pkg);
        l.add(0, pkg);
        while (l.size() > MAX) l.remove(l.size() - 1);
        save(c, l);
    }

    static void remove(Context c, String pkg) {
        List<String> l = get(c);
        if (l.remove(pkg)) save(c, l);
    }

    private static void save(Context c, List<String> l) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY, String.join("\n", l)).apply();
    }
}
