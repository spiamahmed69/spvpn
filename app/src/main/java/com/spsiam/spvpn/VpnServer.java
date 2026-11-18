package com.spsiam.spvpn;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A VPN server entry. The only preloaded server is the public VPNGate relay
 * bundled with the upstream BongoVPN sample. Imported servers come from
 * .ovpn files the user picks themselves. No other servers are invented.
 */
public final class VpnServer {

    public static final String JAPAN_ID = "japan";
    // VPNGate's standard public credentials, as shipped in the upstream sample.
    public static final String JAPAN_USERNAME = "vpn";
    public static final String JAPAN_PASSWORD = "vpn";
    public static final String JAPAN_HOST = "public-vpn-187.opengw.net:443";
    public static final String JAPAN_ASSET = "japan.ovpn";

    public final String id;
    public final String name;
    public final String subtitle;
    public final String flag;
    /** Asset file name for the bundled server, null for imported servers. */
    public final String assetName;
    /** Internal-storage file name for imported servers, null for bundled. */
    public final String internalFileName;
    public final boolean imported;

    private VpnServer(String id, String name, String subtitle, String flag,
                      String assetName, String internalFileName, boolean imported) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.flag = flag;
        this.assetName = assetName;
        this.internalFileName = internalFileName;
        this.imported = imported;
    }

    public static VpnServer japan(Context context) {
        return new VpnServer(JAPAN_ID,
                context.getString(R.string.server_japan),
                context.getString(R.string.server_japan_subtitle) + " • " + JAPAN_HOST,
                "\uD83C\uDDEF\uD83C\uDDF5", // 🇯🇵
                JAPAN_ASSET, null, false);
    }

    public static VpnServer imported(Context context, String id, String name, String fileName) {
        return new VpnServer(id, name,
                context.getString(R.string.server_imported),
                "\uD83D\uDD12", // 🔒
                null, fileName, true);
    }

    /** Loads the full server list: Japan first, then the user's imported configs. */
    public static List<VpnServer> loadAll(Context context) {
        List<VpnServer> servers = new ArrayList<>();
        servers.add(japan(context));
        String raw = SpPrefs.prefs(context).getString(SpPrefs.KEY_IMPORTED, "");
        if (!raw.isEmpty()) {
            for (String entry : raw.split("\u001f")) {
                String[] parts = entry.split("\\|", 2);
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    servers.add(imported(context, "imported:" + parts[1], parts[0], parts[1]));
                }
            }
        }
        return servers;
    }

    /** Persists one imported server (name + internal file name only). */
    public static void saveImported(Context context, String name, String fileName) {
        String raw = SpPrefs.prefs(context).getString(SpPrefs.KEY_IMPORTED, "");
        String entry = name + "|" + fileName;
        String updated = raw.isEmpty() ? entry : raw + "\u001f" + entry;
        SpPrefs.prefs(context).edit().putString(SpPrefs.KEY_IMPORTED, updated).apply();
    }

    /** Deletes all imported config files and their saved metadata. */
    public static void clearImported(Context context) {
        for (VpnServer server : loadAll(context)) {
            if (server.imported && server.internalFileName != null) {
                File file = new File(new File(context.getFilesDir(), "imported"),
                        server.internalFileName);
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        SpPrefs.prefs(context).edit()
                .remove(SpPrefs.KEY_IMPORTED)
                .putString(SpPrefs.KEY_SELECTED_SERVER, JAPAN_ID)
                .apply();
    }

    public static VpnServer findById(Context context, String id) {
        for (VpnServer server : loadAll(context)) {
            if (server.id.equals(id)) return server;
        }
        return japan(context);
    }
}
