package com.spsiam.spvpn;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Small SharedPreferences helper for SP VPN settings.
 * Stores only setting values and imported-config metadata (display name +
 * internal file name). Never stores .ovpn contents, usernames or passwords.
 */
public final class SpPrefs {

    public static final String PREFS_NAME = "sp_vpn_prefs";
    public static final String KEY_THEME = "theme"; // light | dark | system
    public static final String KEY_AUTO_RECONNECT = "auto_reconnect";
    public static final String KEY_DNS_PRIMARY = "dns_primary";
    public static final String KEY_DNS_SECONDARY = "dns_secondary";
    public static final String KEY_SELECTED_SERVER = "selected_server";
    public static final String KEY_IMPORTED = "imported_servers"; // name|file, \u001f separated

    public static final String DEFAULT_DNS_PRIMARY = "8.8.8.8";
    public static final String DEFAULT_DNS_SECONDARY = "1.1.1.1";

    private SpPrefs() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getTheme(Context context) {
        return prefs(context).getString(KEY_THEME, "system");
    }

    public static boolean isAutoReconnect(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_RECONNECT, true);
    }

    public static String getDnsPrimary(Context context) {
        return prefs(context).getString(KEY_DNS_PRIMARY, DEFAULT_DNS_PRIMARY);
    }

    public static String getDnsSecondary(Context context) {
        return prefs(context).getString(KEY_DNS_SECONDARY, DEFAULT_DNS_SECONDARY);
    }

    /** True when the user changed DNS away from the defaults. */
    public static boolean hasCustomDns(Context context) {
        return !DEFAULT_DNS_PRIMARY.equals(getDnsPrimary(context))
                || !DEFAULT_DNS_SECONDARY.equals(getDnsSecondary(context));
    }

    public static String getSelectedServerId(Context context) {
        return prefs(context).getString(KEY_SELECTED_SERVER, VpnServer.JAPAN_ID);
    }

    /** Applies the saved theme before the Activity draws. Call in onCreate. */
    public static void applyTheme(Context context) {
        String theme = getTheme(context);
        int mode;
        if ("light".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
