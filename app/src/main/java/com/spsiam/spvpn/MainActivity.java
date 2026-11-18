package com.spsiam.spvpn;

import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import ai.bongotech.bongovpn.BongoVpn;

/**
 * SP VPN main screen.
 *
 * Real OpenVPN connection through the BongoVPN library (ai.bongotech:bongovpn).
 * Nothing here is simulated: the Connect button starts a real OpenVPN tunnel
 * with the selected .ovpn profile, and errors from the tunnel are shown as-is.
 * The only bundled endpoint is the public VPNGate relay from the upstream
 * sample; if it is down, import your own working .ovpn file.
 *
 * Security: .ovpn contents, usernames and passwords are never logged,
 * never shown in a Toast, and never written anywhere except the app's
 * private internal storage (imported configs only).
 */
public class MainActivity extends AppCompatActivity {

    private static final int RECONNECT_MAX_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY_MS = 5000;

    private BongoVpn bongoVpn;
    private MaterialButton btnConnect;
    private ProgressBar progressConnect;
    private TextView tvStatus, tvTimer, tvPublicIp, tvSelectedServer;
    private TextView tvDownloadSpeed, tvUploadSpeed, tvSessionUsage;
    private View viewStatusDot;
    private LinearLayout serverList;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long connectedAtMillis = 0;
    private boolean userRequestedDisconnect = false;
    private boolean switchingServer = false;
    private int reconnectAttempts = 0;
    private String lastError = "";
    private VpnServer selectedServer;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (connectedAtMillis > 0) {
                long seconds = (System.currentTimeMillis() - connectedAtMillis) / 1000;
                tvTimer.setText(String.format(Locale.US, "%02d:%02d:%02d",
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60));
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || userRequestedDisconnect) return;
            tvStatus.setText(R.string.status_connecting);
            attachCurrentServer();
            connectVpn();
        }
    };

    private final ActivityResultLauncher<String> importLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onConfigPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SpPrefs.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btnConnect = findViewById(R.id.btnConnect);
        progressConnect = findViewById(R.id.progressConnect);
        tvStatus = findViewById(R.id.tvStatus);
        tvTimer = findViewById(R.id.tvTimer);
        tvPublicIp = findViewById(R.id.tvPublicIp);
        tvSelectedServer = findViewById(R.id.tvSelectedServer);
        tvDownloadSpeed = findViewById(R.id.tvDownloadSpeed);
        tvUploadSpeed = findViewById(R.id.tvUploadSpeed);
        tvSessionUsage = findViewById(R.id.tvSessionUsage);
        viewStatusDot = findViewById(R.id.viewStatusDot);
        serverList = findViewById(R.id.serverList);

        bongoVpn = new BongoVpn(this);

        // Request notification permission on startup (not mandatory).
        if (!bongoVpn.hasNotificationPermission()) {
            bongoVpn.requestNotificationPermission();
        }

        selectedServer = VpnServer.findById(this, SpPrefs.getSelectedServerId(this));
        attachCurrentServer();
        rebuildServerList();
        updateSelectedServerLabel();
        fetchPublicIp();

        bongoVpn.setVpnListener(new BongoVpn.VpnListener() {
            @Override
            public void onVpnConnected() {
                reconnectAttempts = 0;
                handler.removeCallbacks(reconnectRunnable);
                connectedAtMillis = System.currentTimeMillis();
                handler.post(timerRunnable);
                setUiConnected();
                fetchPublicIp();
            }

            @Override
            public void onVpnStopped() {
                handler.removeCallbacks(timerRunnable);
                connectedAtMillis = 0;
                if (switchingServer) {
                    switchingServer = false;
                    setUiDisconnected(getString(R.string.status_ready));
                } else if (userRequestedDisconnect) {
                    setUiDisconnected(getString(R.string.status_disconnected));
                } else {
                    maybeReconnect();
                }
            }

            @Override
            public void onStatusUpdate(String status) {
                tvSessionUsage.setText(status);
            }

            @Override
            public void onError(String errorMessage) {
                lastError = errorMessage == null ? "" : errorMessage;
                handler.removeCallbacks(timerRunnable);
                connectedAtMillis = 0;
                if (switchingServer) {
                    switchingServer = false;
                    setUiDisconnected(getString(R.string.status_ready));
                } else if (userRequestedDisconnect) {
                    setUiDisconnected(getString(R.string.status_disconnected));
                } else {
                    maybeReconnect();
                }
            }

            @Override
            public void onSpeedUpdate(long downloadBytes, long uploadBytes,
                                      long downloadSpeed, long uploadSpeed) {
                tvDownloadSpeed.setText(bongoVpn.formatSpeed(downloadSpeed));
                tvUploadSpeed.setText(bongoVpn.formatSpeed(uploadSpeed));
                tvSessionUsage.setText(getString(R.string.session_usage) + ": "
                        + bongoVpn.formatBytes(downloadBytes) + " / "
                        + bongoVpn.formatBytes(uploadBytes));
            }
        });

        btnConnect.setOnClickListener(view -> {
            btnConnect.setEnabled(false);
            if (bongoVpn.isConnected()) {
                userRequestedDisconnect = true;
                handler.removeCallbacks(reconnectRunnable);
                tvStatus.setText(R.string.status_disconnecting);
                btnConnect.setText(R.string.disconnect);
                bongoVpn.stopVpn();
            } else {
                userRequestedDisconnect = false;
                reconnectAttempts = 0;
                tvStatus.setText(R.string.status_connecting);
                btnConnect.setText(R.string.connect);
                progressConnect.setVisibility(View.VISIBLE);
                connectVpn();
            }
        });

        findViewById(R.id.btnImport).setOnClickListener(v -> importLauncher.launch("*/*"));

        bongoVpn.showNotification()
                .title("SP VPN")
                .connectedText("Connected and Secured")
                .smallIcon(R.drawable.security_icon)
                .showSpeed(true)
                .targetActivity(MainActivity.class);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new android.content.Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new android.content.Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ VPN

    private void connectVpn() {
        if (selectedServer == null) {
            progressConnect.setVisibility(View.GONE);
            btnConnect.setEnabled(true);
            tvStatus.setText(R.string.error_no_server);
            return;
        }
        if (bongoVpn.hasVpnPermission()) {
            bongoVpn.startVpn();
        } else {
            progressConnect.setVisibility(View.GONE);
            btnConnect.setEnabled(true);
            tvStatus.setText(R.string.error_vpn_permission);
            bongoVpn.requestVpnPermission();
        }
    }

    /**
     * Attaches the selected profile to the library.
     * DNS is applied honestly: the BongoVPN library has no DNS setter, so
     * custom DNS is written into the OpenVPN config as "dhcp-option DNS"
     * lines and the profile is attached from a string. The VPN server may
     * push its own DNS and override these values.
     */
    private void attachCurrentServer() {
        if (selectedServer == null) return;
        try {
            if (!selectedServer.imported && !SpPrefs.hasCustomDns(this)) {
                bongoVpn.attachFromAsset(VpnServer.JAPAN_ASSET,
                        VpnServer.JAPAN_USERNAME, VpnServer.JAPAN_PASSWORD);
                return;
            }
            String config = readSelectedConfigText();
            if (config == null) {
                tvStatus.setText(R.string.import_failed);
                return;
            }
            if (SpPrefs.hasCustomDns(this)) {
                config = applyDnsToConfig(config);
            }
            if (selectedServer.imported) {
                // Imported configs carry no known credentials; pass null so
                // the library does not send a username/password.
                bongoVpn.attachFromString(config, null, null);
            } else {
                bongoVpn.attachFromString(config,
                        VpnServer.JAPAN_USERNAME, VpnServer.JAPAN_PASSWORD);
            }
        } catch (Exception e) {
            tvStatus.setText(getString(R.string.error_generic, e.getMessage()));
        }
    }

    private String readSelectedConfigText() throws Exception {
        if (selectedServer.imported) {
            File file = new File(new File(getFilesDir(), "imported"),
                    selectedServer.internalFileName);
            try (InputStream in = new java.io.FileInputStream(file)) {
                return readAll(in);
            }
        } else {
            try (InputStream in = getAssets().open(VpnServer.JAPAN_ASSET)) {
                return readAll(in);
            }
        }
    }

    private String applyDnsToConfig(String config) {
        StringBuilder sb = new StringBuilder(config);
        sb.append("\ndhcp-option DNS ").append(SpPrefs.getDnsPrimary(this)).append("\n");
        String secondary = SpPrefs.getDnsSecondary(this);
        if (secondary != null && !secondary.trim().isEmpty()) {
            sb.append("dhcp-option DNS ").append(secondary.trim()).append("\n");
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Retries after an unexpected drop, up to 3 times, 5 seconds apart. */
    private void maybeReconnect() {
        if (!SpPrefs.isAutoReconnect(this) || userRequestedDisconnect) {
            setUiDisconnected(getString(R.string.error_generic, lastError));
            return;
        }
        if (reconnectAttempts < RECONNECT_MAX_ATTEMPTS) {
            reconnectAttempts++;
            tvStatus.setText(getString(R.string.status_reconnecting, reconnectAttempts));
            progressConnect.setVisibility(View.VISIBLE);
            btnConnect.setEnabled(false);
            handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        } else {
            setUiDisconnected(getString(R.string.reconnect_failed, lastError));
        }
    }

    // ------------------------------------------------------------------ UI

    private void setUiConnected() {
        progressConnect.setVisibility(View.GONE);
        btnConnect.setEnabled(true);
        btnConnect.setText(R.string.disconnect);
        tvStatus.setText(R.string.status_connected);
        viewStatusDot.setBackgroundTintList(
                ColorStateList.valueOf(getColor(R.color.green)));
        tvTimer.setText("00:00:00");
        rebuildServerList();
    }

    private void setUiDisconnected(String statusText) {
        progressConnect.setVisibility(View.GONE);
        btnConnect.setEnabled(true);
        btnConnect.setText(R.string.connect);
        tvStatus.setText(statusText);
        viewStatusDot.setBackgroundTintList(
                ColorStateList.valueOf(getColor(R.color.red)));
        tvTimer.setText("00:00:00");
        tvDownloadSpeed.setText("0.0 KB/s");
        tvUploadSpeed.setText("0.0 KB/s");
        rebuildServerList();
    }

    private void rebuildServerList() {
        serverList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        List<VpnServer> servers = VpnServer.loadAll(this);
        boolean connected = bongoVpn != null && bongoVpn.isConnected();
        for (VpnServer server : servers) {
            View card = inflater.inflate(R.layout.item_server, serverList, false);
            ((TextView) card.findViewById(R.id.tvFlag)).setText(server.flag);
            ((TextView) card.findViewById(R.id.tvServerName)).setText(server.name);
            ((TextView) card.findViewById(R.id.tvServerHost)).setText(server.subtitle);
            View dot = card.findViewById(R.id.viewServerDot);
            boolean isSelected = selectedServer != null && selectedServer.id.equals(server.id);
            dot.setBackgroundTintList(ColorStateList.valueOf(getColor(
                    connected && isSelected ? R.color.green : R.color.red)));
            MaterialCardView cardView = (MaterialCardView) card;
            cardView.setStrokeColor(ColorStateList.valueOf(getColor(
                    isSelected ? R.color.accent_blue : R.color.card_stroke)));
            cardView.setStrokeWidth(isSelected ? 3 : 1);
            card.setOnClickListener(v -> selectServer(server));
            serverList.addView(card);
        }
    }

    private void updateSelectedServerLabel() {
        if (selectedServer != null) {
            tvSelectedServer.setText(getString(R.string.selected_server, selectedServer.name));
        }
    }

    private void selectServer(VpnServer server) {
        boolean wasConnected = bongoVpn != null && bongoVpn.isConnected();
        if (wasConnected) {
            // Suppress auto-reconnect for the stop that belongs to this switch.
            switchingServer = true;
            userRequestedDisconnect = false;
            handler.removeCallbacks(reconnectRunnable);
            bongoVpn.stopVpn();
        }
        selectedServer = server;
        SpPrefs.prefs(this).edit()
                .putString(SpPrefs.KEY_SELECTED_SERVER, server.id).apply();
        attachCurrentServer();
        updateSelectedServerLabel();
        rebuildServerList();
        if (!wasConnected) {
            tvStatus.setText(R.string.status_ready);
        }
    }

    // ------------------------------------------------------- Public IP (real)

    /** Fetches the current public IP from ipify on a background thread. */
    private void fetchPublicIp() {
        tvPublicIp.setText(getString(R.string.public_ip) + ": "
                + getString(R.string.public_ip_fetching));
        new Thread(() -> {
            String ip = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.ipify.org?format=json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (InputStream in = connection.getInputStream()) {
                    String body = readAll(in);
                    JSONObject json = new JSONObject(body);
                    ip = json.optString("ip", null);
                }
            } catch (Exception ignored) {
                ip = null; // shown as Unavailable, never faked
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String result = ip;
            runOnUiThread(() -> tvPublicIp.setText(getString(R.string.public_ip) + ": "
                    + (result == null || result.isEmpty()
                    ? getString(R.string.public_ip_unknown) : result)));
        }).start();
    }

    // ------------------------------------------------------------- Import

    private void onConfigPicked(Uri uri) {
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            String config = readAll(in);
            // Same validation rule the library itself applies.
            if (!config.contains("client") || !config.contains("<ca>")) {
                Toast.makeText(this, R.string.import_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            String displayName = displayNameOf(uri);
            String fileName = "imported_" + System.currentTimeMillis() + ".ovpn";
            File dir = new File(getFilesDir(), "imported");
            if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(new File(dir, fileName))) {
                out.write(config.getBytes(StandardCharsets.UTF_8));
            }
            VpnServer.saveImported(this, displayName, fileName);
            VpnServer imported = VpnServer.imported(this, "imported:" + fileName, displayName, fileName);
            Toast.makeText(this, getString(R.string.import_success, displayName),
                    Toast.LENGTH_SHORT).show();
            selectServer(imported);
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String displayNameOf(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
            name = null;
        }
        if (name == null || name.isEmpty()) name = getString(R.string.server_imported);
        if (name.toLowerCase(Locale.US).endsWith(".ovpn")) {
            name = name.substring(0, name.length() - 5);
        }
        return name;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(reconnectRunnable);
        if (bongoVpn != null) bongoVpn.release();
        super.onDestroy();
    }
}
