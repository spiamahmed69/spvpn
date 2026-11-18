package com.spsiam.spvpn;

import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.regex.Pattern;

/** SP VPN settings: theme, auto-reconnect, DNS, and imported-config cleanup. */
public class SettingsActivity extends AppCompatActivity {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}"
                    + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SpPrefs.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RadioGroup rgTheme = findViewById(R.id.rgTheme);
        RadioButton rbLight = findViewById(R.id.rbLight);
        RadioButton rbDark = findViewById(R.id.rbDark);
        RadioButton rbSystem = findViewById(R.id.rbSystem);

        String theme = SpPrefs.getTheme(this);
        if ("light".equals(theme)) {
            rbLight.setChecked(true);
        } else if ("dark".equals(theme)) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
        }

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            String newTheme;
            int mode;
            if (checkedId == R.id.rbLight) {
                newTheme = "light";
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rbDark) {
                newTheme = "dark";
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                newTheme = "system";
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }
            SpPrefs.prefs(this).edit().putString(SpPrefs.KEY_THEME, newTheme).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
            recreate();
        });

        SwitchMaterial switchAutoReconnect = findViewById(R.id.switchAutoReconnect);
        switchAutoReconnect.setChecked(SpPrefs.isAutoReconnect(this));
        switchAutoReconnect.setOnCheckedChangeListener((button, checked) ->
                SpPrefs.prefs(this).edit()
                        .putBoolean(SpPrefs.KEY_AUTO_RECONNECT, checked).apply());

        TextInputEditText etDnsPrimary = findViewById(R.id.etDnsPrimary);
        TextInputEditText etDnsSecondary = findViewById(R.id.etDnsSecondary);
        etDnsPrimary.setText(SpPrefs.getDnsPrimary(this));
        etDnsSecondary.setText(SpPrefs.getDnsSecondary(this));

        findViewById(R.id.btnSaveDns).setOnClickListener(v -> {
            String primary = textOf(etDnsPrimary);
            String secondary = textOf(etDnsSecondary);
            if (!IPV4.matcher(primary).matches()
                    || (!secondary.isEmpty() && !IPV4.matcher(secondary).matches())) {
                Toast.makeText(this, R.string.dns_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            SpPrefs.prefs(this).edit()
                    .putString(SpPrefs.KEY_DNS_PRIMARY, primary)
                    .putString(SpPrefs.KEY_DNS_SECONDARY, secondary)
                    .apply();
            Toast.makeText(this, R.string.dns_saved, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnClearImported).setOnClickListener(v -> {
            VpnServer.clearImported(this);
            Toast.makeText(this, R.string.clear_imported_done, Toast.LENGTH_SHORT).show();
        });
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
