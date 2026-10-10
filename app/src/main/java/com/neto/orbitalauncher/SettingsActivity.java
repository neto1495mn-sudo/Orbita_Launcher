package com.neto.orbitalauncher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;

import com.neto.orbitalauncher.theme.ThemeApplier;
import com.neto.orbitalauncher.theme.ThemeManager;
import com.neto.orbitalauncher.theme.ThemedDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SharedPreferences categoryPrefs;

    // Icon scale values from old launcher (82, 99, 125, 165, 236 dp)
    // These map to seekbar positions 0-100
    private static final int[] ICON_SCALES_DP = {90, 110, 140, 180, 236};
    private static final int DEFAULT_SCALE_INDEX = 3;  // 180dp
    private static final int ICON_SIZE_MIN_DP = 90;
    private static final int ICON_SIZE_MAX_DP = 236;

    // Broadcast action for theme changes
    // Broadcast action for category changes (create/rename/delete/modify)
    private static final String ACTION_CATEGORIES_CHANGED = "com.neto.orbitalauncher.CATEGORIES_CHANGED";

    // Meta standard window size (1024x640 dp)
    private static final int META_STANDARD_WIDTH_DP = 1024;
    private static final int META_STANDARD_HEIGHT_DP = 640;

    // Shizuku manager for shell-level commands
    private ShizukuManager shizukuManager;

    private static final String PREFS_NAME = "VRLPrefs";
    private static final String CATEGORY_PREFS = "vr_categories";

    private static final String KEY_EDIT_MODE = "edit_mode";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_ICON_SIZE_SCALE = "icon_size_scale";  // Store scale index (0-4)
    private static final String KEY_BG_OPACITY = "background_opacity";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_SUPPRESS_STORE = "suppress_store";

    private static final int REQUEST_CODE_CREATE_BACKUP = 200;
    private static final int REQUEST_CODE_OPEN_BACKUP = 201;
    private static final int REQUEST_CODE_USAGE_ACCESS = 202;

    /**
     * Tracks whether we're temporarily losing focus because we launched
     * a sub-activity (file picker, device info, etc.). When true, we
     * don't auto-close on focus loss because the user will return.
     */
    private boolean expectingFocusReturn = false;
    private boolean isTopResumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        categoryPrefs = getSharedPreferences(CATEGORY_PREFS, MODE_PRIVATE);

        // Initialize all views
        SwitchCompat switchEditMode = findViewById(R.id.switchEditMode);
        SeekBar seekIconSize = findViewById(R.id.seekIconSize);
        TextView txtIconSize = findViewById(R.id.txtIconSize);
        TextView txtIconSizeRange = findViewById(R.id.txtIconSizeRange);
        SeekBar seekBgOpacity = findViewById(R.id.seekBgOpacity);
        TextView txtBgOpacity = findViewById(R.id.txtBgOpacity);
        AppCompatButton btnManageCategories = findViewById(R.id.btnManageCategories);
        View btnBack = findViewById(R.id.btnBack);
        AppCompatButton btnGameStats = findViewById(R.id.btnGameStats);
        AppCompatButton btnDeviceInfo = findViewById(R.id.btnDeviceInfo);
        AppCompatButton btnUsageAccess = findViewById(R.id.btnUsageAccess);
        SwitchCompat switchAutoStart = findViewById(R.id.switchAutoStart);

        // Status do gatilho por foco (o servico de acessibilidade)
        TextView lblHoverTriggerStatus = findViewById(R.id.lblHoverTriggerStatus);

        // ADD BACKUP/RESTORE BUTTONS
        Button btnBackup = findViewById(R.id.btnBackup);
        Button btnRestore = findViewById(R.id.btnRestore);

        // ADD SYSTEM ACCESS BUTTON (Native Settings)
        Button btnNativeSettings = findViewById(R.id.btnNativeSettings);

        // Set initial values
        switchEditMode.setChecked(prefs.getBoolean(KEY_EDIT_MODE, false));
        switchAutoStart.setChecked(prefs.getBoolean(KEY_AUTO_START, true));

        // Update boot status label to reflect actual state
        TextView autoRestartStatus = findViewById(R.id.autoRestartStatus);
        if (autoRestartStatus != null) {
            boolean bootEnabled = prefs.getBoolean(KEY_AUTO_START, true);
            autoRestartStatus.setText(getString(bootEnabled ? R.string.set_status_on : R.string.set_status_off));
            autoRestartStatus.setTextColor(bootEnabled ? 0xFF66BB6A : 0xFF9A9BA0);
        }

        // Setup Icon Size SeekBar - maps to old launcher scale values (82-236 dp)
        if (seekIconSize != null && txtIconSize != null) {
            // Get current scale index (0-4) and convert to seekbar progress (0-100)
            int currentScaleIndex = prefs.getInt(KEY_ICON_SIZE_SCALE, DEFAULT_SCALE_INDEX);
            int seekProgress = convertScaleIndexToSeekProgress(currentScaleIndex);

            seekIconSize.setProgress(seekProgress);
            int currentIconSizeDp = ICON_SCALES_DP[currentScaleIndex];
            txtIconSize.setText(currentIconSizeDp + "dp");

            // Show range text
            if (txtIconSizeRange != null) {
                txtIconSizeRange.setText(ICON_SIZE_MIN_DP + "dp - " + ICON_SIZE_MAX_DP + "dp");
            }

            seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Convert progress (0-100) to actual dp using the old launcher scale mapping
                    int iconSizeDp = convertSeekProgressToIconSizeDp(progress);
                    txtIconSize.setText(iconSizeDp + "dp");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    int iconSizeDp = convertSeekProgressToIconSizeDp(progress);
                    int scaleIndex = convertIconSizeDpToScaleIndex(iconSizeDp);

                    // Save both the scale index and the actual size
                    prefs.edit()
                            .putInt(KEY_ICON_SIZE_SCALE, scaleIndex)
                            .putInt(KEY_ICON_SIZE, iconSizeDp)
                            .apply();

                    // Apply to MainActivity
                    if (MainActivity.instance != null) {
                        MainActivity.instance.updateIconSizes();
                    }

                    Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.set_toast_icon_size, iconSizeDp), Toast.LENGTH_SHORT).show();
                }
            });
        }

        int opacity = prefs.getInt(KEY_BG_OPACITY, 100);
        seekBgOpacity.setProgress(opacity);
        txtBgOpacity.setText(opacity + "%");

        // Set up listeners
        switchEditMode.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_EDIT_MODE, c).apply();
            if (MainActivity.instance != null) MainActivity.instance.refreshEditMode();
        });

        SwitchCompat switchReopen = findViewById(R.id.switchReopenOnClose);
        if (switchReopen != null) {
            switchReopen.setChecked(prefs.getBoolean("reopen_on_close", false));
            switchReopen.setOnCheckedChangeListener((b, c) -> {
                prefs.edit().putBoolean("reopen_on_close", c).apply();
                Toast.makeText(this, getString(c ? R.string.set_toast_reopen_on : R.string.set_toast_reopen_off), Toast.LENGTH_SHORT).show();
                // "Reabrir ao fechar" depende do gatilho por foco: liga os dois juntos
                if (c) enableFocusTrigger();
            });
        }

        switchAutoStart.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_AUTO_START, c).apply();
            TextView bootStatus = findViewById(R.id.autoRestartStatus);
            if (bootStatus != null) {
                bootStatus.setText(getString(c ? R.string.set_status_on : R.string.set_status_off));
                bootStatus.setTextColor(c ? 0xFF66BB6A : 0xFF9A9BA0);
            }
            Toast.makeText(this, getString(c ? R.string.set_toast_autostart_on : R.string.set_toast_autostart_off), Toast.LENGTH_SHORT).show();
        });

        // Suppress Meta Store toggle
        androidx.appcompat.widget.SwitchCompat switchSuppressStore = findViewById(R.id.switchSuppressStore);
        if (switchSuppressStore != null) {
            switchSuppressStore.setChecked(prefs.getBoolean(KEY_SUPPRESS_STORE, false));
            switchSuppressStore.setOnCheckedChangeListener((b, c) -> {
                prefs.edit().putBoolean(KEY_SUPPRESS_STORE, c).apply();
                Toast.makeText(this,
                        getString(c ? R.string.set_toast_store_on : R.string.set_toast_store_off),
                        Toast.LENGTH_SHORT).show();
            });
        }


        seekBgOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                txtBgOpacity.setText(p + "%");
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.edit().putInt(KEY_BG_OPACITY, sb.getProgress()).apply();
                if (MainActivity.instance != null) MainActivity.instance.updateBackground();
            }
        });

        updateSettingsIP();

        btnDeviceInfo.setOnClickListener(v -> {
            startActivity(new Intent(this, DeviceInfoActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnManageCategories.setOnClickListener(v -> showCategoryManager());

        refreshHoverTriggerStatus(lblHoverTriggerStatus);
        btnBack.setOnClickListener(v -> finish());

        btnGameStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, PlaytimeStatsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // Set initial label based on current permission state
        updateUsageAccessButton(btnUsageAccess);

        btnUsageAccess.setOnClickListener(v -> requestUsageAccessPermission(btnUsageAccess));

        // ADD BACKUP/RESTORE BUTTON LISTENERS
        btnBackup.setOnClickListener(v -> backupLayout());
        btnRestore.setOnClickListener(v -> restoreLayout());

        setupImageSource();

        // Refresh Icons - clears Glide cache and reloads all cover art
        Button btnRefreshIcons = findViewById(R.id.btnRefreshIcons);
        if (btnRefreshIcons != null) {
            btnRefreshIcons.setOnClickListener(v -> {
                btnRefreshIcons.setEnabled(false);
                btnRefreshIcons.setText(R.string.set_refreshing);
                com.bumptech.glide.Glide.get(this).clearMemory();
                new Thread(() -> {
                    com.bumptech.glide.Glide.get(this).clearDiskCache();
                    StoreImageManager.clearSaved(this);
                    runOnUiThread(() -> {
                        btnRefreshIcons.setEnabled(true);
                        btnRefreshIcons.setText(R.string.set_refresh_icons);
                        Toast.makeText(this, getString(R.string.set_toast_icons_cleared), Toast.LENGTH_SHORT).show();
                    });
                }).start();
            });
        }

        // ADD QUEST UTILITIES BUTTON LISTENERS
        Button btnRestartUI = findViewById(R.id.btnRestartUI);

        Button btnShizuku = findViewById(R.id.btnShizuku);
        if (btnShizuku != null) {
            btnShizuku.setOnClickListener(v -> onShizukuButtonClicked());
        }
        if (btnRestartUI != null) {
            btnRestartUI.setOnClickListener(v -> restartQuestUI());
        }

        // APP MANAGER BUTTON
        Button btnAppManager = findViewById(R.id.btnAppManager);
        if (btnAppManager != null) {
            btnAppManager.setOnClickListener(v -> {
                Intent appManagerIntent = new Intent(this, AppManagerActivity.class);
                startActivity(appManagerIntent);
            });
        }

        // Initialize Shizuku for shell commands
        initializeShizuku();

        // Setup version display and update checker
        setupVersionAndUpdates();

        // Barra lateral: troca a secao mostrada a direita
        setupSectionNav();

        // Apply theme FIRST to entire activity
        View rootView = findViewById(android.R.id.content);
        ThemeApplier.applyThemeToHierarchy(rootView);

        // THEN setup Native Settings button (red/green) - happens after theme so colors persist
        if (btnNativeSettings != null) {
            updateNativeSettingsButton(btnNativeSettings);
        }
    }

    /**
     * Set window size to match the launching activity (MainActivity).
     * MainActivity passes its current window dimensions via Intent extras
     * (window_width, window_height) so the Settings panel renders at the
     * same size as the main launcher, even when the launcher has been
     * resized in Horizon Home.
     *
     * Falls back to Meta's standard size (1024x640 dp) if extras aren't
     * provided (e.g., direct launch via adb).
     */
    private void setMetaStandardWindowSize() {
        Window window = getWindow();
        float density = getResources().getDisplayMetrics().density;

        // Try to read window dimensions passed from MainActivity
        int widthPx = getIntent().getIntExtra("window_width", 0);
        int heightPx = getIntent().getIntExtra("window_height", 0);

        Log.d("SettingsActivity", "Intent extras: " + widthPx + "x" + heightPx + "px");

        // Sanity check - if dimensions are too small (e.g., 0 or phone-size),
        // fall back to Meta standard. This catches cases where the launcher
        // hadn't fully laid out at click time.
        int minAcceptablePx = (int) (700 * density);  // ~700dp minimum
        if (widthPx < minAcceptablePx || heightPx < minAcceptablePx) {
            widthPx = (int) (META_STANDARD_WIDTH_DP * density);
            heightPx = (int) (META_STANDARD_HEIGHT_DP * density);
            Log.d("SettingsActivity", "Using fallback Meta standard: " + widthPx + "x" + heightPx + "px");
        } else {
            Log.d("SettingsActivity", "Using MainActivity dimensions: " + widthPx + "x" + heightPx + "px");
        }

        // Apply via both setAttributes AND setLayout for maximum effect.
        // setAttributes alone can be overridden by the dialog theme's window
        // constraints; setLayout forces the size unconditionally.
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        params.width = widthPx;
        params.height = heightPx;
        params.gravity = android.view.Gravity.CENTER;
        window.setAttributes(params);
        window.setLayout(widthPx, heightPx);

        // Set minimum size so user can't shrink it below comfortable usability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int minWidthPx = (int) (800 * density);
            int minHeightPx = (int) (500 * density);
            window.getDecorView().setMinimumWidth(minWidthPx);
            window.getDecorView().setMinimumHeight(minHeightPx);
        }

        Log.d("SettingsActivity", "Final window size: " + widthPx + "x" + heightPx + "px");
    }

    /**
     * Tell MainActivity that categories have changed (create/rename/delete/modify).
     * MainActivity will rebuild the category bar and refresh the app cards
     * so removed categories disappear immediately without needing a refresh.
     */
    private void sendCategoriesChangedBroadcast() {
        Intent intent = new Intent(ACTION_CATEGORIES_CHANGED);
        // Explicit package - required on Android 13+ for receivers in separate tasks
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        Log.d("SettingsActivity", "Categories change broadcast sent to " + getPackageName());
    }

    /**
     * Convert seekbar progress (0-100) to icon size in dp using the old launcher scale mapping
     * This maps to the 5 discrete values: 82, 99, 125, 165, 236
     */
    private int convertSeekProgressToIconSizeDp(int progress) {
        // Map progress ranges to the 5 discrete values
        if (progress <= 20) {
            return ICON_SCALES_DP[0]; // 90dp
        } else if (progress <= 40) {
            return ICON_SCALES_DP[1]; // 110dp
        } else if (progress <= 60) {
            return ICON_SCALES_DP[2]; // 140dp
        } else if (progress <= 80) {
            return ICON_SCALES_DP[3]; // 180dp
        } else {
            return ICON_SCALES_DP[4]; // 236dp
        }
    }

    /**
     * Convert scale index (0-4) to seekbar progress (0-100)
     */
    private int convertScaleIndexToSeekProgress(int scaleIndex) {
        switch (scaleIndex) {
            case 0: return 10;  // 90dp
            case 1: return 30;  // 110dp
            case 2: return 50;  // 140dp (default)
            case 3: return 70;  // 180dp
            case 4: return 90;  // 236dp
            default: return 50;
        }
    }

    /**
     * Convert icon size dp to the closest scale index (0-4)
     */
    private int convertIconSizeDpToScaleIndex(int iconSizeDp) {
        int closestIndex = DEFAULT_SCALE_INDEX;
        int smallestDiff = Math.abs(iconSizeDp - ICON_SCALES_DP[DEFAULT_SCALE_INDEX]);

        for (int i = 0; i < ICON_SCALES_DP.length; i++) {
            int diff = Math.abs(iconSizeDp - ICON_SCALES_DP[i]);
            if (diff < smallestDiff) {
                smallestDiff = diff;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    /**
     * Native Settings button - launches Quest's Android Settings directly
     * Uses the gotosettings approach (https://github.com/arpruss/gotosettings)
     * No external APK needed - just uses standard Android intents
     */
    private static final String SETTINGS_PACKAGE = "com.android.settings";

    private void updateNativeSettingsButton(Button btn) {
        // Mark button to be ignored by theme system - we manage colors manually
        btn.setTag("theme_ignore");

        // Always show "Abrir" - Settings is built into Android, always available
        btn.setText(R.string.set_open);
        btn.setBackgroundResource(R.drawable.orbita_bg_pill);
        btn.setBackgroundTintList(null);
        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setOnClickListener(v -> goToSettings());
    }

    /**
     * Launch Quest's native Android Settings.
     * Shows setup instructions dialog. Settings only opens from this dialog,
     * and the dialog STAYS OPEN so the user can refer to it while configuring.
     */
    private void goToSettings() {
        // Build the dialog
        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_dev_title)
                .setMessage(R.string.set_dev_message)
                // Listeners null - we override AFTER show so dialog doesn't auto-dismiss
                .setPositiveButton(R.string.set_open_settings, null)
                .setNegativeButton(R.string.set_close, null)
                .setCancelable(true)
                .create();

        // Show with theme applied (this fires its own OnShowListener for theming)
        ThemedDialog.showThemed(dialog);

        // NOW set our button click overrides (after the dialog is shown and themed)
        // This must happen AFTER showThemed because that sets its own OnShowListener
        Button openBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        if (openBtn != null) {
            openBtn.setOnClickListener(v -> {
                openSettingsDirectly();
                Toast.makeText(this, getString(R.string.set_toast_window_stays_open), Toast.LENGTH_SHORT).show();
                // NOTE: NOT calling dialog.dismiss() - keeps dialog visible for reference
            });
        }

        Button closeBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dialog.dismiss());
        }
    }

    /**
     * Actually open Settings - tries Shizuku first, falls back to intent.
     *
     * Tries 3 methods in order of preference:
     * 1. Shizuku 'am start' - DIRECT, no extra screens (best UX)
     * 2. Standard launch intent with TASK_ON_HOME flag (gotosettings approach)
     * 3. Application details fallback
     */
    private void openSettingsDirectly() {
        // Method 1: Try Shizuku for direct launch (best UX - no extra clicks)
        if (shizukuManager != null && shizukuManager.isReady()) {
            try {
                // DeepLinkHomepageActivity opens Settings directly without App Info screen
                shizukuManager.executeShellCommand(
                        "am start -n com.android.settings/.homepage.DeepLinkHomepageActivity"
                );
                return;
            } catch (Exception e) {
                android.util.Log.w("SettingsActivity", "Shizuku launch failed, trying intent", e);
            }
        }

        // Method 2: Standard launch intent (gotosettings approach - may show App Info on v81+)
        PackageManager pm = getPackageManager();
        try {
            Intent i = pm.getLaunchIntentForPackage(SETTINGS_PACKAGE);
            if (i != null) {
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                startActivity(i);
                return;
            }
            throw new Exception("No launch intent");
        } catch (Exception e) {
            // Method 3: Application details fallback
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + SETTINGS_PACKAGE));
                i.setPackage(SETTINGS_PACKAGE);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(this, getString(R.string.set_toast_android_settings_fail), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ===== SECOES (barra lateral) =====

    private static final String KEY_LAST_SECTION = "settings_last_section";

    /** Itens da barra lateral, na mesma ordem de SECTION_IDS e SECTION_TITLES. */
    private static final int[] NAV_IDS = {
            R.id.settingsNavGeneral, R.id.settingsNavAppearance, R.id.settingsNavApps,
            R.id.settingsNavQuest, R.id.settingsNavPlaytime, R.id.settingsNavUpdates,
            R.id.settingsNavBackup
    };
    private static final int[] SECTION_IDS = {
            R.id.settingsSectionGeneral, R.id.settingsSectionAppearance, R.id.settingsSectionApps,
            R.id.settingsSectionQuest, R.id.settingsSectionPlaytime, R.id.settingsSectionUpdates,
            R.id.settingsSectionBackup
    };
    private static final int[] SECTION_TITLES = {
            R.string.set_nav_general, R.string.set_nav_appearance, R.string.set_nav_apps,
            R.string.set_nav_quest, R.string.set_nav_playtime, R.string.set_nav_updates,
            R.string.set_nav_backup
    };

    private void setupSectionNav() {
        for (int i = 0; i < NAV_IDS.length; i++) {
            View item = findViewById(NAV_IDS[i]);
            if (item == null) continue;
            final int index = i;
            item.setOnClickListener(v -> showSection(index));
        }
        showSection(prefs.getInt(KEY_LAST_SECTION, 0));
    }

    /** Mostra so a secao escolhida, marca o item da barra lateral e lembra a escolha. */
    private void showSection(int index) {
        if (index < 0 || index >= SECTION_IDS.length) index = 0;
        for (int i = 0; i < SECTION_IDS.length; i++) {
            View section = findViewById(SECTION_IDS[i]);
            if (section != null) section.setVisibility(i == index ? View.VISIBLE : View.GONE);
            View item = findViewById(NAV_IDS[i]);
            if (item != null) item.setSelected(i == index);
        }
        TextView title = findViewById(R.id.settingsSectionTitle);
        if (title != null) title.setText(SECTION_TITLES[index]);
        View scroll = findViewById(R.id.settingsScroll);
        if (scroll != null) scroll.scrollTo(0, 0);
        prefs.edit().putInt(KEY_LAST_SECTION, index).apply();
    }

    // ===== USAGE ACCESS PERMISSION =====

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check permission state when user returns from the settings screen
        AppCompatButton btnUsageAccess = findViewById(R.id.btnUsageAccess);
        if (btnUsageAccess != null) {
            updateUsageAccessButton(btnUsageAccess);
        }
        // Atualiza o texto do botao do Shizuku (pode ter sido instalado/iniciado fora daqui)
        updateShizukuButton();

        // Re-apply theme FIRST in case it changed
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ThemeApplier.applyThemeToHierarchy(rootView);
        }

        // THEN re-check Native Settings install state (overrides theme colors)
        Button btnNativeSettings = findViewById(R.id.btnNativeSettings);
        if (btnNativeSettings != null) {
            updateNativeSettingsButton(btnNativeSettings);
        }

        // Re-check hover-trigger accessibility state - the user may have
        // come back from toggling it in Quest's accessibility settings.
        refreshHoverTriggerStatus(findViewById(R.id.lblHoverTriggerStatus));
    }

    /**
     * Mostra no texto de status se o gatilho por foco (EvolveAccessibilityService) esta ligado.
     */
    private void refreshHoverTriggerStatus(TextView statusLabel) {
        if (statusLabel == null) return;
        if (AccessibilityServiceHelper.isEnabled(this)) {
            statusLabel.setText(R.string.set_hover_status_on);
            statusLabel.setTextColor(0xFF66BB6A); // light green
        } else {
            statusLabel.setText(R.string.set_hover_status_off);
            statusLabel.setTextColor(0xFF888888); // gray
        }
    }

    // Marca que o proximo resultado do Shizuku e o do comando que liga o gatilho
    private boolean pendingFocusTriggerEnable = false;

    /**
     * Liga o gatilho por foco. Com o Shizuku pronto, liga sozinho (mesma permissao do ADB);
     * sem ele, o Android nao deixa o app ligar a acessibilidade, entao abrimos a tela certa
     * e explicamos o que tocar.
     */
    private void enableFocusTrigger() {
        if (AccessibilityServiceHelper.isEnabled(this)) return;

        if (shizukuManager != null && shizukuManager.isReady()) {
            String comp = new android.content.ComponentName(this, EvolveAccessibilityService.class).flattenToString();
            String cmd = "cur=$(settings get secure enabled_accessibility_services); "
                    + "case \"$cur\" in "
                    + "*" + comp + "*) ;; "
                    + "''|null) settings put secure enabled_accessibility_services " + comp + " ;; "
                    + "*) settings put secure enabled_accessibility_services \"$cur:" + comp + "\" ;; "
                    + "esac; settings put secure accessibility_enabled 1";
            pendingFocusTriggerEnable = true;
            shizukuManager.executeShellCommand(cmd);
            return;
        }

        AccessibilityServiceHelper.openSettings(this);
        Toast.makeText(this, getString(R.string.set_toast_hover_find), Toast.LENGTH_LONG).show();
    }

    private void onFocusTriggerCommandResult() {
        pendingFocusTriggerEnable = false;
        refreshHoverTriggerStatus(findViewById(R.id.lblHoverTriggerStatus));
        if (AccessibilityServiceHelper.isEnabled(this)) {
            Toast.makeText(this, getString(R.string.set_toast_trigger_enabled), Toast.LENGTH_SHORT).show();
        } else {
            // O comando nao funcionou: cai no caminho manual
            AccessibilityServiceHelper.openSettings(this);
            Toast.makeText(this, getString(R.string.set_toast_hover_find), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void updateUsageAccessButton(AppCompatButton btn) {
        if (hasUsageStatsPermission()) {
            btn.setText(R.string.set_usage_granted);
            btn.setBackgroundResource(R.drawable.orbita_bg_pill);
        } else {
            // Falta a permissao: botao em destaque (azul) para chamar atencao
            btn.setText(R.string.set_usage_grant_btn);
            btn.setBackgroundResource(R.drawable.orbita_bg_pill_accent);
        }
        btn.setSupportBackgroundTintList(null);
    }

    private void requestUsageAccessPermission(AppCompatButton btn) {
        if (hasUsageStatsPermission()) {
            Toast.makeText(this, getString(R.string.set_toast_usage_already), Toast.LENGTH_SHORT).show();
            return;
        }
        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_usage_dialog_title)
                .setMessage(R.string.set_usage_dialog_message)
                .setPositiveButton(R.string.set_open_settings, (d, w) -> {
                    try {
                        // Deep-link directly to this app's usage access entry (avoids Quest 2 list crash)
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_CODE_USAGE_ACCESS);
                    } catch (ActivityNotFoundException e) {
                        // Fallback to the general usage access list
                        try {
                            startActivityForResult(
                                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    REQUEST_CODE_USAGE_ACCESS);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(this, getString(R.string.set_toast_settings_screen_unavailable), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    // ===== BACKUP & RESTORE METHODS =====

    private void backupLayout() {
        try {
            JSONObject backup = new JSONObject();

            // Save VRLPrefs - preserve types properly
            JSONObject prefsData = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();

                if (value instanceof Boolean) {
                    prefsData.put(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    prefsData.put(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    prefsData.put(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    prefsData.put(entry.getKey(), (Float) value);
                } else if (value instanceof Set) {
                    continue;
                } else {
                    prefsData.put(entry.getKey(), String.valueOf(value));
                }
            }
            backup.put("vrprefs", prefsData);

            // Save categories
            JSONObject categoriesData = new JSONObject();
            for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Set) {
                    JSONArray setArray = new JSONArray((Set) entry.getValue());
                    categoriesData.put(entry.getKey(), setArray);
                }
            }
            backup.put("categories", categoriesData);

            String fileName = "vrlauncher_backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";

            saveBackupDirectly(backup, fileName);

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.set_toast_backup_fail, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBackupDirectly(JSONObject backup, String fileName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    ThemedDialog.showThemed(new AlertDialog.Builder(this)
                            .setTitle(R.string.set_storage_perm_title)
                            .setMessage(R.string.set_storage_perm_message)
                            .setPositiveButton(R.string.set_grant_permission, (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                    Toast.makeText(this, getString(R.string.set_toast_enable_all_files), Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    android.util.Log.e("SettingsActivity", "Failed to open settings", e);
                                    Toast.makeText(this, getString(R.string.set_toast_enable_storage_manual), Toast.LENGTH_LONG).show();
                                }
                            })
                            .setNegativeButton(R.string.set_cancel, null)
                            .create());
                    return;
                }
            }

            File externalStorage = Environment.getExternalStorageDirectory();
            File backupDir = new File(externalStorage, "evolve_backups");

            if (!backupDir.exists()) {
                boolean created = backupDir.mkdirs();
                if (!created && !backupDir.exists()) {
                    throw new Exception(getString(R.string.set_err_create_dir));
                }
            }

            if (!backupDir.exists() || !backupDir.canWrite()) {
                throw new Exception(getString(R.string.set_err_dir_not_writable));
            }

            File backupFile = new File(backupDir, fileName);
            FileOutputStream fos = new FileOutputStream(backupFile);
            fos.write(backup.toString(2).getBytes());
            fos.flush();
            fos.close();

            try {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.set_backup_done_title)
                        .setMessage(getString(R.string.set_backup_saved_dialog, fileName))
                        .setPositiveButton(R.string.set_ok, null)
                        .create();
                ThemedDialog.showThemed(dialog);
            } catch (Exception dialogEx) {
                Toast.makeText(this, getString(R.string.set_backup_saved_toast, fileName), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Fallback backup failed: " + e.getMessage(), e);
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle(R.string.set_backup_fail_title)
                    .setMessage(e.getMessage())
                    .setPositiveButton(R.string.set_ok, null)
                    .create());
        }
    }

    private void restoreLayout() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        startActivityForResult(intent, REQUEST_CODE_OPEN_BACKUP);
    }

    // ===== AUTO-CLOSE WHEN FOCUS RETURNS TO LAUNCHER =====

    @Override
    public void startActivity(Intent intent) {
        // Mark that we're launching a sub-activity so the auto-close logic
        // doesn't fire us while we're temporarily in the background.
        expectingFocusReturn = true;
        super.startActivity(intent);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        expectingFocusReturn = true;
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Called when this activity gains or loses the "top resumed activity"
     * status. This is the right signal for auto-closing: it fires when
     * ANOTHER activity becomes top (user clicked launcher, or we launched
     * a sub-activity), but NOT when dialogs open on top of us (because
     * dialogs are part of our own window).
     *
     * Available since Android 10 (API 29). Quest 3 runs Android 14.
     */

    @Override
    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
        super.onTopResumedActivityChanged(isTopResumedActivity);

        isTopResumed = isTopResumedActivity;
        if (isTopResumedActivity) {
            expectingFocusReturn = false;
        }
        // Settings is part of the launcher UI - never auto-close on focus changes.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Sub-activity returned - clear the flag so future focus loss closes us.
        expectingFocusReturn = false;

        if (requestCode == REQUEST_CODE_USAGE_ACCESS) {
            if (hasUsageStatsPermission()) {
                Toast.makeText(this, getString(R.string.set_toast_usage_granted), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.set_toast_usage_denied), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CODE_CREATE_BACKUP) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                saveBackupToUri(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_OPEN_BACKUP) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                restoreFromUri(data.getData());
            }
        }
    }

    private void saveBackupToUri(Uri uri) {
        try {
            JSONObject backup = new JSONObject();

            JSONObject prefsData = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    prefsData.put(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    prefsData.put(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    prefsData.put(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    prefsData.put(entry.getKey(), (Float) value);
                } else if (value instanceof Set) {
                    continue;
                } else {
                    prefsData.put(entry.getKey(), String.valueOf(value));
                }
            }
            backup.put("vrprefs", prefsData);

            JSONObject categoriesData = new JSONObject();
            for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Set) {
                    JSONArray setArray = new JSONArray((Set) entry.getValue());
                    categoriesData.put(entry.getKey(), setArray);
                }
            }
            backup.put("categories", categoriesData);

            getContentResolver().openOutputStream(uri).write(backup.toString(2).getBytes());

            Toast.makeText(this, getString(R.string.set_toast_backup_ok), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.set_toast_backup_fail, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreFromUri(Uri uri) {
        try {
            byte[] data = new byte[getContentResolver().openInputStream(uri).available()];
            getContentResolver().openInputStream(uri).read(data);

            String backupStr = new String(data);
            JSONObject backup = new JSONObject(backupStr);

            if (!backup.has("vrprefs") || !backup.has("categories")) {
                Toast.makeText(this, getString(R.string.set_toast_backup_invalid), Toast.LENGTH_LONG).show();
                return;
            }

            SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.clear();

            JSONObject prefsData = backup.getJSONObject("vrprefs");
            JSONArray keys = prefsData.names();

            if (keys == null || keys.length() == 0) {
                Toast.makeText(this, getString(R.string.set_toast_backup_no_prefs), Toast.LENGTH_LONG).show();
                return;
            }

            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                Object value = prefsData.get(key);

                if (key != null && !key.isEmpty()) {
                    if (value instanceof Boolean) {
                        prefsEditor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        prefsEditor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        prefsEditor.putLong(key, (Long) value);
                    } else if (value instanceof Double) {
                        prefsEditor.putFloat(key, ((Double) value).floatValue());
                    } else {
                        prefsEditor.putString(key, String.valueOf(value));
                    }
                }
            }
            boolean prefsApplied = prefsEditor.commit();

            if (!prefsApplied) {
                Toast.makeText(this, getString(R.string.set_toast_prefs_save_fail), Toast.LENGTH_LONG).show();
                return;
            }

            SharedPreferences.Editor catEditor = categoryPrefs.edit();
            catEditor.clear();

            JSONObject categoriesData = backup.getJSONObject("categories");
            JSONArray catKeys = categoriesData.names();
            if (catKeys != null) {
                for (int i = 0; i < catKeys.length(); i++) {
                    String key = catKeys.getString(i);
                    if (key == null || !key.startsWith("cat_")) {
                        continue;
                    }

                    JSONArray setArray = categoriesData.getJSONArray(key);
                    Set<String> set = new HashSet<>();
                    for (int j = 0; j < setArray.length(); j++) {
                        String packageName = setArray.getString(j);
                        if (packageName != null && !packageName.isEmpty()) {
                            set.add(packageName);
                        }
                    }
                    catEditor.putStringSet(key, set);
                }
            }
            boolean catApplied = catEditor.commit();

            if (!catApplied) {
                Toast.makeText(this, getString(R.string.set_toast_cats_save_fail), Toast.LENGTH_LONG).show();
                return;
            }

            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle(R.string.set_restore_done_title)
                    .setMessage(R.string.set_restore_done_message)
                    .setPositiveButton(R.string.set_restart_now, (d, w) -> {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    })
                    .setNegativeButton(R.string.set_later, (d, w) -> {
                        Toast.makeText(this, getString(R.string.set_toast_changes_after_restart), Toast.LENGTH_LONG).show();
                    })
                    .setCancelable(false)
                    .create());

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.set_toast_restore_fail, e.getMessage()), Toast.LENGTH_LONG).show();

            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle(R.string.set_restore_fail_title)
                    .setMessage(R.string.set_restore_fail_message)
                    .setPositiveButton(R.string.set_clear_settings, (d, w) -> {
                        prefs.edit().clear().commit();
                        categoryPrefs.edit().clear().commit();
                        Toast.makeText(this, getString(R.string.set_toast_settings_cleared), Toast.LENGTH_SHORT).show();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    })
                    .setNegativeButton(R.string.set_cancel, null)
                    .create());
        }
    }

    // ===== END BACKUP & RESTORE METHODS =====

    private void showCreateCategoryDialog() {
        Set<String> existingCategories = new HashSet<>();
        Map<String, ?> all = categoryPrefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                existingCategories.add(key.substring(4));
            }
        }

        final EditText input = new EditText(this);

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(R.string.set_create_category)
                .setMessage(R.string.set_enter_category_name)
                .setView(input)
                .setPositiveButton(R.string.set_create, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (existingCategories.contains(name)) {
                            Toast.makeText(this, getString(R.string.set_toast_category_exists, name), Toast.LENGTH_SHORT).show();
                            showCreateCategoryDialog();
                        } else {
                            categoryPrefs.edit().putStringSet("cat_" + name, new HashSet<>()).apply();
                            sendCategoriesChangedBroadcast();
                            Toast.makeText(this, getString(R.string.set_toast_category_created, name), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    private void showCategoryManager() {
        Map<String, ?> allEntries = categoryPrefs.getAll();

        List<String> categoryNames = new ArrayList<>();

        for (String key : allEntries.keySet()) {
            if (key.startsWith("cat_")) {
                String categoryName = key.substring(4);
                categoryNames.add(categoryName);
            }
        }

        Collections.sort(categoryNames);

        if (categoryNames.isEmpty()) {
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle(R.string.set_category_manager)
                    .setMessage(R.string.set_no_categories)
                    .setPositiveButton(R.string.set_create_new, (d, w) -> showCreateCategoryDialog())
                    .setNegativeButton(R.string.set_cancel, null)
                    .create());
            return;
        }

        final String[] categoriesArray = new String[categoryNames.size()];
        for (int i = 0; i < categoryNames.size(); i++) {
            categoriesArray[i] = categoryNames.get(i);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_category_manager_count, categoryNames.size()))
                .setItems(categoriesArray, (d, w) -> {
                    String selectedCategory = categoriesArray[w];
                    showCategoryOptions(selectedCategory);
                })
                .setPositiveButton(R.string.set_create_new, (d, w) -> showCreateCategoryDialog());

        if (!categoryNames.isEmpty()) {
            builder.setNeutralButton(R.string.set_delete_all, (d, w) -> {
                showDeleteAllConfirmation();
            });
        }

        builder.setNegativeButton(R.string.set_cancel, null);

        AlertDialog dialog = builder.create();
        ThemedDialog.showThemed(dialog);
    }

    private void showCategoryOptions(String categoryName) {
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        int appCount = appsInCategory != null ? appsInCategory.size() : 0;

        String[] options = {
                getString(R.string.set_opt_view_apps, appCount),
                getString(R.string.set_opt_rename_category),
                getString(R.string.set_opt_delete_category)
        };

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_category_title, categoryName))
                .setItems(options, (d, w) -> {
                    switch (w) {
                        case 0:
                            showAppsInCategory(categoryName, appsInCategory);
                            break;
                        case 1:
                            showRenameCategoryDialog(categoryName);
                            break;
                        case 2:
                            showDeleteCategoryConfirmation(categoryName);
                            break;
                    }
                })
                .setNegativeButton(R.string.set_back, (d, w) -> showCategoryManager())
                .create());
    }

    private void showAppsInCategory(String categoryName, Set<String> appPackages) {
        if (appPackages == null || appPackages.isEmpty()) {
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.set_apps_in_title, categoryName))
                    .setMessage(R.string.set_no_apps_in_category)
                    .setPositiveButton(R.string.set_ok, null)
                    .create());
            return;
        }

        List<String> appNames = new ArrayList<>();
        PackageManager pm = getPackageManager();

        for (String packageName : appPackages) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                appNames.add(appName + "\n  (" + packageName + ")");
            } catch (PackageManager.NameNotFoundException e) {
                appNames.add(getString(R.string.set_unknown_app) + "\n  (" + packageName + ")");
            }
        }

        Collections.sort(appNames);

        String[] appNamesArray = appNames.toArray(new String[0]);

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_apps_in_title_count, categoryName, appPackages.size()))
                .setItems(appNamesArray, null)
                .setPositiveButton(R.string.set_ok, null)
                .create());
    }

    private void showRenameCategoryDialog(String oldName) {
        final EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelection(input.getText().length());

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(R.string.set_rename_category_title)
                .setMessage(getString(R.string.set_rename_prompt, oldName))
                .setView(input)
                .setPositiveButton(R.string.set_rename, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        renameCategory(oldName, newName);
                    }
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    private void renameCategory(String oldName, String newName) {
        Set<String> existingCategories = new HashSet<>();
        Map<String, ?> all = categoryPrefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                existingCategories.add(key.substring(4));
            }
        }

        if (existingCategories.contains(newName)) {
            Toast.makeText(this, getString(R.string.set_toast_category_exists, newName), Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> apps = categoryPrefs.getStringSet("cat_" + oldName, new HashSet<>());

        categoryPrefs.edit()
                .putStringSet("cat_" + newName, apps != null ? new HashSet<>(apps) : new HashSet<>())
                .remove("cat_" + oldName)
                .apply();

        // Update per-app category references in main prefs
        if (apps != null && !apps.isEmpty()) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String packageName : apps) {
                editor.putString("cat_" + packageName, newName);
            }
            editor.apply();
        }

        sendCategoriesChangedBroadcast();

        Toast.makeText(this, getString(R.string.set_toast_category_renamed, newName), Toast.LENGTH_SHORT).show();
        showCategoryManager();
    }

    private void showDeleteCategoryConfirmation(String categoryName) {
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        int appCount = appsInCategory != null ? appsInCategory.size() : 0;

        String message = getString(R.string.set_delete_category_confirm, categoryName);
        if (appCount > 0) {
            message += "\n\n" + getString(R.string.set_delete_category_apps_note, appCount);
        }

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(R.string.set_delete_category_title)
                .setMessage(message)
                .setPositiveButton(R.string.set_delete, (d, w) -> {
                    deleteCategory(categoryName);
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    private void deleteCategory(String categoryName) {
        // CRITICAL: Also clear per-app category references in main prefs.
        // Without this, restarting the launcher would re-apply the deleted
        // category to the same apps (badge reappears on cards).
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        if (appsInCategory != null && !appsInCategory.isEmpty()) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String packageName : appsInCategory) {
                editor.remove("cat_" + packageName);
            }
            editor.apply();
        }

        categoryPrefs.edit()
                .remove("cat_" + categoryName)
                .apply();

        sendCategoriesChangedBroadcast();

        Toast.makeText(this, getString(R.string.set_toast_category_deleted, categoryName), Toast.LENGTH_SHORT).show();
        showCategoryManager();
    }

    private void showDeleteAllConfirmation() {
        Map<String, ?> all = categoryPrefs.getAll();
        int categoryCount = 0;
        int totalApps = 0;

        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                categoryCount++;
                Set<String> apps = categoryPrefs.getStringSet(key, new HashSet<>());
                if (apps != null) {
                    totalApps += apps.size();
                }
            }
        }

        String message = getString(R.string.set_delete_all_confirm, categoryCount);
        if (totalApps > 0) {
            message += "\n\n" + getString(R.string.set_delete_all_apps_note, totalApps);
        }

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(R.string.set_delete_all_title)
                .setMessage(message)
                .setPositiveButton(R.string.set_delete_all, (d, w) -> {
                    // Clear per-app category references from main prefs first
                    Set<String> allCategorizedPackages = new HashSet<>();
                    for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                        if (entry.getKey().startsWith("cat_")) {
                            Set<String> apps = categoryPrefs.getStringSet(entry.getKey(), null);
                            if (apps != null) allCategorizedPackages.addAll(apps);
                        }
                    }
                    SharedPreferences.Editor editor = prefs.edit();
                    for (String packageName : allCategorizedPackages) {
                        editor.remove("cat_" + packageName);
                    }
                    editor.apply();

                    categoryPrefs.edit().clear().apply();
                    sendCategoriesChangedBroadcast();
                    Toast.makeText(this, getString(R.string.set_toast_all_categories_deleted), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    private void updateAutoRestartStatus(TextView statusText, boolean isEnabled) {
        if (statusText == null) return;

        if (isEnabled) {
            statusText.setText(R.string.auto_restart_enabled);
            statusText.setTextColor(0xFF4CAF50);
        } else {
            statusText.setText(R.string.auto_restart_disabled);
            statusText.setTextColor(0xFF9E9E9E);
        }
    }

    // ===== VERSION & UPDATES =====

    private void setupVersionAndUpdates() {
        AppCompatButton btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        TextView txtUpdateStatus = findViewById(R.id.txtUpdateStatus);
        SwitchCompat switchAutoUpdate = findViewById(R.id.switchAutoUpdate);

        if (switchAutoUpdate != null) {
            switchAutoUpdate.setChecked(prefs.getBoolean("auto_update_enabled", true));
            switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("auto_update_enabled", isChecked).apply();
                Toast.makeText(this, isChecked ? R.string.set_toast_auto_update_on : R.string.set_toast_auto_update_off,
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (btnCheckUpdates != null) {
            btnCheckUpdates.setOnClickListener(v -> {
                showUpdateStatus(txtUpdateStatus, getString(R.string.set_toast_checking_updates), 0xFF888888);
                final UpdateManager updateManager = new UpdateManager(this);

                updateManager.checkForUpdates(new UpdateManager.UpdateCallback() {
                    @Override
                    public void onUpdateAvailable(String version, String downloadUrl, String releaseNotes) {
                        if (isFinishing() || isDestroyed()) return;
                        showUpdateStatus(txtUpdateStatus,
                                getString(R.string.set_update_available_version, version), 0xFF6B8EFF);
                        android.widget.LinearLayout container = new android.widget.LinearLayout(SettingsActivity.this);
                        container.setOrientation(android.widget.LinearLayout.VERTICAL);
                        int padding = (int) (16 * getResources().getDisplayMetrics().density);
                        container.setPadding(padding, padding, padding, padding);

                        android.widget.TextView versionInfo = new android.widget.TextView(SettingsActivity.this);
                        versionInfo.setText(SettingsActivity.this.getString(R.string.set_update_available_version, version));
                        versionInfo.setTextSize(13);
                        versionInfo.setTypeface(null, android.graphics.Typeface.BOLD);
                        versionInfo.setPadding(0, 0, 0, padding);
                        container.addView(versionInfo);

                        android.widget.TextView notesView = new android.widget.TextView(SettingsActivity.this);
                        notesView.setTextSize(12);
                        notesView.setLineSpacing(0, 1.2f);
                        notesView.setTextIsSelectable(true);
                        String html = UpdateManager.markdownToHtml(releaseNotes);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            notesView.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT));
                        } else {
                            notesView.setText(android.text.Html.fromHtml(html));
                        }
                        notesView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

                        android.widget.ScrollView scroll = new android.widget.ScrollView(SettingsActivity.this);
                        scroll.addView(notesView);
                        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
                        scroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, maxHeight));
                        container.addView(scroll);

                        ThemedDialog.showThemed(new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle(R.string.set_update_available_title)
                                .setView(container)
                                .setPositiveButton(R.string.set_update_download, (d, w) -> updateManager.downloadAndInstall(downloadUrl))
                                .setNegativeButton(R.string.set_later, null)
                                .create());
                    }

                    @Override
                    public void onNoUpdateAvailable(String currentVersion) {
                        if (isFinishing() || isDestroyed()) return;
                        // Mesma versao (ou mais nova) que a do GitHub: so avisa no card, sem baixar nada
                        showUpdateStatus(txtUpdateStatus,
                                getString(R.string.set_uptodate_message, currentVersion), 0xFF66BB6A);
                    }

                    @Override
                    public void onError(String error) {
                        if (isFinishing() || isDestroyed()) return;
                        showUpdateStatus(txtUpdateStatus, error, 0xFFEF5350);
                    }
                });
            });
        }
    }

    /** Mostra o resultado da checagem de atualizacao no proprio card de Atualizacoes. */
    private void showUpdateStatus(TextView statusView, String message, int color) {
        if (statusView == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }
        statusView.setText(message);
        statusView.setTextColor(color);
        statusView.setVisibility(View.VISIBLE);
    }

    private void launchNativeSettings() {
        goToSettings();
    }

    // ===== SHIZUKU INTEGRATION =====

    private void initializeShizuku() {
        shizukuManager = new ShizukuManager(this);

        shizukuManager.initialize(new ShizukuManager.ShizukuStatusListener() {
            @Override
            public void onStatusChanged(boolean available, boolean hasPermission) {
                Log.i("SettingsActivity", String.format("Shizuku status - Available: %b, Permission: %b",
                        available, hasPermission));
                runOnUiThread(() -> updateShizukuButton());
            }

            @Override
            public void onCommandResult(boolean success, String output) {
                runOnUiThread(() -> {
                    if (pendingFocusTriggerEnable) {
                        onFocusTriggerCommandResult();
                    } else if (success) {
                        Toast.makeText(SettingsActivity.this, "" + output, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SettingsActivity.this, "" + output, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showShizukuSetupDialog() {
        ShizukuInstaller installer = new ShizukuInstaller(this);
        ShizukuInstaller.InstallStatus status = installer.getStatus();

        switch (status) {
            case NOT_INSTALLED:
                showInstallShizukuDialog(installer);
                break;
            case INSTALLED_NOT_RUNNING:
                showStartShizukuDialog(installer);
                break;
            case RUNNING:
                showGrantPermissionDialog();
                break;
            default:
                showManualSetupDialog();
                break;
        }
    }

    /** Botao "Instalar Shizuku" das Configuracoes: instala, abre ou pede permissao, conforme o caso. */
    private void onShizukuButtonClicked() {
        if (shizukuManager != null && shizukuManager.isReady()) {
            Toast.makeText(this, R.string.set_toast_shizuku_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        showShizukuSetupDialog();
        if (shizukuManager != null) {
            shizukuManager.recheckStatus();
        }
    }

    private void updateShizukuButton() {
        Button btnShizuku = findViewById(R.id.btnShizuku);
        if (btnShizuku == null) return;
        ShizukuInstaller installer = new ShizukuInstaller(this);
        switch (installer.getStatus()) {
            case NOT_INSTALLED:
                btnShizuku.setText(R.string.set_shizuku_btn_install);
                break;
            case INSTALLED_NOT_RUNNING:
                btnShizuku.setText(R.string.set_open_shizuku);
                break;
            default:
                btnShizuku.setText(shizukuManager != null && shizukuManager.isReady()
                        ? R.string.set_shizuku_btn_ready : R.string.set_grant_permission);
                break;
        }
    }

    private void showInstallShizukuDialog(ShizukuInstaller installer) {
        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_shizuku_install_title)
                .setMessage(R.string.set_shizuku_install_message)
                .setPositiveButton(R.string.set_install, (dialog, which) -> installer.installShizuku(SettingsActivity.this))
                .setNegativeButton(R.string.set_not_now, null)
                .create());
    }

    private void showStartShizukuDialog(ShizukuInstaller installer) {
        String instructions = shizukuManager != null ?
                shizukuManager.getSetupInstructions() : getString(R.string.set_shizuku_default_instructions);

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_shizuku_start_title)
                .setMessage(getString(R.string.set_shizuku_start_message, instructions))
                .setPositiveButton(R.string.set_open_shizuku, (dialog, which) -> {
                    if (!installer.launchShizukuApp()) {
                        Toast.makeText(SettingsActivity.this, SettingsActivity.this.getString(R.string.set_toast_shizuku_open_fail), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.set_close, null)
                .create());
    }

    private void showGrantPermissionDialog() {
        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_grant_permission)
                .setMessage(R.string.set_shizuku_grant_message)
                .setPositiveButton(R.string.set_grant, (dialog, which) -> {
                    if (shizukuManager != null) {
                        shizukuManager.requestPermission();
                    }
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    private void showManualSetupDialog() {
        String status = shizukuManager != null ? shizukuManager.getStatusMessage() : getString(R.string.set_shizuku_not_initialized);
        String instructions = shizukuManager != null ? shizukuManager.getSetupInstructions() : "";

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_shizuku_setup_title)
                .setMessage(status + "\n\n" + instructions)
                .setPositiveButton(R.string.set_grant_permission, (dialog, which) -> {
                    if (shizukuManager != null) {
                        shizukuManager.requestPermission();
                    }
                })
                .setNegativeButton(R.string.set_close, null)
                .create());
    }

    // ===== QUEST UTILITIES =====

    private void restartQuestUI() {
        if (shizukuManager == null || !shizukuManager.isReady()) {
            showShizukuSetupDialog();
            if (shizukuManager != null) {
                shizukuManager.recheckStatus();
            }
            return;
        }

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_stop_vrshell_title)
                .setMessage(R.string.set_stop_vrshell_message)
                .setPositiveButton(R.string.set_stop, (dialog, which) -> {
                    Toast.makeText(this, getString(R.string.set_toast_stopping_vrshell), Toast.LENGTH_SHORT).show();
                    shizukuManager.forceStopPackage("com.oculus.vrshell");
                })
                .setNegativeButton(R.string.set_cancel, null)
                .create());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shizukuManager != null) {
            shizukuManager.cleanup();
        }
    }

    /** Mostra o IP do aparelho (antes ficava na tela principal). */
    private void updateSettingsIP() {
        TextView txt = findViewById(R.id.txtSettingsIP);
        if (txt == null) return;
        String ip = null;
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                int v = wm.getConnectionInfo().getIpAddress();
                if (v != 0) {
                    ip = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                            (v & 0xff), (v >> 8 & 0xff), (v >> 16 & 0xff), (v >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) { }
        if (ip != null) {
            txt.setText("\uD83C\uDF10 " + ip);
            txt.setTextColor(android.graphics.Color.parseColor("#32CD32"));
        } else {
            txt.setText(R.string.set_no_internet);
            txt.setTextColor(android.graphics.Color.parseColor("#808080"));
        }
    }

    /** Origem das imagens: colecao do Evolve ou loja da Meta. */
    private void setupImageSource() {
        Button btnEvolve = findViewById(R.id.btnImageSourceEvolve);
        Button btnMeta = findViewById(R.id.btnImageSourceMeta);
        if (btnEvolve == null || btnMeta == null) return;
        btnEvolve.setOnClickListener(v -> chooseImageSource(StoreImageManager.SOURCE_EVOLVE));
        btnMeta.setOnClickListener(v -> chooseImageSource(StoreImageManager.SOURCE_META));
        updateImageSourceButtons();
    }

    private void chooseImageSource(String source) {
        if (source.equals(StoreImageManager.getSource(this))) return;
        StoreImageManager.setSource(this, source);
        com.bumptech.glide.Glide.get(this).clearMemory();
        updateImageSourceButtons();
    }

    private void updateImageSourceButtons() {
        boolean meta = StoreImageManager.isMeta(this);
        Button btnEvolve = findViewById(R.id.btnImageSourceEvolve);
        Button btnMeta = findViewById(R.id.btnImageSourceMeta);
        TextView desc = findViewById(R.id.txtImageSourceDesc);
        btnEvolve.setBackgroundResource(meta ? R.drawable.orbita_bg_pill : R.drawable.orbita_bg_pill_accent);
        btnMeta.setBackgroundResource(meta ? R.drawable.orbita_bg_pill_accent : R.drawable.orbita_bg_pill);
        if (desc != null) {
            desc.setText(meta ? R.string.set_image_source_meta_desc : R.string.set_image_source_evolve_desc);
        }
    }
}
