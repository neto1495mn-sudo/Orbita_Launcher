package com.neto.orbitalauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.bluetooth.BluetoothAdapter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.neto.orbitalauncher.PlaytimeTracker;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.neto.orbitalauncher.theme.ThemeApplier;
import com.neto.orbitalauncher.theme.ThemeManager;
import com.neto.orbitalauncher.theme.ThemedDialog;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView appsGrid;
    private AppAdapter appAdapter;
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> filteredList = new ArrayList<>();
    private PackageManager packageManager;
    private EditText searchEditText;

    
    // Floating bulk-action bar - appears at bottom when apps are selected.
    // Hosts both bulk actions (Move, Uninstall) and single-app actions
    // (Rename, Playtime) that show only when exactly one app is selected.
    private android.widget.LinearLayout bulkActionBar;
    private android.widget.TextView bulkActionCountLabel;
    private android.widget.Button bulkActionRenameBtn;
    private android.widget.Button bulkActionPlaytimeBtn;
    private android.widget.Button bulkActionAppSettingsBtn;
    private android.widget.Button bulkActionRemoveCategoryBtn;
    // Queue of packages to uninstall sequentially (Android shows the
    // uninstall confirmation one at a time, so we launch them one by one).
    private final java.util.Queue<String> pendingUninstalls = new java.util.LinkedList<>();
    private static final int REQUEST_CODE_UNINSTALL = 9123;

    private ExecutorService executorService = Executors.newFixedThreadPool(4);
    private Map<String, Drawable> iconCache = new HashMap<>();
    private static final String GITHUB_ICON_BASE_URL = "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/";

    // RESTORED: Icon scale values from old launcher (82, 99, 125, 165, 236 dp)
    private static final int[] ICON_SCALES_DP = {90, 110, 140, 180, 236};
    private static final int DEFAULT_SCALE_INDEX = 3;  // 180dp default

    // Card overhead: margin (12dp each side = 24dp) + padding (8dp each side = 16dp) = 40dp total
    private static final int CARD_HORIZONTAL_OVERHEAD_DP = 22;

    // Broadcast action for theme changes
    private static final String ACTION_CATEGORIES_CHANGED = "com.neto.orbitalauncher.CATEGORIES_CHANGED";

    // Friendly display names for Meta system apps
    private static final java.util.Map<String, String> META_APP_NAMES;
    static {
        META_APP_NAMES = new java.util.HashMap<>();
        META_APP_NAMES.put("com.oculus.browser", "Meta Browser");
        META_APP_NAMES.put("com.oculus.store", "Meta Store");
        META_APP_NAMES.put("com.oculus.systemux", "System UX");
        META_APP_NAMES.put("com.oculus.socialplatform", "Social Platform");
        META_APP_NAMES.put("com.oculus.companion.app", "Meta Companion");
        META_APP_NAMES.put("com.oculus.avatar2", "Avatars");
        META_APP_NAMES.put("com.oculus.casting", "Casting");
        META_APP_NAMES.put("com.oculus.explore", "Explore");
        META_APP_NAMES.put("com.oculus.guardian", "Guardian");
        META_APP_NAMES.put("com.oculus.home", "Meta Home");
        META_APP_NAMES.put("com.oculus.mobile.preferencemanager", "Preferences");
        META_APP_NAMES.put("com.oculus.mrservice", "Mixed Reality");
        META_APP_NAMES.put("com.oculus.os.dialogs", "System Dialogs");
        META_APP_NAMES.put("com.oculus.passthrough", "Passthrough");
        META_APP_NAMES.put("com.oculus.peoplehub", "People Hub");
        META_APP_NAMES.put("com.oculus.photos", "Meta Photos");
        META_APP_NAMES.put("com.oculus.tv", "Meta TV");
        META_APP_NAMES.put("com.oculus.video", "Meta Video");
        META_APP_NAMES.put("com.oculus.voiceinput", "Voice Input");
        META_APP_NAMES.put("com.oculus.workrooms", "Horizon Workrooms");
        META_APP_NAMES.put("com.oculus.spatialapp", "Spatial App");
        META_APP_NAMES.put("com.oculus.focus", "Focus Mode");
        META_APP_NAMES.put("com.oculus.updateservice", "Update Service");
        META_APP_NAMES.put("com.oculus.accountscenter", "Accounts Center");
        META_APP_NAMES.put("com.oculus.helpcenter", "Help Center");
        META_APP_NAMES.put("com.oculus.notification", "Notifications");
        META_APP_NAMES.put("com.oculus.environmentservice", "Environment");
        META_APP_NAMES.put("com.oculus.systemdriver", "System Driver");
        META_APP_NAMES.put("com.oculus.horizon", "Meta Horizon");
        META_APP_NAMES.put("com.facebook.arvr.quill", "Quill");
        META_APP_NAMES.put("com.facebook.spatial.player", "Spatial Media Player");
        META_APP_NAMES.put("com.meta.spatial.editor", "Spatial Editor");
    }

    // Meta-only packages: shown in Meta Apps view, hidden from main grid
    private static final String[] SYSTEM_PACKAGES = {
            "com.oculus",
            "com.facebook",
            "com.meta.",
            "com.android.healthconnect.controller",
            "com.android.documentsui"
    };

    private boolean isEditMode = false;
    private SharedPreferences prefs;
    private Set<String> selectedApps = new HashSet<>();

    private static final boolean ENABLE_IMAGE_CACHING = true;
    public static MainActivity instance;


    private static final String PREFS_NAME = "VRLPrefs";
    private static final String KEY_PERMISSION_GRANTED = "usage_stats_permission_granted";
    private static final String KEY_PERMISSION_CHECKED = "usage_stats_permission_checked";
    private static final String KEY_LAST_CATEGORY = "last_category";

    private static final String CATEGORY_PREFS = "vr_categories";
    private SharedPreferences categoryPrefs;
    private Map<String, Set<String>> categories = new HashMap<>();
    private LinearLayout categoryBar;
    private String currentCategory = "All Apps";

    // Quick Settings Panel
    private BroadcastReceiver appChangeListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // Custom broadcast
            if ("APP_CHANGE_DETECTED".equals(action)) {
                refreshAll();
                String packageName = intent.getStringExtra("package_name");
                String appAction = intent.getStringExtra("action");
                boolean replacing = intent.getBooleanExtra("replacing", false);

                if (Intent.ACTION_PACKAGE_ADDED.equals(appAction) && !replacing) {
                    Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.main_toast_new_app_refreshed), Toast.LENGTH_SHORT).show();
                }
            }
            // System package events
            else if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                    Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
                    Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                refreshAll();

                if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    String packageName = intent.getData().getSchemeSpecificPart();
                    Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.main_toast_new_app_installed, packageName), Toast.LENGTH_SHORT).show();
                }
            }
        }
    };


    /**
     * Receives broadcasts when categories are modified in Settings
     * (create/rename/delete). Rebuilds the category bar and refreshes
     * the app grid so removed categories vanish immediately - no manual
     * refresh required.
     */
    private BroadcastReceiver categoryChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CATEGORIES_CHANGED.equals(intent.getAction())) {
                runOnUiThread(() -> {
                    // 1. Reload the in-memory categories map from SharedPreferences
                    loadCategories();

                    // 2. CRITICAL: Recompute each app's .category field. The badge on
                    //    each card is rendered from app.category which is cached on
                    //    the AppInfo object - so we must update it manually here,
                    //    otherwise deleted categories' badges persist.
                    if (appList != null) {
                        for (AppInfo app : appList) {
                            String newCategory = "Uncategorized";
                            for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                                if (entry.getValue().contains(app.packageName)) {
                                    newCategory = entry.getKey();
                                    break;
                                }
                            }
                            app.category = newCategory;
                        }
                    }

                    // 3. Rebuild the category bar at the bottom
                    buildCategoryBar();

                    // 4. If user was viewing a now-deleted category, switch to All Apps
                    if (currentCategory != null && !"All Apps".equals(currentCategory)
                            && !categories.containsKey(currentCategory)) {
                        currentCategory = "All Apps";
                        prefs.edit().putString("selected_category", "All Apps").apply();
                        String query = searchEditText != null ? searchEditText.getText().toString() : "";
                        filterApps(query);
                    }
                    updateCategoryButtonStates(currentCategory);

                    // 5. Refresh visible cards so badges update
                    if (appAdapter != null) {
                        appAdapter.notifyDataSetChanged();
                    }

                    Log.d("MainActivity", "Categories refreshed - " + categories.size() + " categories");
                });
            }
        }
    };

    private Handler statusHandler = new Handler();
    private TextView txtTime;
    private TextView txtIP;
    private ImageView batteryIcon;
    private TextView txtBattery;
    private ImageView wifiIcon;
    private TextView txtWifiSignal;
    private WifiManager wifiManager;
    private LinearLayout wifiContainer;
    private Runnable statusUpdateRunnable;

    private PlaytimeTracker playtimeTracker;
    private Handler playtimeHandler = new Handler();
    private Runnable playtimeUpdateRunnable;
    private PlaytimeTracker.TimeRange currentPlaytimeRange = PlaytimeTracker.TimeRange.TODAY;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryLevel(intent);
        }
    };

    // ===== SAFE BOOLEAN PREFERENCE HELPER =====
    private boolean getBooleanPreference(String key, boolean defaultValue) {
        try {
            // Try to get as boolean first
            return prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            // If it's stored as string, convert it
            try {
                String value = prefs.getString(key, String.valueOf(defaultValue));
                return Boolean.parseBoolean(value);
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    // ===== END HELPER =====

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window background transparent for true see-through effect
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);

        setContentView(R.layout.activity_main);

        instance = this;

        appsGrid = findViewById(R.id.appsGrid);
        // Hide grid until entry animation kicks off, so user doesn't see
        // a static frame of the final layout before cards fly in.
        appsGrid.setAlpha(0f);

        // Find the search container first
        LinearLayout searchContainer = findViewById(R.id.searchContainer);

        // Then find the search EditText within the container
        searchEditText = searchContainer.findViewById(R.id.searchEditText);

        categoryBar = findViewById(R.id.categoryBar);

        txtTime = findViewById(R.id.txtTime);
        txtIP = findViewById(R.id.txtIP);

        // Find battery and WiFi views
        batteryIcon = findViewById(R.id.batteryIcon);
        txtBattery = findViewById(R.id.txtBattery);
        wifiIcon = findViewById(R.id.wifiIcon);
        txtWifiSignal = findViewById(R.id.txtWifiSignal);
        wifiContainer = findViewById(R.id.wifiContainer);


        appsGrid.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        appsGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);

        packageManager = getPackageManager();

        prefs = getSharedPreferences("VRLPrefs", MODE_PRIVATE);
        categoryPrefs = getSharedPreferences(CATEGORY_PREFS, MODE_PRIVATE);

        // FIXED: Use safe boolean helper
        isEditMode = getBooleanPreference("edit_mode", false);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Initialize playtime tracker
        playtimeTracker = new PlaytimeTracker(this);


        // Check for usage stats permission - but use cached result
        boolean permissionGranted = prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
        boolean permissionChecked = prefs.getBoolean(KEY_PERMISSION_CHECKED, false);

        if (!permissionChecked) {
            // First time running, check actual permission
            if (playtimeTracker.hasPermission()) {
                prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
            }
            prefs.edit().putBoolean(KEY_PERMISSION_CHECKED, true).apply();
            permissionGranted = prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
        }

        if (!permissionGranted) {

        }

        if (permissionGranted) {
            startPlaytimeUpdates();
        }

        loadCategories();

        // Initialize default categories if none exist
        if (categories.isEmpty()) {
            SharedPreferences.Editor editor = categoryPrefs.edit();
            editor.putStringSet("cat_Games", new HashSet<>());
            editor.putStringSet("cat_Media", new HashSet<>());
            editor.putStringSet("cat_Tools", new HashSet<>());
            editor.putStringSet("cat_Social", new HashSet<>());
            editor.apply();
            loadCategories(); // Reload after adding defaults
        }

        // Load last selected category from preferences
        // Default to "All Apps" if no saved category exists
        currentCategory = prefs.getString("last_category", "All Apps");

        // Validate that the saved category still exists
        // If it was deleted, fall back to "All Apps"
        if (!currentCategory.equals("All Apps") && !currentCategory.equals("Meta Apps")
                && !categories.containsKey(currentCategory)) {
            currentCategory = "All Apps";
            // Save the corrected category
            prefs.edit().putString("last_category", currentCategory).apply();
        }

        loadUserApps();

        // Filter apps based on the restored category BEFORE creating adapter
        filteredList.clear();
        if (currentCategory.equals("All Apps")) {
            // "Todos": todos os apps, menos os da Meta que nao estao em pasta
            for (AppInfo app : appList) {
                if (showInAllApps(app)) filteredList.add(app);
            }
        } else {
            // Show apps from the saved category
            Set<String> pkgs = categories.get(currentCategory);
            if (pkgs != null) {
                for (AppInfo app : appList) {
                    if (pkgs.contains(app.packageName)) {
                        filteredList.add(app);
                    }
                }
            }
        }
        sortForCurrentMode(filteredList, currentCategory);

        // Create adapter with filtered list
        appAdapter = new AppAdapter(filteredList);
        appsGrid.setAdapter(appAdapter);

        // FIXED: Call setupRecyclerView() so the layout manager and
        // dynamic resize listener are properly initialized
        setupRecyclerView();

        // VR POLISH: schedule the directional entry animation to play once
        // the grid has finished laying out its visible cards.
        scheduleEntryAnimation();

        // Build category bar and highlight the saved category
        buildCategoryBar();
        updateCategoryButtonStates(currentCategory);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package"); // critical for package-related broadcasts

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appChangeListener, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(appChangeListener, filter);
        }

        // Register category change receiver for real-time updates from Settings
        IntentFilter categoryFilter = new IntentFilter(ACTION_CATEGORIES_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(categoryChangeReceiver, categoryFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(categoryChangeReceiver, categoryFilter);
        }

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, batteryFilter);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterApps(s.toString());
            }
        });

        ImageView clearSearch = findViewById(R.id.clearSearch);
        clearSearch.setOnClickListener(v -> {
            searchEditText.setText("");
            filterApps("");
        });

        ImageView btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);

            // Get Main window dimensions
            android.graphics.Rect mainWindowRect = new android.graphics.Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(mainWindowRect);

            // Alternative: get actual window dimensions
            int mainWidth = getWindow().getDecorView().getWidth();
            int mainHeight = getWindow().getDecorView().getHeight();

            if (mainWidth > 0 && mainHeight > 0) {
                intent.putExtra("window_width", mainWidth);
                intent.putExtra("window_height", mainHeight);
            } else {
                // Fallback to screen dimensions
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                intent.putExtra("window_width", metrics.widthPixels);
                intent.putExtra("window_height", metrics.heightPixels);
            }

            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // META APPS BUTTON - bottom-left corner
        // FLOATING BULK ACTION BAR - bottom-center, visible when apps selected
        addBulkActionBar();

        // Barra lateral (substitui os botoes flutuantes e a aba da borda)
        setupSideNav();

        appsGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == RecyclerView.SCROLL_STATE_DRAGGING ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    Glide.with(MainActivity.this).pauseRequests();
                } else {
                    Glide.with(MainActivity.this).resumeRequests();
                }
            }
        });

        startStatusUpdates();

        // Apply current theme to entire view hierarchy
        View rootView = findViewById(android.R.id.content);
        ThemeApplier.applyThemeToHierarchy(rootView);
    }

    private final Runnable autoUpdateCheck = () -> {
        try {
            if (isFinishing() || isDestroyed()) return;
            UpdateManager um = new UpdateManager(MainActivity.this);
            if (um.shouldCheckForUpdates()) um.checkForUpdates(false);
        } catch (Exception e) {
            Log.e("MainActivity", "auto update check failed", e);
        }
    };

    private void scheduleAutoUpdateCheck() {
        if (appsGrid == null) return;
        appsGrid.removeCallbacks(autoUpdateCheck);
        appsGrid.postDelayed(autoUpdateCheck, 8000);
    }

    /**
     * Create the floating bulk-action column that appears on the right side
     * of the screen when one or more apps are selected in edit mode. Provides
     * quick access to Move-to-Category, Uninstall, and Clear-Selection.
     * Visibility is managed by updateBulkActionBarVisibility().
     */
    private void addBulkActionBar() {
        try {
            float d = getResources().getDisplayMetrics().density;

            // Container - vertical column on the right side
            bulkActionBar = new android.widget.LinearLayout(this);
            bulkActionBar.setOrientation(android.widget.LinearLayout.VERTICAL);
            bulkActionBar.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            bulkActionBar.setPadding((int)(10 * d), (int)(12 * d), (int)(10 * d), (int)(12 * d));
            bulkActionBar.setElevation(12 * d);
            bulkActionBar.setVisibility(View.GONE);

            // Themed pill-shaped background
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(20 * d);
            com.neto.orbitalauncher.theme.Theme theme =
                    com.neto.orbitalauncher.theme.ThemeManager.getInstance(this).getCurrentTheme();
            bg.setColor(theme.bgSecondary);
            bg.setStroke((int)(1 * d), theme.borderPrimary);
            bulkActionBar.setBackground(bg);

            // Count label
            bulkActionCountLabel = new android.widget.TextView(this);
            bulkActionCountLabel.setText(getString(R.string.main_selected_count, 0));
            bulkActionCountLabel.setTextColor(theme.textPrimary);
            bulkActionCountLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            bulkActionCountLabel.setGravity(android.view.Gravity.CENTER);
            bulkActionCountLabel.setMaxLines(2);
            bulkActionCountLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
            bulkActionCountLabel.setPadding(0, (int)(2 * d), 0, (int)(8 * d));
            bulkActionBar.addView(bulkActionCountLabel);

            // Move-to-Category button
            android.widget.Button moveBtn = makeBulkBarButton(getString(R.string.main_bulk_move), R.drawable.ic_bulk_move, theme);
            moveBtn.setOnClickListener(v -> showMultiAssignCategoryDialog());
            bulkActionBar.addView(moveBtn, makeBulkBarButtonParams(d));

            // Remove from current category button (only shows when viewing
            // a specific category like "Games" rather than "All Apps")
            bulkActionRemoveCategoryBtn = makeBulkBarButton(getString(R.string.main_bulk_remove_category), R.drawable.ic_bulk_remove, theme);
            bulkActionRemoveCategoryBtn.setOnClickListener(v -> removeSelectedFromCurrentCategory());
            bulkActionBar.addView(bulkActionRemoveCategoryBtn, makeBulkBarButtonParams(d));

            // Rename button (only shows when exactly 1 app selected)
            bulkActionRenameBtn = makeBulkBarButton(getString(R.string.main_bulk_rename), R.drawable.ic_bulk_edit, theme);
            bulkActionRenameBtn.setOnClickListener(v -> renameSingleSelected());
            bulkActionBar.addView(bulkActionRenameBtn, makeBulkBarButtonParams(d));

            // Playtime button (only shows when exactly 1 app selected)
            bulkActionPlaytimeBtn = makeBulkBarButton(getString(R.string.main_bulk_stats), R.drawable.ic_bulk_stats, theme);
            bulkActionPlaytimeBtn.setOnClickListener(v -> showPlaytimeForSingleSelected());
            bulkActionBar.addView(bulkActionPlaytimeBtn, makeBulkBarButtonParams(d));

            // App settings button (only shows when exactly 1 app selected):
            // opens the native Android settings screen for that app
            bulkActionAppSettingsBtn = makeBulkBarButton(getString(R.string.main_bulk_app_settings), R.drawable.ic_settings, theme);
            bulkActionAppSettingsBtn.setOnClickListener(v -> openAppSettingsForSingleSelected());
            bulkActionBar.addView(bulkActionAppSettingsBtn, makeBulkBarButtonParams(d));

            // Uninstall button
            android.widget.Button uninstallBtn = makeBulkBarButton(getString(R.string.main_bulk_uninstall), R.drawable.ic_delete, theme);
            uninstallBtn.setOnClickListener(v -> confirmAndUninstallSelected());
            bulkActionBar.addView(uninstallBtn, makeBulkBarButtonParams(d));

            // Position at the right edge, vertically centered
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    (int)(210 * d),
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
            params.setMargins(0, 0, (int)(20 * d), 0);

            addContentView(bulkActionBar, params);
            android.util.Log.i("MainActivity", "Bulk action bar added");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to add bulk action bar", e);
        }
    }

    private android.widget.Button makeBulkBarButton(String text, int iconRes,
                                                    com.neto.orbitalauncher.theme.Theme theme) {
        float d = getResources().getDisplayMetrics().density;
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText(text);
        btn.setTextColor(theme.textPrimary);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setAllCaps(false);
        btn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btn.setPadding((int)(14 * d), (int)(9 * d), (int)(14 * d), (int)(9 * d));

        // Android-style line icon on the left, tinted to the text color
        android.graphics.drawable.Drawable icon = getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            int size = (int)(18 * d);
            icon.setBounds(0, 0, size, size);
            icon.setTint(iconRes == R.drawable.ic_delete ? 0xFFFF6B6B : theme.textPrimary);
            btn.setCompoundDrawablesRelative(icon, null, null, null);
            btn.setCompoundDrawablePadding((int)(12 * d));
        }
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);

        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(20 * d);
        btnBg.setColor(theme.bgSecondary);
        btnBg.setStroke((int)(1 * d), theme.borderPrimary);
        btn.setBackground(btnBg);
        return btn;
    }

    private android.widget.LinearLayout.LayoutParams makeBulkBarButtonParams(float density) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int)(3 * density), 0, (int)(3 * density));
        return lp;
    }

    /**
     * Show or hide the bulk action bar based on current selection size.
     * Also shows/hides conditional buttons (single-app actions visible
     * only when 1 app selected; remove-from-category visible only when
     * viewing a specific category).
     */
    private void updateBulkActionBarVisibility() {
        if (bulkActionBar == null) return;
        int count = selectedApps.size();

        // Conditional buttons: single-app actions
        boolean singleSelected = (count == 1);
        if (bulkActionRenameBtn != null) {
            bulkActionRenameBtn.setVisibility(singleSelected ? View.VISIBLE : View.GONE);
        }
        if (bulkActionPlaytimeBtn != null) {
            bulkActionPlaytimeBtn.setVisibility(singleSelected ? View.VISIBLE : View.GONE);
        }
        if (bulkActionAppSettingsBtn != null) {
            bulkActionAppSettingsBtn.setVisibility(singleSelected ? View.VISIBLE : View.GONE);
        }
        // Remove-from-category: only when viewing a specific category
        if (bulkActionRemoveCategoryBtn != null) {
            boolean inCategoryView = currentCategory != null &&
                    !currentCategory.equals("All Apps") &&
                    !currentCategory.isEmpty();
            bulkActionRemoveCategoryBtn.setVisibility(inCategoryView ? View.VISIBLE : View.GONE);
        }

        if (count > 0) {
            // Um app: mostra o nome dele. Varios: mostra quantos.
            AppInfo single = count == 1 ? getSingleSelectedApp() : null;
            if (single != null) {
                bulkActionCountLabel.setText(CustomLabelManager.getInstance(this).getDisplayLabel(
                        single.packageName, single.label != null ? single.label : single.packageName));
            } else {
                bulkActionCountLabel.setText(getString(R.string.main_selected_count, count));
            }
            if (bulkActionBar.getVisibility() != View.VISIBLE) {
                bulkActionBar.setVisibility(View.VISIBLE);
                bulkActionBar.setAlpha(0f);
                bulkActionBar.setTranslationX(40 * getResources().getDisplayMetrics().density);
                bulkActionBar.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(180)
                        .start();
            }
        } else {
            if (bulkActionBar.getVisibility() == View.VISIBLE) {
                bulkActionBar.animate()
                        .alpha(0f)
                        .translationX(40 * getResources().getDisplayMetrics().density)
                        .setDuration(140)
                        .withEndAction(() -> bulkActionBar.setVisibility(View.GONE))
                        .start();
            }
        }
    }

    /**
     * Helper: find the AppInfo for the single currently-selected package.
     */
    private AppInfo getSingleSelectedApp() {
        if (selectedApps.size() != 1) return null;
        String pkg = selectedApps.iterator().next();
        for (AppInfo app : appList) {
            if (app.packageName.equals(pkg)) return app;
        }
        return null;
    }

    private void renameSingleSelected() {
        AppInfo app = getSingleSelectedApp();
        if (app != null) showRenameDialog(app);
    }

    private void showPlaytimeForSingleSelected() {
        AppInfo app = getSingleSelectedApp();
        if (app != null) showPlaytimeDetails(app);
    }

    private void openAppSettingsForSingleSelected() {
        AppInfo app = getSingleSelectedApp();
        if (app == null) return;
        clearSelection();
        openAppSettings(app.packageName);
    }

    /**
     * Abre a tela de configuracoes nativas do Android para um app (permissoes, armazenamento,
     * forcar parada...). No Quest, a Meta pode interceptar o pedido generico, entao tentamos
     * primeiro direto no app de Configuracoes do Android.
     */
    private void openAppSettings(String packageName) {
        android.net.Uri uri = android.net.Uri.parse("package:" + packageName);
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
            intent.setPackage("com.android.settings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "Native app settings launch failed, trying generic intent", e);
        }
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.main_toast_cannot_open_app_settings), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Remove all selected apps from the currently-viewed category.
     * Only meaningful when the user is browsing a specific category.
     */
    private void removeSelectedFromCurrentCategory() {
        if (selectedApps.isEmpty()) return;
        if (currentCategory == null || currentCategory.equals("All Apps") || currentCategory.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_switch_to_category), Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> categorySet = categories.get(currentCategory);
        if (categorySet == null) return;

        int removed = 0;
        Set<String> newSet = new HashSet<>(categorySet);
        for (String pkg : selectedApps) {
            if (newSet.remove(pkg)) {
                removed++;
                // Clear the per-app category preference so the badge
                // doesn't resurrect on the next refresh
                prefs.edit().remove("cat_" + pkg).apply();
            }
        }
        // Persist the updated category set
        categoryPrefs.edit().putStringSet("cat_" + currentCategory, newSet).apply();

        Toast.makeText(this,
                getString(removed == 1 ? R.string.main_toast_removed_from_category_one
                        : R.string.main_toast_removed_from_category_other, removed, displayCategoryName(currentCategory)),
                Toast.LENGTH_SHORT).show();

        selectedApps.clear();
        updateBulkActionBarVisibility();
        loadCategories();
        refreshAll();
    }

    /**
     * Ask the user to confirm bulk uninstall, then queue the selected
     * packages and launch the first uninstall dialog. Android only shows
     * one at a time, so we sequence them via onActivityResult.
     */
    private void confirmAndUninstallSelected() {
        if (selectedApps.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_no_apps_selected), Toast.LENGTH_SHORT).show();
            return;
        }

        final int count = selectedApps.size();

        // Build a preview of which apps will be uninstalled
        StringBuilder preview = new StringBuilder();
        int shown = 0;
        for (String pkg : selectedApps) {
            String label = pkg;
            for (AppInfo app : appList) {
                if (app.packageName.equals(pkg)) {
                    label = app.label;
                    break;
                }
            }
            preview.append("• ").append(label).append("\n");
            shown++;
            if (shown >= 8 && count > 9) {
                preview.append(getString(R.string.main_uninstall_more, count - shown)).append("\n");
                break;
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(getString(count == 1 ? R.string.main_uninstall_title_one : R.string.main_uninstall_title_other, count))
                .setMessage(preview.toString() +
                        "\n" + getString(R.string.main_uninstall_confirm_note))
                .setPositiveButton(getString(R.string.main_btn_uninstall), (d, w) -> {
                    pendingUninstalls.clear();
                    pendingUninstalls.addAll(selectedApps);
                    Set<String> previouslySelected = new HashSet<>(selectedApps);
                    selectedApps.clear();
                    updateBulkActionBarVisibility();
                    if (appAdapter != null) appAdapter.notifyDataSetChanged();
                    Toast.makeText(this,
                            getString(count == 1 ? R.string.main_toast_uninstalling_one : R.string.main_toast_uninstalling_other, count),
                            Toast.LENGTH_SHORT).show();
                    launchNextUninstall();
                })
                .setNegativeButton(getString(R.string.main_btn_cancel), null);
        ThemedDialog.showThemed(b.create());
    }

    /**
     * Launch the system uninstall dialog for the next package in the queue.
     * After the user confirms or cancels, onActivityResult is called and we
     * advance to the next package.
     */
    private void launchNextUninstall() {
        String pkg = pendingUninstalls.poll();
        if (pkg == null) {
            // Queue drained - refresh app list (some apps may have been removed)
            Toast.makeText(this, getString(R.string.main_toast_bulk_uninstall_done), Toast.LENGTH_SHORT).show();
            refreshAll();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(android.net.Uri.parse("package:" + pkg));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(intent, REQUEST_CODE_UNINSTALL);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to launch uninstall for " + pkg, e);
            // Skip and try the next one
            launchNextUninstall();
        }
    }

    private void startStatusUpdates() {
        // Remove any existing callbacks first to avoid duplicates
        if (statusHandler != null && statusUpdateRunnable != null) {
            statusHandler.removeCallbacks(statusUpdateRunnable);
        }

        statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                updateIPAddress();
                updateWifiSignal();
                statusHandler.postDelayed(this, 1000); // Update every 1 seconds
            }
        };
        statusHandler.post(statusUpdateRunnable);
    }

    private void updateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        txtTime.setText(currentTime);
    }

    private void updateIPAddress() {
        String ipAddress = getDeviceIPAddress();
        if (ipAddress != null && !ipAddress.isEmpty()) {
            txtIP.setText("🌐 " + ipAddress);
            txtIP.setTextColor(Color.parseColor("#32CD32"));
        } else {
            txtIP.setText(getString(R.string.main_status_no_internet));
            txtIP.setTextColor(Color.parseColor("#808080"));
        }
    }

    private void updateBatteryLevel(Intent batteryIntent) {
        if (batteryIntent == null) {
            batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }

        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            float batteryPercent = (level / (float) scale) * 100;
            int roundedPercent = Math.round(batteryPercent);

            runOnUiThread(() -> {
                txtBattery.setText(roundedPercent + "%");

                int batteryIconRes = R.drawable.ic_battery_full;
                if (isCharging) {
                    batteryIconRes = R.drawable.ic_battery_charging;
                } else if (roundedPercent <= 20) {
                    batteryIconRes = R.drawable.ic_battery_low;
                } else if (roundedPercent <= 50) {
                    batteryIconRes = R.drawable.ic_battery_half;
                } else if (roundedPercent <= 80) {
                    batteryIconRes = R.drawable.ic_battery_high;
                }
                // If >80%, use ic_battery_full (default)

                batteryIcon.setImageResource(batteryIconRes);

                if (roundedPercent <= 15) {
                    txtBattery.setTextColor(Color.parseColor("#FF6B6B"));
                    batteryIcon.setColorFilter(Color.parseColor("#FF6B6B"));
                } else if (isCharging) {
                    txtBattery.setTextColor(Color.parseColor("#4CAF50"));
                    batteryIcon.setColorFilter(Color.parseColor("#4CAF50"));
                } else {
                    txtBattery.setTextColor(Color.parseColor("#6B8EFF"));
                    batteryIcon.setColorFilter(Color.parseColor("#6B8EFF"));
                }
            });
        }
    }

    private void updateWifiSignal() {
        runOnUiThread(() -> {
            try {
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    int rssi = wifiInfo.getRssi();
                    int level = WifiManager.calculateSignalLevel(rssi, 4);

                    int wifiIconRes;
                    String signalText;

                    // Only set text color, NOT icon color
                    int textColor;

                    switch (level) {
                        case 0:
                            wifiIconRes = R.drawable.ic_wifi_low;
                            signalText = getString(R.string.main_wifi_signal_weak);
                            textColor = Color.parseColor("#FF6B6B"); // Red text only
                            break;
                        case 1:
                            wifiIconRes = R.drawable.ic_wifi_low;
                            signalText = getString(R.string.main_wifi_signal_low);
                            textColor = Color.parseColor("#FF6B6B"); // Red text only
                            break;
                        case 2:
                            wifiIconRes = R.drawable.ic_wifi_medium;
                            signalText = getString(R.string.main_wifi_signal_good);
                            textColor = Color.parseColor("#FFA500"); // Orange text only
                            break;
                        case 3:
                            wifiIconRes = R.drawable.ic_wifi_full;
                            signalText = getString(R.string.main_wifi_signal_excellent);
                            textColor = Color.parseColor("#6B8EFF"); // Blue text only
                            break;
                        default:
                            wifiIconRes = R.drawable.ic_wifi_full;
                            signalText = getString(R.string.main_wifi_signal_full);
                            textColor = Color.parseColor("#6B8EFF"); // Blue text only
                            break;
                    }

                    wifiIcon.setImageResource(wifiIconRes);
                    txtWifiSignal.setText(signalText);
                    txtWifiSignal.setTextColor(textColor);

                    // IMPORTANT: Remove the color filter so the icon shows its true colors
                    wifiIcon.clearColorFilter();

                } else {
                    // WiFi is disabled
                    wifiIcon.setImageResource(R.drawable.ic_wifi_off);
                    txtWifiSignal.setText(getString(R.string.main_wifi_signal_off));
                    txtWifiSignal.setTextColor(Color.parseColor("#808080"));
                    wifiIcon.clearColorFilter(); // No color filter - shows red slash
                }
            } catch (Exception e) {
                wifiIcon.setImageResource(R.drawable.ic_wifi_off);
                txtWifiSignal.setText(getString(R.string.main_wifi_signal_na));
                txtWifiSignal.setTextColor(Color.parseColor("#808080"));
                wifiIcon.clearColorFilter();
            }
        });
    }

    private void showWifiDetails() {
        try {
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int rssi = wifiInfo.getRssi();
                String ssid = wifiInfo.getSSID();
                if (ssid.equals("<unknown ssid>") || ssid.equals("0x")) {
                    ssid = getString(R.string.main_wifi_hidden_network);
                }

                String details = getString(R.string.main_wifi_details_body,
                        ssid.replace("\"", ""), rssi, wifiInfo.getLinkSpeed(), wifiInfo.getFrequency());

                ThemedDialog.showThemed(new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.main_wifi_details_title))
                        .setMessage(details)
                        .setPositiveButton(getString(R.string.main_btn_ok), null)
                        .create());
            } else {
                Toast.makeText(this, getString(R.string.main_toast_wifi_disabled), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.main_toast_wifi_details_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private String getDeviceIPAddress() {
        try {
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                        (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            }

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null && activeNetwork.isConnected()) {
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        try {
                            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                                NetworkInterface intf = en.nextElement();
                                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                                    InetAddress inetAddress = enumIpAddr.nextElement();
                                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && inetAddress.isSiteLocalAddress()) {
                                        return inetAddress.getHostAddress();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Continue to fallback
                        }
                    }
                }
            }

            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            String ip = inetAddress.getHostAddress();
                            boolean isIPv4 = ip.indexOf(':') < 0;
                            if (isIPv4) {
                                return ip;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Return null if all methods fail
            }

        } catch (Exception e) {
            // Return null if all methods fail
        }

        return null;
    }

    public void refreshEditMode() {
        // FIXED: Use safe boolean helper
        isEditMode = getBooleanPreference("edit_mode", false);
        selectedApps.clear();
        appAdapter.notifyDataSetChanged();
    }

    private void saveCurrentCategory() {
        prefs.edit().putString(KEY_LAST_CATEGORY, currentCategory).apply();
    }

    /**
     * Start periodic playtime updates
     */
    private void startPlaytimeUpdates() {
        // Only start if permission is granted
        if (!prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            return;
        }

        playtimeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updatePlaytimeData();
                playtimeHandler.postDelayed(this, 30000); // Update every 30 seconds
            }
        };
        playtimeHandler.post(playtimeUpdateRunnable);
    }

    /**
     * Update playtime data for all apps
     */
    private void updatePlaytimeData() {
        if (playtimeTracker == null) return;

        runOnUiThread(() -> {
            try {
                // Get playtime for different time ranges
                Map<String, Long> todayPlaytime = playtimeTracker.getTodayPlaytime();
                Map<String, Long> weekPlaytime = playtimeTracker.getLast7DaysPlaytime();
                Map<String, Long> monthPlaytime = playtimeTracker.getLast30DaysPlaytime();
                Map<String, Long> allTimePlaytime = playtimeTracker.getAllTimePlaytime();

                // Get currently running app
                String currentPackage = playtimeTracker.getCurrentRunningApp();

                // Update all apps in appList
                for (AppInfo app : appList) {
                    app.playtimeToday = todayPlaytime.getOrDefault(app.packageName, 0L);
                    app.playtimeWeek = weekPlaytime.getOrDefault(app.packageName, 0L);
                    app.playtimeMonth = monthPlaytime.getOrDefault(app.packageName, 0L);
                    app.playtimeAllTime = allTimePlaytime.getOrDefault(app.packageName, 0L);
                    app.isCurrentlyRunning = app.packageName.equals(currentPackage);
                }

                // Refresh adapter if we're showing playtime in UI
                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }

            } catch (Exception e) {
            }
        });
    }

    /**
     * Show playtime details for an app
     */
    private void showPlaytimeDetails(AppInfo app) {
        String playtimeInfo =
                getString(R.string.main_playtime_for, app.label) + "\n\n" +
                        getString(R.string.main_time_today) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeToday) + "\n" +
                        getString(R.string.main_time_this_week) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeWeek) + "\n" +
                        getString(R.string.main_time_this_month) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeMonth) + "\n" +
                        getString(R.string.main_time_all_time) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeAllTime) + "\n" +
                        (app.isCurrentlyRunning ? "\n" + getString(R.string.main_currently_running) : "");

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_playtime_stats_title))
                .setMessage(playtimeInfo)
                .setPositiveButton(getString(R.string.main_btn_ok), null)
                .create());
    }

    /**
     * Show playtime leaderboard
     */
    public void showPlaytimeLeaderboard() {
        if (playtimeTracker == null) return;

        String[] timeRanges = {getString(R.string.main_time_today), getString(R.string.main_time_this_week),
                getString(R.string.main_time_this_month), getString(R.string.main_time_all_time)};
        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_most_played_title))
                .setItems(timeRanges, (d, which) -> {
                    PlaytimeTracker.TimeRange range;
                    switch (which) {
                        case 0:
                            range = PlaytimeTracker.TimeRange.TODAY;
                            break;
                        case 1:
                            range = PlaytimeTracker.TimeRange.WEEK;
                            break;
                        case 2:
                            range = PlaytimeTracker.TimeRange.MONTH;
                            break;
                        default:
                            range = PlaytimeTracker.TimeRange.ALL_TIME;
                            break;
                    }
                    showLeaderboardForRange(range);
                })
                .create());
    }

    /**
     * Show leaderboard for specific time range
     */
    private void showLeaderboardForRange(PlaytimeTracker.TimeRange range) {
        if (playtimeTracker == null) return;

        List<PlaytimeTracker.PlaytimeEntry> leaderboard =
                playtimeTracker.getPlaytimeLeaderboard(20, range);

        if (leaderboard.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_no_playtime_data), Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.main_top20_header)).append("\n\n");

        int rank = 1;
        for (PlaytimeTracker.PlaytimeEntry entry : leaderboard) {
            // Get app name from package using appInfo
            String packageName = entry.appInfo != null ? entry.appInfo.packageName : "";
            String appName = packageName;

            // Try to find a friendly name
            for (AppInfo app : appList) {
                if (app.packageName.equals(packageName)) {
                    appName = app.label;
                    break;
                }
            }

            // If still package name, clean it up
            if (appName.equals(packageName) && !appName.isEmpty()) {
                appName = cleanUpPackageName(packageName);
            }

            message.append(rank).append(". ")
                    .append(appName)
                    .append(" - ").append(entry.getFormattedPlaytime())
                    .append("\n");
            rank++;
        }

        String rangeText;
        switch (range) {
            case TODAY:
                rangeText = getString(R.string.main_time_today);
                break;
            case WEEK:
                rangeText = getString(R.string.main_time_this_week);
                break;
            case MONTH:
                rangeText = getString(R.string.main_time_this_month);
                break;
            default:
                rangeText = getString(R.string.main_time_all_time);
                break;
        }

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_leaderboard_title, rangeText))
                .setMessage(message.toString())
                .setPositiveButton(getString(R.string.main_btn_ok), null)
                .create());
    }

    /**
     * Toggle playtime display in grid
     */
    private void togglePlaytimeDisplay() {
        // You can cycle through time ranges
        PlaytimeTracker.TimeRange[] ranges = PlaytimeTracker.TimeRange.values();
        int nextIndex = (currentPlaytimeRange.ordinal() + 1) % ranges.length;
        currentPlaytimeRange = ranges[nextIndex];

        String rangeText;
        switch (currentPlaytimeRange) {
            case TODAY:
                rangeText = getString(R.string.main_time_today);
                break;
            case WEEK:
                rangeText = getString(R.string.main_time_this_week);
                break;
            case MONTH:
                rangeText = getString(R.string.main_time_this_month);
                break;
            default:
                rangeText = getString(R.string.main_time_all_time);
                break;
        }

        Toast.makeText(this, getString(R.string.main_toast_showing_playtime, rangeText), Toast.LENGTH_SHORT).show();
        appAdapter.notifyDataSetChanged();
    }

    public void refreshDisplay() {
        runOnUiThread(() -> {
            if (appAdapter != null) {
                // Force complete rebind of all items
                appAdapter.notifyItemRangeChanged(0, appAdapter.getItemCount());

                // Force grid to redraw completely
                if (appsGrid != null) {
                    appsGrid.invalidate();
                    appsGrid.requestLayout();
                    appsGrid.scheduleLayoutAnimation();
                }
            }
        });
    }

    /**
     * RESTORED: Update icon sizes using the old launcher logic
     * Sets a FIXED column width, letting the GridView calculate columns naturally
     * This is what made the old launcher work perfectly on Quest
     */
    public void updateIconSizes() {
        isEditMode = getBooleanPreference("edit_mode", false);

        // Get the user's selected icon scale index (0-4, default 2 = 125dp)
        int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
        int iconSizeDp = ICON_SCALES_DP[scaleIndex];

        // Save the actual icon size in dp for the adapter to use
        prefs.edit().putInt("icon_size", iconSizeDp).apply();

        // Calculate columns based on fixed icon size
        int columns = calculateOptimalColumns();

        if (appsGrid != null) {
            GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
            if (glm != null) {
                glm.setSpanCount(columns);
                if (gridSpacingDecoration != null) {
                    gridSpacingDecoration.setSpanCount(columns);
                }
            }
        }

        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }

        Toast.makeText(this, getString(R.string.main_toast_icon_size, iconSizeDp, columns), Toast.LENGTH_SHORT).show();
    }

    /** Mistura a cor do tema com um cinza bem escuro: amount 1 = cor do tema, 0 = cinza escuro. */
    private static int blendToDarkGray(int color, float amount) {
        final int dark = 0x1C; // #1C1D20
        int r = Math.round(dark + (Color.red(color) - dark) * amount);
        int g = Math.round(dark + 1 + (Color.green(color) - dark - 1) * amount);
        int b = Math.round(dark + 4 + (Color.blue(color) - dark - 4) * amount);
        return Color.rgb(r, g, b);
    }

    public void updateBackground() {
        runOnUiThread(() -> {
            try {
                View mainLayout = findViewById(R.id.mainLayout);
                if (mainLayout == null) return;

                int opacity = prefs.getInt("background_opacity", 100);
                // No Quest a parte transparente da janela aparece preta. Entao a barrinha nao mexe
                // na transparencia: ela escurece o painel ate um cinza bem escuro, nunca preto.
                float mix = Math.max(0f, Math.min(1f, opacity / 100f));

                // A janela fica transparente; a opacidade vale para o painel cinza e a barra lateral
                mainLayout.setAlpha(1.0f);
                mainLayout.setBackgroundColor(Color.TRANSPARENT);

                com.neto.orbitalauncher.theme.Theme t =
                        com.neto.orbitalauncher.theme.ThemeManager.getInstance(this).getCurrentTheme();

                MaterialCardView panel = findViewById(R.id.panelCard);
                if (panel != null) {
                    panel.setCardBackgroundColor(blendToDarkGray(t.bgSecondary, mix));
                }
                View side = findViewById(R.id.sideNav);
                if (side != null) {
                    side.setBackgroundColor(blendToDarkGray(t.bgPrimary, mix));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * FIXED: refreshAll() no longer creates a new GridLayoutManager.
     * It just updates the span count on the existing one, keeping the
     * OnLayoutChangeListener from setupRecyclerView() intact.
     */
    public void refreshAll() {
        runOnUiThread(() -> {
            if (searchEditText != null) {
                searchEditText.setText("");
            }

            // DON'T reset to "All Apps" - load saved category instead
            currentCategory = prefs.getString(KEY_LAST_CATEGORY, "All Apps");

            // Validate category still exists
            if (!currentCategory.equals("All Apps") && !currentCategory.equals("Meta Apps")
                    && !categories.containsKey(currentCategory)) {
                currentCategory = "All Apps";
                saveCurrentCategory();
            }

            updateCategoryButtonStates(currentCategory);
            updateSearchStatus("", false);
            selectedApps.clear();

            loadCategories();
            loadUserApps();
            buildCategoryBar();
            filterApps("");

            int columns = calculateOptimalColumns();
            if (appsGrid != null) {
                GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
                if (glm != null) {
                    glm.setSpanCount(columns);
                    if (gridSpacingDecoration != null) {
                        gridSpacingDecoration.setSpanCount(columns);
                    }
                }
            }

            if (appAdapter != null) {
                appAdapter.notifyDataSetChanged();
            }

            updateBackground();

            if (appsGrid != null) {
                appsGrid.invalidate();
                appsGrid.requestLayout();
            }

            updateWifiSignal();

            Toast.makeText(this, getString(R.string.main_toast_launcher_refreshed), Toast.LENGTH_SHORT).show();
        });
    }

    private GridSpacingItemDecoration gridSpacingDecoration;

    /**
     * Tracks whether we've already played the entry animation this session.
     */
    private boolean entryAnimationPlayed = false;
    // Temporary override for icon scale used by the hover-fix trick: when set
    // to a non-negative value, loadAppIcon uses this instead of the pref.
    // Used to do a "shrink then grow" sequence at startup that triggers the
    // layout pass needed for hover hitboxes to be correct on all Quest headsets.
    private int iconScaleOverride = -1;

    /**
     * VR POLISH: animates ALL currently visible cards flying in from 8 different
     * directions. Runs ONCE per session, triggered by a layout listener after
     * the grid has fully laid out its children.
     *
     * Using a batch approach (all-at-once after layout) instead of per-bind
     * animation avoids race conditions where some cards animate and others
     * don't, depending on when their onBindViewHolder fires relative to the
     * RecyclerView's layout pass.
     */
    private void playEntryAnimation() {
        if (entryAnimationPlayed || appsGrid == null) return;
        if (appsGrid.getChildCount() == 0) return;  // no children yet, wait
        entryAnimationPlayed = true;

        float density = getResources().getDisplayMetrics().density;
        float distance = 300f * density;

        // STEP 1: While the grid is still alpha=0 (invisible), set each child
        // to its off-screen starting state. This way when we reveal the grid,
        // the children are already invisible (alpha 0) and off-position - no
        // static-layout flash.
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View card = appsGrid.getChildAt(i);
            if (card == null) continue;

            int direction = i % 8;
            float startX = 0f, startY = 0f;
            switch (direction) {
                case 0: startY = -distance; break;
                case 1: startX = distance;  startY = -distance; break;
                case 2: startX = distance;  break;
                case 3: startX = distance;  startY = distance;  break;
                case 4: startY = distance;  break;
                case 5: startX = -distance; startY = distance;  break;
                case 6: startX = -distance; break;
                case 7: startX = -distance; startY = -distance; break;
            }

            float startRotation = (direction % 2 == 0) ? -8f : 8f;

            card.setTranslationX(startX);
            card.setTranslationY(startY);
            card.setAlpha(0f);
            card.setScaleX(0.5f);
            card.setScaleY(0.5f);
            card.setRotation(startRotation);
        }

        // STEP 2: Reveal the grid container - children still invisible because
        // their individual alpha is 0
        appsGrid.setAlpha(1f);

        // Track when the last card's animation finishes so we can do
        // a final cleanup that fixes hover-dispatch issues on some headsets.
        // The user reported that resizing icons fixes broken hover - this
        // mimics what resize does (a notifyDataSetChanged rebind) which
        // resets RecyclerView's internal touch/hover dispatch state.
        final int totalCards = appsGrid.getChildCount();
        long maxDelay = (totalCards - 1) * 45L + 650L + 100L;  // last card finish + buffer

        // STEP 3: Animate each child flying in
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            final View card = appsGrid.getChildAt(i);
            if (card == null) continue;

            long delay = i * 45L;

            card.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setDuration(650)
                    .setStartDelay(delay)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        // Hard-reset to clean rest state at end of entry animation
                        card.setTranslationX(0f);
                        card.setTranslationY(0f);
                        card.setAlpha(1f);
                        card.setScaleX(1f);
                        card.setScaleY(1f);
                        card.setRotation(0f);
                    })
                    .start();
        }

        // Final cleanup: do the "resize trick" that fixes hover dispatch
        // on some Quest headsets. We temporarily render icons at a SMALLER
        // size, then restore to the actual size. The size transition forces
        // a real layout pass which makes hover hitboxes correct.
        // This is what the user found empirically: resizing fixes hover.
        appsGrid.postDelayed(() -> {
            int actualScale = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
            // Pick a DIFFERENT scale to ensure dimensions actually change
            int tempScale = (actualScale == 0) ? 1 : 0;

            iconScaleOverride = tempScale;
            if (appAdapter != null) appAdapter.notifyDataSetChanged();

            // After one layout pass, restore actual scale
            appsGrid.post(() -> appsGrid.post(() -> {
                iconScaleOverride = -1;
                if (appAdapter != null) appAdapter.notifyDataSetChanged();
            }));
        }, maxDelay);
    }

    /**
     * Sets up a polling check that fires the entry animation once children
     * have been laid out. The initial delay (300ms) lets any other initial
     * setup logic (column recalculation, deferred notifyDataSetChanged, etc.)
     * complete and stabilize before we animate, so the animation isn't
     * destroyed by a subsequent rebinding.
     */
    private void scheduleEntryAnimation() {
        if (appsGrid == null) return;
        // Initial delay lets the layout settle (column calc, initial bindings)
        appsGrid.postDelayed(() -> tryPlayEntryAnimation(0), 300);
    }

    private void tryPlayEntryAnimation(int attempt) {
        if (entryAnimationPlayed || appsGrid == null) return;
        if (attempt > 20) {
            // Safety: never hide the grid forever - reveal it even if animation fails
            appsGrid.setAlpha(1f);
            entryAnimationPlayed = true;
            return;
        }

        if (appsGrid.getChildCount() > 0) {
            playEntryAnimation();
        } else {
            // No children yet - try again in 100ms
            appsGrid.postDelayed(() -> tryPlayEntryAnimation(attempt + 1), 100);
        }
    }

    /**
     * VR POLISH: Adds hover, focus, and press animations to a card.
     * On Quest, the controller pointer triggers hover events on Android views,
     * making this effectively a "looking-at-it / pointing-at-it" highlight.
     *
     *   - HOVER / FOCUS:  card pops forward dramatically (scale 1.15×, +40dp lift)
     *                     with an overshoot bounce when pointer enters
     *   - PRESS:          card briefly compresses, then POPS even further on release
     *                     (scale 1.22×, +56dp lift) before settling back to hover
     */
    /** Troca de pasta com um fade simples e rapido (padrao Android). */
    private void switchToCategoryAnimated(String newCategory) {
        if (newCategory == null) return;
        if (newCategory.equals(currentCategory)) return;  // no change, skip animation

        Log.i("MainActivity", "switchToCategoryAnimated: " + currentCategory + " → " + newCategory);

        animateGridTransition(() -> {
            currentCategory = newCategory;
            saveCurrentCategory();
            updateCategoryButtonStates(newCategory);
            // Clear search text here (during the hidden phase) so the
            // TextWatcher doesn't fire filterApps while the exit animation
            // is running and kill it.
            if (searchEditText != null) {
                searchEditText.setText("");
            }
            filterApps("");
        });
    }

    /** Os apps somem rapido, troca o conteudo, e os novos aparecem com fade. */
    private void animateGridTransition(Runnable dataSwap) {
        if (appsGrid == null) {
            dataSwap.run();
            return;
        }
        appsGrid.animate().cancel();
        appsGrid.animate()
                .alpha(0f)
                .setDuration(90)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    dataSwap.run();
                    appsGrid.scrollToPosition(0);
                    appsGrid.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private boolean categoryBarEntryAnimated = false;
    private final java.util.HashSet<String> animatedCategoryNames = new java.util.HashSet<>();

    /**
     * VR POLISH: hover/focus/press animations for category buttons.
     * Same drama as the app cards but slightly less dramatic since buttons
     * are smaller. Uses scale + translation only (no translationZ since
     * default Button widgets don't have card-like shadows).
     */
    private void applyButtonInteractionEffects(View btn) {
        if (btn == null) return;

        // Log button setup once so we can verify the latest build is installed
        String btnTag = (btn instanceof Button) ? ((Button) btn).getText().toString() : "button";
        Log.i("MainActivity", "Applying interaction effects to: " + btnTag);

        final float hoverScale = 1.10f;
        final float pressScale = 0.94f;
        final float popScale = 1.16f;

        btn.setFocusable(true);
        btn.setClickable(true);

        btn.setOnHoverListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_HOVER_ENTER:
                    v.animate().cancel();
                    v.animate().scaleX(hoverScale).scaleY(hoverScale)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_HOVER_EXIT:
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    break;
            }
            return false;
        });

        btn.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().cancel();
            if (hasFocus) {
                v.animate().scaleX(hoverScale).scaleY(hoverScale)
                        .setDuration(180)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                        .start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            }
        });

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(pressScale).scaleY(pressScale)
                            .setDuration(70)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    Log.i("MainActivity", "Button ACTION_UP fired");
                    v.animate().cancel();
                    v.animate().scaleX(popScale).scaleY(popScale)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                            .withEndAction(() -> {
                                float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                                v.animate().scaleX(endScale).scaleY(endScale)
                                        .setDuration(200)
                                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                        .start();
                            })
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                    v.animate().scaleX(endScale).scaleY(endScale).setDuration(180).start();
                    break;
            }
            return false;
        });
    }

    /**
     * Animates a single category button popping in from below.
     * Used for both the initial entry animation and for buttons added later
     * (e.g., when a new category is created in Settings).
     */
    private void animateCategoryButtonEntry(View btn, int indexInBar) {
        if (btn == null) return;
        float density = getResources().getDisplayMetrics().density;

        btn.setTranslationY(80f * density);   // start below
        btn.setAlpha(0f);
        btn.setScaleX(0.6f);
        btn.setScaleY(0.6f);
        btn.setRotation((indexInBar % 2 == 0) ? -6f : 6f);

        btn.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setStartDelay(indexInBar * 60L)
                .setDuration(500)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                .start();
    }

    /**
     * Initial entry animation for the entire category bar.
     * Only fires once per session (subsequent rebuilds don't re-animate).
     */
    private void animateCategoryBarEntry() {
        if (categoryBarEntryAnimated || categoryBar == null) return;
        if (categoryBar.getChildCount() == 0) return;
        categoryBarEntryAnimated = true;

        for (int i = 0; i < categoryBar.getChildCount(); i++) {
            View btn = categoryBar.getChildAt(i);
            animateCategoryButtonEntry(btn, i);
            // Track this button's label so it doesn't re-animate on later rebuilds
            if (btn instanceof Button) {
                animatedCategoryNames.add(categoryButtonKey((Button) btn));
            }
        }
    }

    /** Cartao claro arredondado por tras do item sob o ponteiro; a sombra fica embaixo dele. */
    private void setHoverBackdrop(View v, boolean on) {
        if (!(v instanceof com.google.android.material.card.MaterialCardView)) return;
        final com.google.android.material.card.MaterialCardView c = (com.google.android.material.card.MaterialCardView) v;
        final float density = getResources().getDisplayMetrics().density;
        c.setRadius(16f * density);
        c.setCardBackgroundColor(on ? android.graphics.Color.parseColor("#4D4E53") : android.graphics.Color.TRANSPARENT);
        c.setCardElevation(on ? 10f * density : 0f);
        c.setClipToOutline(false);
    }

    private void applyCardInteractionEffects(View card) {
        if (card == null) return;

        card.setFocusable(true);
        card.setClickable(true);

        // Sem ampliacao e sem animacao: so o cartao claro com sombra, que some na hora ao tirar o ponteiro.
        final boolean[] isHovered = {false};

        card.setOnHoverListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_HOVER_ENTER:
                case android.view.MotionEvent.ACTION_HOVER_MOVE:
                    if (isHovered[0]) break;
                    isHovered[0] = true;
                    setHoverBackdrop(v, true);
                    break;
                case android.view.MotionEvent.ACTION_HOVER_EXIT:
                    isHovered[0] = false;
                    setHoverBackdrop(v, false);
                    break;
            }
            return false;  // deixa o clique passar
        });

        // Foco (dpad) mostra o mesmo cartao
        card.setOnFocusChangeListener((v, hasFocus) -> setHoverBackdrop(v, hasFocus));

        card.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_CANCEL && !v.isHovered() && !v.isFocused()) {
                setHoverBackdrop(v, false);
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        int columns = calculateOptimalColumns();

        GridLayoutManager layoutManager = new GridLayoutManager(this, columns) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };

        appsGrid.setLayoutManager(layoutManager);
        appsGrid.setHasFixedSize(false);
        appsGrid.setItemViewCacheSize(50);
        appsGrid.setDrawingCacheEnabled(true);
        appsGrid.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);
        appsGrid.setItemAnimator(null);

        // Entry animation is now handled programmatically per-card in
        // animateCardEntryIfNeeded() so each card can come from a different direction.

        float density = getResources().getDisplayMetrics().density;
        int spacingInPixels = (int) (1 * density);
        gridSpacingDecoration = new GridSpacingItemDecoration(columns, spacingInPixels, true);
        appsGrid.addItemDecoration(gridSpacingDecoration);

        // Listen for window resize - recalculate columns when appsGrid bounds change
        // This handles multi-window resizing on Quest (no Activity restart)
        appsGrid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            int lastWidth = 0;
            int lastHeight = 0;
            Handler resizeHandler = new Handler();
            Runnable resizeRunnable;

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int newWidth = right - left;
                int newHeight = bottom - top;

                if ((newWidth == lastWidth && newHeight == lastHeight) || newWidth == 0) return;
                lastWidth = newWidth;
                lastHeight = newHeight;

                // Debounce rapid resize events
                if (resizeRunnable != null) {
                    resizeHandler.removeCallbacks(resizeRunnable);
                }

                resizeRunnable = () -> {
                    // Recalculate columns for new width
                    int newColumns = calculateOptimalColumns();
                    GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
                    if (glm != null && glm.getSpanCount() != newColumns) {
                        glm.setSpanCount(newColumns);

                        if (gridSpacingDecoration != null) {
                            gridSpacingDecoration.setSpanCount(newColumns);
                        }

                        // Force adapter to recalculate item sizes
                        if (appAdapter != null) {
                            appAdapter.notifyDataSetChanged();
                        }

                        // Force grid to redraw
                        appsGrid.invalidate();
                        appsGrid.requestLayout();
                    }
                };

                resizeHandler.postDelayed(resizeRunnable, 100);
            }
        });
    }

    public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        public void setSpanCount(int spanCount) {
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;

                if (position < spanCount) {
                    outRect.top = spacing;
                }
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = spacing;
                }
            }
        }
    }

    /**
     * RESTORED: Calculate columns based on FIXED icon size (old launcher logic)
     * This calculates how many fixed-size cards fit in the available width
     * Instead of calculating icon size based on columns
     */
    private int calculateOptimalColumns() {
        // Get current window width - in multi-window mode this differs from full screen
        int usableWidthPx;
        if (appsGrid != null && appsGrid.getWidth() > 0) {
            usableWidthPx = appsGrid.getWidth();
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            usableWidthPx = displayMetrics.widthPixels;
        }

        // Get the FIXED icon size from user preference (restored old logic)
        int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
        int iconSizeDp = ICON_SCALES_DP[scaleIndex];
        float density = getResources().getDisplayMetrics().density;
        int iconWidthPx = (int) (iconSizeDp * density);

        // Calculate total card width: icon width + horizontal overhead (margin + padding)
        int cardOverheadPx = (int) (CARD_HORIZONTAL_OVERHEAD_DP * density);
        int totalCardWidthPx = iconWidthPx + cardOverheadPx;

        // Calculate how many of these fixed-size cards fit in the available width
        int columns = usableWidthPx / totalCardWidthPx;
        columns = Math.max(1, columns);

        // Apply maximum column limits (prevent too many tiny cells)
        if (iconSizeDp <= 90) {
            columns = Math.min(columns, 15);
        } else if (iconSizeDp <= 110) {
            columns = Math.min(columns, 12);
        } else {
            columns = Math.min(columns, 10);
        }

        return columns;
    }

    private void loadUserApps() {
        appList.clear();

        List<ApplicationInfo> allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : allApps) {
            try {
                String packageName = appInfo.packageName;

                // Always hidden - never shown in any view
                if (packageName.equals("com.oculus.os.chargecontrol") ||
                        packageName.equals("com.oculus.os.clearactivity") ||
                        packageName.equals("com.oculus.firsttimenux") ||
                        packageName.equals("com.meta.HyperscapeHmdCapture") ||
                        packageName.equals("com.oculus.vrshell") ||
                        packageName.equals("com.oculus.os.qrcodereader") ||
                        packageName.equals("com.oculus.q4bservice") ||
                        packageName.equals("com.oculus.os.voidactivity") ||
                        packageName.equals("com.meta.handseducationmodule") ||
                        packageName.equals("com.oculus.panelapp.library") ||
                        packageName.equals("com.android.settings") ||
                        packageName.equals("com.oculus.panelapp.kiosk") ||
                        packageName.equals("com.android.documentsui") ||
                        packageName.equals("com.oculus.systemux") ||
                        packageName.equals("com.oculus.horizonmediaplayer") ||
                        packageName.equals("com.oculus.ocms")) {
                    continue;
                }


                // FIXED: Use safe boolean helper for hidden apps
                boolean isHidden = getBooleanPreference("hidden_" + packageName, false);
                if (isHidden) {
                    continue;
                }

                String appName = getAppName(packageName, appInfo);

                if (appName == null || appName.isEmpty()) {
                    continue;
                }

                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
                    launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launcherIntent.setPackage(packageName);

                    List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
                    if (resolveInfos == null || resolveInfos.isEmpty()) {
                        continue;
                    }
                }

                AppInfo app = new AppInfo();
                app.label = appName;
                app.packageName = packageName;
                app.icon = appInfo.loadIcon(packageManager);
                app.isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                app.githubIconUrl = getGitHubIconUrl(packageName);
                app.isHidden = isHidden;

                // Get install/update info from PackageInfo
                try {
                    PackageInfo pkgInfo = packageManager.getPackageInfo(packageName, 0);
                    app.versionName = pkgInfo.versionName != null ? pkgInfo.versionName : "N/A";

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    app.firstInstallDate = sdf.format(new Date(pkgInfo.firstInstallTime));
                    app.installTime = pkgInfo.firstInstallTime;
                    app.lastUpdateDate = sdf.format(new Date(pkgInfo.lastUpdateTime));

                    // Check if from store
                    try {
                        String installer = packageManager.getInstallerPackageName(packageName);
                        app.isStoreApp = "com.oculus.store".equals(installer) ||
                                "com.android.vending".equals(installer) ||
                                "com.oculus.tw".equals(installer);
                        app.isSideloaded = !app.isSystemApp && !app.isStoreApp;
                    } catch (Exception e) {
                        app.isStoreApp = false;
                        app.isSideloaded = !app.isSystemApp;
                    }
                } catch (Exception e) {
                    app.versionName = "N/A";
                    app.firstInstallDate = "N/A";
                    app.lastUpdateDate = "N/A";
                }

                // Load saved category from preferences
                String savedCategory = prefs.getString("cat_" + packageName, "Uncategorized");
                // Defensive: if the saved category no longer exists (e.g., stale
                // data from a previous version), treat the app as uncategorized.
                if (!savedCategory.equals("Uncategorized") && !categories.containsKey(savedCategory)) {
                    savedCategory = "Uncategorized";
                }
                app.category = savedCategory;

                // Also check if it's in any category from categoryPrefs
                for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                    if (entry.getValue().contains(packageName)) {
                        app.category = entry.getKey();
                        break;
                    }
                }

                // Apply friendly name for known Meta system apps
                if (META_APP_NAMES.containsKey(packageName)) {
                    app.label = META_APP_NAMES.get(packageName);
                }
                appList.add(app);

            } catch (Exception e) {
                continue;
            }
        }

        appList.sort((a1, a2) -> {
            String label1 = a1.label != null ? a1.label.toLowerCase() : "";
            String label2 = a2.label != null ? a2.label.toLowerCase() : "";
            return label1.compareTo(label2);
        });

        seedDefaultFolders();
        preloadIcons();
    }

    /**
     * Pastas padrao: cada app conhecido (pelo nome) vai para a sua pasta uma unica vez.
     * Se o usuario tirar o app de la depois, ele nao volta. "*" no fim = comeca com.
     */
    private static final String[][] DEFAULT_FOLDER_APPS = {
            {"Games", "beatsaber", "moonlightxr", "portalvr", "re4vr", "sourcevrport",
                    "superhotvr", "tacticalassaultvr", "virtualdesktop*"},
            {"Media", "youtube*"},
            {"Social", "facebook", "instagram", "whatsapp"},
            {"Tools", "files", "arquivos", "orbitalauncher", "shizuku*"},
    };
    private static final String KEY_DEFAULT_FOLDERS_SEEDED = "default_folders_seeded";

    private void seedDefaultFolders() {
        Set<String> seeded = new HashSet<>(prefs.getStringSet(KEY_DEFAULT_FOLDERS_SEEDED, new HashSet<>()));
        boolean changed = false;
        for (AppInfo app : appList) {
            if (seeded.contains(app.packageName)) continue;
            String folder = defaultFolderFor(app);
            if (folder == null) continue;
            seeded.add(app.packageName);
            changed = true;
            if (isInAnyCategory(app.packageName)) continue;
            String key = findCategoryKey(folder);
            if (key == null) continue;
            Set<String> updated = new HashSet<>(categories.get(key));
            updated.add(app.packageName);
            categoryPrefs.edit().putStringSet("cat_" + key, updated).apply();
            categories.put(key, updated);
            prefs.edit().putString("cat_" + app.packageName, key).apply();
            app.category = key;
        }
        if (changed) prefs.edit().putStringSet(KEY_DEFAULT_FOLDERS_SEEDED, seeded).apply();
    }

    private String defaultFolderFor(AppInfo app) {
        if (getPackageName().equals(app.packageName)) return "Tools";
        String n = java.text.Normalizer.normalize(app.label == null ? "" : app.label, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (n.isEmpty()) return null;
        for (String[] row : DEFAULT_FOLDER_APPS) {
            for (int i = 1; i < row.length; i++) {
                String k = row[i];
                boolean match = k.endsWith("*") ? n.startsWith(k.substring(0, k.length() - 1)) : n.equals(k);
                if (match) return row[0];
            }
        }
        return null;
    }

    /** Acha a pasta com esse nome, sem ligar para maiusculas. */
    private String findCategoryKey(String name) {
        for (String key : categories.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return null;
    }

    private boolean isInAnyCategory(String packageName) {
        for (Set<String> categoryApps : categories.values()) {
            if (categoryApps.contains(packageName)) return true;
        }
        return false;
    }

    /** "Todos": todos os apps, menos os da Meta (a nao ser que estejam em alguma pasta). */
    private boolean showInAllApps(AppInfo app) {
        return !isInSystemPackageList(app.packageName) || isInAnyCategory(app.packageName);
    }

    // Ordem que o usuario montou arrastando, salva separada para cada pasta
    private static final String KEY_ORDER_PREFIX = "order_";

    private void applySavedOrder(List<AppInfo> list, String category) {
        String saved = prefs.getString(KEY_ORDER_PREFIX + category, "");
        if (saved.isEmpty()) return;
        final Map<String, Integer> index = new HashMap<>();
        String[] pkgs = saved.split(",");
        for (int i = 0; i < pkgs.length; i++) index.put(pkgs[i], i);
        // Sort estavel: apps sem posicao salva ficam no fim, em ordem alfabetica
        list.sort((a, b) -> Integer.compare(
                index.containsKey(a.packageName) ? index.get(a.packageName) : Integer.MAX_VALUE,
                index.containsKey(b.packageName) ? index.get(b.packageName) : Integer.MAX_VALUE));
    }

    private void saveCurrentOrder() {
        StringBuilder sb = new StringBuilder();
        for (AppInfo app : filteredList) {
            if (sb.length() > 0) sb.append(',');
            sb.append(app.packageName);
        }
        prefs.edit().putString(KEY_ORDER_PREFIX + currentCategory, sb.toString()).apply();
    }

    private String getAppName(String packageName, ApplicationInfo appInfo) {
        String appName = null;

        CharSequence label = appInfo.loadLabel(packageManager);
        if (label != null && !label.toString().trim().isEmpty()) {
            appName = label.toString().trim();
        }

        if (appName == null || appName.isEmpty() || appName.equals(packageName) ||
                appName.replace(".", "").equals(packageName.replace(".", ""))) {
            try {
                PackageInfo pkgInfo = packageManager.getPackageInfo(packageName, 0);
                if (pkgInfo.applicationInfo != null) {
                    CharSequence pkgLabel = pkgInfo.applicationInfo.loadLabel(packageManager);
                    if (pkgLabel != null && !pkgLabel.toString().trim().isEmpty()) {
                        appName = pkgLabel.toString().trim();
                    }
                }
            } catch (Exception e) {
                // Continue to fallback
            }
        }

        if (appName == null || appName.isEmpty() || appName.equals(packageName) ||
                appName.length() < 3 || appName.replace(".", "").length() < 3) {
            appName = cleanUpPackageName(packageName);
        }

        return appName;
    }

    private String cleanUpPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "App";
        }

        String name = packageName;
        if (name.startsWith("com.")) {
            name = name.substring(4);
        } else if (name.startsWith("org.")) {
            name = name.substring(4);
        } else if (name.startsWith("net.")) {
            name = name.substring(4);
        }

        String[] parts = name.split("\\.");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];

            if (lastPart.endsWith("VR")) {
                lastPart = lastPart.substring(0, lastPart.length() - 2);
            }
            if (lastPart.endsWith("vr")) {
                lastPart = lastPart.substring(0, lastPart.length() - 2);
            }
            if (lastPart.endsWith("Quest")) {
                lastPart = lastPart.substring(0, lastPart.length() - 5);
            }

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < lastPart.length(); i++) {
                char c = lastPart.charAt(i);
                if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(lastPart.charAt(i - 1))) {
                    result.append(" ");
                }
                result.append(i == 0 ? Character.toUpperCase(c) : c);
            }

            return result.toString().trim();
        }

        return packageName;
    }

    private void preloadIcons() {
        if (!ENABLE_IMAGE_CACHING) return;

        List<AppInfo> snapshot;
        synchronized (appList) {
            snapshot = new ArrayList<>(appList);
        }

        executorService.execute(() -> {
            // Use the restored icon scale system
            int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
            int iconSizeDp = ICON_SCALES_DP[scaleIndex];
            int iconHeightDp = (int) (iconSizeDp * 0.5625f);
            float density = getResources().getDisplayMetrics().density;
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconHeightDp * density);

            for (AppInfo app : snapshot) {
                try {
                    Glide.with(MainActivity.this)
                            .load(app.githubIconUrl)
                            .preload(iconWidthPx, iconHeightPx);
                } catch (Exception e) {
                    // Ignore preload errors
                }
            }
        });
    }

    private String getGitHubIconUrl(String packageName) {
        return GITHUB_ICON_BASE_URL + packageName + ".jpg";
    }

    private boolean isSystemApp(ApplicationInfo appInfo) {
        boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        return isSystemApp || isUpdatedSystemApp;
    }

    private boolean isInSystemPackageList(String packageName) {
        for (String systemPackage : SYSTEM_PACKAGES) {
            if (packageName.startsWith(systemPackage)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppLaunchable(String packageName) {
        try {
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                return true;
            }

            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launcherIntent.setPackage(packageName);

            List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
            return resolveInfos != null && !resolveInfos.isEmpty();

        } catch (Exception e) {
            return false;
        }
    }

    private void filterApps(String query) {
        List<AppInfo> newFilteredList = new ArrayList<>();

        // Meta Apps fixed category - read-only, shows Meta/Oculus apps
        if ("Meta Apps".equals(currentCategory)) {
            for (AppInfo app : appList) {
                if (!isInSystemPackageList(app.packageName)) continue;
                if (query.isEmpty() || app.label.toLowerCase().contains(query.toLowerCase())) {
                    newFilteredList.add(app);
                }
            }
            newFilteredList.sort((a1, a2) -> (a1.label != null ? a1.label : "").compareToIgnoreCase(a2.label != null ? a2.label : ""));
            filteredList.clear();
            filteredList.addAll(newFilteredList);
            if (appAdapter != null) appAdapter.notifyDataSetChanged();
            updateSearchStatus(getString(R.string.main_search_meta_status), false);
            return;
        }

        if (query.isEmpty()) {
            if (currentCategory.equals("All Apps")) {
                for (AppInfo app : appList) {
                    if (showInAllApps(app)) newFilteredList.add(app);
                }
            } else {
                Set<String> pkgs = categories.get(currentCategory);
                if (pkgs != null) {
                    for (AppInfo app : appList) {
                        if (pkgs.contains(app.packageName)) {
                            app.category = currentCategory;
                            newFilteredList.add(app);
                        }
                    }
                }
            }
            sortForCurrentMode(newFilteredList, currentCategory);

            updateSearchStatus("", false);

        } else {
            // When searching, show ALL matching apps including favorites
            // (so user can find what to unfavorite or favorite)
            String lowerCaseQuery = query.toLowerCase();

            for (AppInfo app : appList) {
                boolean matchesSearch = app.label != null &&
                        (app.label.toLowerCase().contains(lowerCaseQuery) ||
                                app.packageName.toLowerCase().contains(lowerCaseQuery) ||
                                (app.category != null && app.category.toLowerCase().contains(lowerCaseQuery)));

                if (matchesSearch) {
                    newFilteredList.add(app);
                }
            }

            newFilteredList.sort((a1, a2) -> {
                String label1 = a1.label != null ? a1.label : "";
                String label2 = a2.label != null ? a2.label : "";
                return label1.compareToIgnoreCase(label2);
            });

            String status = getString(newFilteredList.size() != 1 ? R.string.main_search_found_other : R.string.main_search_found_one, newFilteredList.size());
            updateSearchStatus(status, true);
        }

        filteredList.clear();
        filteredList.addAll(newFilteredList);

        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }

        ImageView clearSearch = findViewById(R.id.clearSearch);
        if (clearSearch != null) {
            clearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void launchApp(AppInfo app) {
        try {
            Intent intent = packageManager.getLaunchIntentForPackage(app.packageName);

            if (intent == null) {
                Intent vrQueryIntent = new Intent(Intent.ACTION_MAIN);
                vrQueryIntent.addCategory("com.oculus.intent.category.VR");
                vrQueryIntent.setPackage(app.packageName);
                List<ResolveInfo> vrActivities =
                        packageManager.queryIntentActivities(vrQueryIntent, 0);

                if (!vrActivities.isEmpty()) {
                    ResolveInfo vr = vrActivities.get(0);
                    intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory("com.oculus.intent.category.VR");
                    intent.setComponent(new ComponentName(
                            vr.activityInfo.packageName, vr.activityInfo.name));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                } else if (app.activityName != null && !app.activityName.isEmpty()) {
                    intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.setComponent(new ComponentName(app.packageName, app.activityName));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                } else {
                    Toast.makeText(this, getString(R.string.main_toast_cannot_launch, app.label), Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Log.i("MainActivity", "Launching: " + app.packageName);
            // If user explicitly opens the store, bypass suppression
            if ("com.oculus.store".equals(app.packageName) &&
                    EvolveAccessibilityService.instance != null) {
                EvolveAccessibilityService.instance.setUserOpenedStore();
            }
            moveTaskToBack(true);
            startActivity(intent);

        } catch (Exception e) {
            Log.e("MainActivity", "Launch failed for " + app.packageName, e);
            Toast.makeText(this, getString(R.string.main_toast_error_launching, app.label), Toast.LENGTH_SHORT).show();
        }
    }

    public static class AppInfo {
        public String label;
        public String packageName;
        public String activityName = "";
        public Drawable icon;
        public boolean isSystemApp;
        public String githubIconUrl;
        public String category = "Uncategorized";
        public boolean isHidden = false;

        // NEW: Playtime tracking fields
        public long playtimeToday = 0;
        public long playtimeWeek = 0;
        public long playtimeMonth = 0;
        public long playtimeAllTime = 0;
        public boolean isCurrentlyRunning = false;

        // NEW: Install/Update info
        public String firstInstallDate = "";
        public long installTime = 0;
        public String lastUpdateDate = "";
        public String versionName = "";
        public boolean isStoreApp = false;
        public boolean isSideloaded = false;
    }

    private void loadCategories() {
        categories.clear();
        Map<String, ?> all = categoryPrefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                String categoryName = key.substring(4);
                Set<String> packageSet = categoryPrefs.getStringSet(key, new HashSet<>());
                categories.put(categoryName, packageSet);
            }
        }
    }

    private int moveAppsToCategorySync(String targetCategory, Set<String> appsToMove) {
        int movedCount = 0;

        Map<String, Set<String>> updatedCategories = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            updatedCategories.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        if (!updatedCategories.containsKey(targetCategory)) {
            updatedCategories.put(targetCategory, new HashSet<>());
        }

        Set<String> targetSet = updatedCategories.get(targetCategory);

        for (String packageName : appsToMove) {
            // Remove from all existing categories
            for (Set<String> categorySet : updatedCategories.values()) {
                categorySet.remove(packageName);
            }

            // Add to target category
            targetSet.add(packageName);

            // Save to preferences
            prefs.edit()
                    .putString("cat_" + packageName, targetCategory)
                    .apply();

            // Update the app's category in the appList
            for (AppInfo app : appList) {
                if (app.packageName.equals(packageName)) {
                    app.category = targetCategory;
                    break;
                }
            }

            movedCount++;
        }

        // Save updated categories
        SharedPreferences.Editor editor = categoryPrefs.edit();
        for (Map.Entry<String, Set<String>> entry : updatedCategories.entrySet()) {
            editor.putStringSet("cat_" + entry.getKey(), entry.getValue());
        }
        editor.apply();

        loadCategories();

        return movedCount;
    }

    private int removeAppsFromCategoriesSync(Set<String> appsToRemove) {
        int removedCount = 0;

        List<String> appsList = new ArrayList<>(appsToRemove);

        for (String packageName : appsList) {
            try {
                AppInfo foundApp = null;
                for (AppInfo app : appList) {
                    if (app.packageName.equals(packageName)) {
                        foundApp = app;
                        break;
                    }
                }

                if (foundApp == null) continue;

                for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                    if (entry.getValue().contains(foundApp.packageName)) {
                        Set<String> categorySet = new HashSet<>(entry.getValue());
                        categorySet.remove(foundApp.packageName);
                        categoryPrefs.edit().putStringSet("cat_" + entry.getKey(), categorySet).apply();

                        prefs.edit().remove("cat_" + foundApp.packageName).apply();
                        foundApp.category = "Uncategorized";
                        removedCount++;
                        break;
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }

        loadCategories();

        return removedCount;
    }

    private void buildCategoryBar() {
        if (categoryBar == null) return;

        categoryBar.removeAllViews();

        int categoryCount = categories.size() + 1;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = screenWidth - 48;
        int maxButtonWidth = 120;
        int minButtonWidth = 80;

        float density = getResources().getDisplayMetrics().density;
        int suggestedWidth = Math.min(
                Math.max(availableWidth / Math.max(categoryCount, 3),
                        (int) (minButtonWidth * density)),
                (int) (maxButtonWidth * density)
        );

        Button allAppsBtn = new Button(this);
        allAppsBtn.setText(displayCategoryName("All Apps"));
        allAppsBtn.setTag("All Apps"); // logical key (display text is translated)
        allAppsBtn.setOnClickListener(v -> {
            runOnUiThread(() -> {
                try {
                    if (isEditMode && !selectedApps.isEmpty()) {
                        int removedCount = removeAppsFromCategoriesSync(selectedApps);
                        selectedApps.clear();
                        currentCategory = "All Apps";
                        saveCurrentCategory();
                        updateCategoryButtonStates("All Apps");

                        if (searchEditText != null) {
                            searchEditText.setText("");
                        }

                        loadCategories();
                        filterApps("");

                        if (appAdapter != null) {
                            appAdapter.notifyDataSetChanged();
                        }

                        Toast.makeText(this, getString(R.string.main_toast_removed_from_categories, removedCount), Toast.LENGTH_SHORT).show();
                    } else {
                        Log.i("MainActivity", "All Apps button clicked");
                        switchToCategoryAnimated("All Apps");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.main_toast_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            });
        });

        try {
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.category_button_background);
            if (drawable != null) {
                allAppsBtn.setBackground(drawable);
            } else {
                allAppsBtn.setBackgroundColor(Color.parseColor("#2D2D2D"));
            }
        } catch (Exception e) {
            allAppsBtn.setBackgroundColor(Color.parseColor("#2D2D2D"));
        }

        allAppsBtn.setTextColor(Color.WHITE);
        allAppsBtn.setPadding(8, 4, 8, 4);
        allAppsBtn.setAllCaps(false);
        allAppsBtn.setTextSize(11);

        LinearLayout.LayoutParams allAppsParams = new LinearLayout.LayoutParams(
                suggestedWidth,
                (int) (28 * density)
        );
        allAppsParams.setMargins(4, 0, 4, 0);
        allAppsBtn.setLayoutParams(allAppsParams);

        allAppsBtn.setBackgroundColor(currentCategory.equals("All Apps") ?
                Color.parseColor("#6B8EFF") : Color.parseColor("#2D2D2D"));

        // VR polish: hover/press effects
        applyButtonInteractionEffects(allAppsBtn);

        // Meta Apps button - before All Apps
        Button metaAppsBtn = new Button(this);
        metaAppsBtn.setText(getString(R.string.main_category_meta_button));
        metaAppsBtn.setTag("\u2699 Meta"); // logical key = legacy button text (keeps updateCategoryButtonStates behavior)
        metaAppsBtn.setTextColor(Color.WHITE);
        metaAppsBtn.setPadding(8, 4, 8, 4);
        metaAppsBtn.setAllCaps(false);
        metaAppsBtn.setTextSize(11);
        metaAppsBtn.setBackgroundColor(currentCategory.equals("Meta Apps") ?
                Color.parseColor("#6B8EFF") : Color.parseColor("#2D2D2D"));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                suggestedWidth, (int)(28 * density));
        metaParams.setMargins(4, 0, 4, 0);
        metaAppsBtn.setLayoutParams(metaParams);
        metaAppsBtn.setOnClickListener(v -> switchToCategoryAnimated("Meta Apps"));
        applyButtonInteractionEffects(metaAppsBtn);
        categoryBar.addView(metaAppsBtn);

        categoryBar.addView(allAppsBtn);

        for (String category : categories.keySet()) {
            addCategoryButton(category, () -> switchToCategoryAnimated(category));
        }

        buildSideCategoryList();

        // Schedule the initial entry animation (only runs once per session).
        // For subsequent buildCategoryBar calls (e.g. after a category is
        // added in Settings), animate only the NEW buttons.
        if (!categoryBarEntryAnimated) {
            categoryBar.postDelayed(this::animateCategoryBarEntry, 300);
        } else {
            // Animate any category buttons we haven't seen before
            for (int i = 0; i < categoryBar.getChildCount(); i++) {
                View btn = categoryBar.getChildAt(i);
                if (btn instanceof Button) {
                    String label = categoryButtonKey((Button) btn);
                    if (!animatedCategoryNames.contains(label)) {
                        animatedCategoryNames.add(label);
                        animateCategoryButtonEntry(btn, i);
                    }
                }
            }
        }
    }

    private void addCategoryButton(String name, Runnable action) {
        if (categoryBar == null) return;

        int categoryCount = categories.size() + 1;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = screenWidth - 48;
        int maxButtonWidth = 120;
        int minButtonWidth = 80;

        float density = getResources().getDisplayMetrics().density;
        int suggestedWidth = Math.min(
                Math.max(availableWidth / Math.max(categoryCount, 3),
                        (int) (minButtonWidth * density)),
                (int) (maxButtonWidth * density)
        );

        Button btn = new Button(this);
        btn.setText(displayCategoryName(name));
        btn.setTag(name); // logical key (display text may be translated)
        btn.setOnClickListener(v -> {
            runOnUiThread(() -> {
                try {
                    if (isEditMode && !selectedApps.isEmpty()) {
                        int movedCount = moveAppsToCategorySync(name, selectedApps);
                        selectedApps.clear();
                        currentCategory = name;
                        saveCurrentCategory();
                        updateCategoryButtonStates(name);

                        if (searchEditText != null) {
                            searchEditText.setText("");
                        }

                        loadCategories();
                        filterApps("");

                        if (appAdapter != null) {
                            appAdapter.notifyDataSetChanged();
                        }

                        Toast.makeText(this, getString(R.string.main_toast_moved_to_category, movedCount, displayCategoryName(name)), Toast.LENGTH_SHORT).show();
                    } else {
                        Log.i("MainActivity", "Category button clicked: " + name);
                        // Delegate everything to the action runnable
                        // (which calls switchToCategoryAnimated)
                        updateSearchStatus("", false);
                        action.run();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.main_toast_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            });
        });

        try {
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.category_button_background);
            if (drawable != null) {
                btn.setBackground(drawable);
            } else {
                btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
            }
        } catch (Exception e) {
            btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
        }

        btn.setTextColor(Color.WHITE);
        btn.setPadding(8, 4, 8, 4);
        btn.setAllCaps(false);
        btn.setTextSize(11);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                suggestedWidth,
                (int) (28 * density)
        );
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);

        if (name.equals(currentCategory)) {
            btn.setBackgroundColor(Color.parseColor("#6B8EFF"));
        } else {
            btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
        }

        // VR polish: hover/press effects on every category button
        applyButtonInteractionEffects(btn);

        categoryBar.addView(btn);
    }

    // ------------------------------------------------------------------
    // Barra lateral do novo layout. Cada icone dispara a mesma acao que ja existia.
    // ------------------------------------------------------------------
    private void setupSideNav() {
        try {
            // Icone de apps: abre/fecha a lista de categorias logo abaixo dele
            View navAll = findViewById(R.id.navAll);
            if (navAll != null) navAll.setOnClickListener(v -> toggleSideCategoryList());

            // Botao embaixo da bateria: escolhe a ordem dos apps
            View dropdown = findViewById(R.id.btnCategoryDropdown);
            if (dropdown != null) dropdown.setOnClickListener(v -> showSortMenu(v));

            // Reloginho: abre a tela de Tempo de jogo
            View navPlaytime = findViewById(R.id.navPlaytime);
            if (navPlaytime != null) navPlaytime.setOnClickListener(v -> {
                startActivity(new Intent(this, PlaytimeStatsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });

            refreshCategoryDropdownLabel();
        } catch (Exception e) {
            Log.e("MainActivity", "setupSideNav failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Lista de categorias na barra lateral
    // ------------------------------------------------------------------
    private boolean sideCategoriesExpanded = false;

    private void toggleSideCategoryList() {
        final View list = findViewById(R.id.sideCategoryList);
        if (list == null) return;
        sideCategoriesExpanded = !sideCategoriesExpanded;
        list.animate().cancel();
        list.setPivotY(0f);
        if (sideCategoriesExpanded) {
            buildSideCategoryList();
            list.setVisibility(View.VISIBLE);
            list.setAlpha(0f);
            list.setScaleY(0.6f);
            list.animate().alpha(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            list.animate().alpha(0f).scaleY(0.6f).setDuration(140)
                    .withEndAction(() -> list.setVisibility(View.GONE)).start();
        }
    }

    /** Monta os itens da lista: "Todos" + as categorias que o usuario criou, em ordem alfabetica. */
    private void buildSideCategoryList() {
        android.widget.LinearLayout list = findViewById(R.id.sideCategoryList);
        if (list == null) return;
        list.removeAllViews();

        list.addView(makeSideCategoryItem("All Apps", getString(R.string.main_side_all), R.drawable.ic_apps));

        java.util.List<String> names = new java.util.ArrayList<>(categories.keySet());
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            list.addView(makeSideCategoryItem(name, displayCategoryName(name), iconForCategory(name)));
        }
        refreshSideCategorySelection();
    }

    private View makeSideCategoryItem(String key, String label, int iconRes) {
        float d = getResources().getDisplayMetrics().density;

        android.widget.LinearLayout item = new android.widget.LinearLayout(this);
        item.setOrientation(android.widget.LinearLayout.VERTICAL);
        item.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        item.setPadding((int) (2 * d), (int) (7 * d), (int) (2 * d), (int) (6 * d));
        item.setTag(key);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        item.addView(icon, new android.widget.LinearLayout.LayoutParams((int) (22 * d), (int) (22 * d)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.parseColor("#E0E0E0"));
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = (int) (3 * d);
        item.addView(text, tp);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                (int) (60 * d), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * d);
        item.setLayoutParams(lp);

        // Mesmo comportamento do botao da barra de categorias (inclusive mover apps no modo de edicao)
        item.setOnClickListener(v -> clickCategoryBarButton(key));

        // Hover igual ao dos apps: cartao claro, sem zoom, some na hora
        item.setOnHoverListener((v, event) -> {
            int action = event.getAction();
            if (action == android.view.MotionEvent.ACTION_HOVER_ENTER) {
                styleSideCategoryItem(v, true);
            } else if (action == android.view.MotionEvent.ACTION_HOVER_EXIT) {
                styleSideCategoryItem(v, false);
            }
            return false;
        });
        item.setOnFocusChangeListener((v, hasFocus) -> styleSideCategoryItem(v, hasFocus));

        // Soltar um app arrastado aqui move o app para esta pasta
        item.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof String;
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    // Pasta acende e o app arrastado diminui para mostrar onde vai cair
                    markAppDragMoved();
                    styleSideCategoryItem(v, true, true);
                    animateDragShadow(DRAG_SHADOW_SMALL);
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    styleSideCategoryItem(v, false);
                    animateDragShadow(1f);
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    styleSideCategoryItem(v, false);
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    styleSideCategoryItem(v, false);
                    dropAppOnCategory((String) event.getLocalState(), key);
                    return true;
                default:
                    return true;
            }
        });

        styleSideCategoryItem(item, false);
        return item;
    }

    // A lista foi aberta so por causa do arrasto? Entao fecha de novo quando o arrasto acabar.
    private boolean sideListOpenedForDrag = false;

    // Arrasto de app em andamento (igual ao Android: o app sai do lugar e os outros abrem espaco)
    private String draggingPackage = null;
    // Apps que vao juntos para a pasta (todos os marcados, se o arrastado estiver entre eles)
    private final Set<String> dragGroup = new HashSet<>();
    private boolean dragMoved = false;
    private boolean reorderedDuringDrag = false;
    private float dragStartX = Float.NaN, dragStartY = Float.NaN;
    private AppDragShadow currentDragShadow;
    private android.animation.ValueAnimator dragShadowAnimator;
    private static final float DRAG_SHADOW_SMALL = 0.45f;

    /** Imagem do app que segue o ponteiro; pode ser diminuida enquanto esta em cima de uma pasta. */
    private static class AppDragShadow extends View.DragShadowBuilder {
        private final android.graphics.Bitmap bitmap;
        float scale = 1f;

        AppDragShadow(View v) {
            super(v);
            bitmap = android.graphics.Bitmap.createBitmap(Math.max(1, v.getWidth()), Math.max(1, v.getHeight()),
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            v.draw(canvas);
            roundIconCorners(v, canvas);
        }

        /** O desenho em bitmap ignora o recorte arredondado da miniatura; recorta os cantos aqui. */
        private static void roundIconCorners(View card, android.graphics.Canvas canvas) {
            View icon = card.findViewById(R.id.appIcon);
            if (icon == null) return;
            float left = 0f, top = 0f;
            for (View cur = icon; cur != null && cur != card; ) {
                left += cur.getLeft();
                top += cur.getTop();
                android.view.ViewParent parent = cur.getParent();
                cur = parent instanceof View ? (View) parent : null;
            }
            android.graphics.RectF r = new android.graphics.RectF(left, top, left + icon.getWidth(), top + icon.getHeight());
            float radius = 14 * card.getResources().getDisplayMetrics().density;
            android.graphics.Path corners = new android.graphics.Path();
            corners.addRect(r, android.graphics.Path.Direction.CW);
            android.graphics.Path rounded = new android.graphics.Path();
            rounded.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
            corners.op(rounded, android.graphics.Path.Op.DIFFERENCE);
            android.graphics.Paint clear = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            clear.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            canvas.drawPath(corners, clear);
        }

        @Override
        public void onProvideShadowMetrics(android.graphics.Point size, android.graphics.Point touch) {
            size.set(bitmap.getWidth(), bitmap.getHeight());
            touch.set(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        }

        @Override
        public void onDrawShadow(android.graphics.Canvas canvas) {
            canvas.save();
            canvas.scale(scale, scale, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
            canvas.drawBitmap(bitmap, 0f, 0f, null);
            canvas.restore();
        }
    }

    /** Comeca a arrastar o app (depois do toque longo), abrindo a lista de pastas se estiver fechada. */
    private void startAppDrag(View v, AppInfo app) {
        try {
            if (!sideCategoriesExpanded) {
                toggleSideCategoryList();
                sideListOpenedForDrag = true;
            }
            View scroll = findViewById(R.id.sideCategoryScroll);
            if (scroll != null) {
                scroll.setOnDragListener((sv, event) -> {
                    if (event.getAction() == android.view.DragEvent.ACTION_DRAG_ENDED) {
                        if (sideListOpenedForDrag) {
                            sideListOpenedForDrag = false;
                            if (sideCategoriesExpanded) toggleSideCategoryList();
                        }
                        finishAppDrag();
                    }
                    return true;
                });
            }
            setupGridDragListener();

            currentDragShadow = new AppDragShadow(v);
            draggingPackage = app.packageName;
            dragGroup.clear();
            if (selectedApps.contains(app.packageName)) dragGroup.addAll(selectedApps);
            dragGroup.add(app.packageName);
            dragMoved = false;
            reorderedDuringDrag = false;
            dragStartX = Float.NaN;
            dragStartY = Float.NaN;

            // Animacao dos outros apps abrindo espaco (so durante o arrasto)
            androidx.recyclerview.widget.DefaultItemAnimator animator = new androidx.recyclerview.widget.DefaultItemAnimator();
            animator.setMoveDuration(160);
            animator.setSupportsChangeAnimations(false);
            appsGrid.setItemAnimator(animator);

            android.content.ClipData data = android.content.ClipData.newPlainText("orbita_app", app.packageName);
            boolean started = v.startDragAndDrop(data, currentDragShadow, app.packageName, 0);
            if (!started) {
                finishAppDrag();
                return;
            }
            // O app sai do lugar: fica so a imagem flutuando
            int pos = indexOfPackage(filteredList, app.packageName);
            if (pos >= 0 && appAdapter != null) appAdapter.notifyItemChanged(pos);
        } catch (Exception e) {
            Log.w("MainActivity", "startAppDrag failed", e);
            finishAppDrag();
        }
    }

    private boolean gridDragListenerSet = false;

    /** A grade recebe o arrasto para reordenar os apps. */
    private void setupGridDragListener() {
        if (gridDragListenerSet || appsGrid == null) return;
        gridDragListenerSet = true;
        appsGrid.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof String;
                case android.view.DragEvent.ACTION_DRAG_LOCATION:
                    onGridDragLocation(event.getX(), event.getY());
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    markAppDragMoved();
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    finishAppDrag();
                    return true;
                default:
                    return true;
            }
        });
    }

    /** Enquanto arrasta por cima da grade, os outros apps vao abrindo espaco. */
    private void onGridDragLocation(float x, float y) {
        float d = getResources().getDisplayMetrics().density;
        if (Float.isNaN(dragStartX)) {
            dragStartX = x;
            dragStartY = y;
        } else if (!dragMoved && Math.hypot(x - dragStartX, y - dragStartY) > 24 * d) {
            markAppDragMoved();
        }
        if (!dragMoved || !canReorderHere()) return;

        // Rola a grade quando chega perto da borda de cima ou de baixo
        float edge = 48 * d;
        if (y < edge) appsGrid.scrollBy(0, (int) (-12 * d));
        else if (y > appsGrid.getHeight() - edge) appsGrid.scrollBy(0, (int) (12 * d));

        RecyclerView.ItemAnimator anim = appsGrid.getItemAnimator();
        if (anim != null && anim.isRunning()) return;

        View child = appsGrid.findChildViewUnder(x, y);
        if (child == null) return;
        int to = appsGrid.getChildAdapterPosition(child);
        int from = indexOfPackage(filteredList, draggingPackage);
        if (to == RecyclerView.NO_POSITION || from < 0 || to == from) return;

        // Guarda a posicao da rolagem: mover o primeiro item faz a grade pular
        GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
        int firstPos = glm != null ? glm.findFirstVisibleItemPosition() : RecyclerView.NO_POSITION;
        View firstView = firstPos != RecyclerView.NO_POSITION && glm != null ? glm.findViewByPosition(firstPos) : null;
        int firstTop = firstView != null ? firstView.getTop() - appsGrid.getPaddingTop() : 0;

        AppInfo moving = filteredList.remove(from);
        filteredList.add(to, moving);
        appAdapter.notifyItemMoved(from, to);
        reorderedDuringDrag = true;

        if (glm != null && firstPos != RecyclerView.NO_POSITION) {
            glm.scrollToPositionWithOffset(firstPos, firstTop);
        }
    }

    /** So da para reordenar sem busca ativa e fora da lista fixa da Meta. */
    private boolean canReorderHere() {
        boolean searching = searchEditText != null && searchEditText.getText().length() > 0;
        return !searching && !"Meta Apps".equals(currentCategory);
    }

    /** O app comecou a ser arrastado de verdade: as opcoes da direita somem. */
    private void markAppDragMoved() {
        if (draggingPackage == null || dragMoved) return;
        dragMoved = true;
        if (!selectedApps.isEmpty()) clearSelectionQuietly();
    }

    /** Fim do arrasto: o app volta a aparecer e a ordem nova fica salva. */
    private void finishAppDrag() {
        if (dragShadowAnimator != null) dragShadowAnimator.cancel();
        dragShadowAnimator = null;
        currentDragShadow = null;
        if (draggingPackage == null) return;
        draggingPackage = null;
        dragGroup.clear();
        if (reorderedDuringDrag) switchToCustomOrder();
        reorderedDuringDrag = false;
        dragMoved = false;
        if (appsGrid != null) appsGrid.setItemAnimator(null);
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
    }

    /** Aumenta ou diminui suavemente a imagem do app que esta sendo arrastado. */
    private void animateDragShadow(float target) {
        if (currentDragShadow == null || appsGrid == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (dragShadowAnimator != null) dragShadowAnimator.cancel();
        final AppDragShadow shadow = currentDragShadow;
        dragShadowAnimator = android.animation.ValueAnimator.ofFloat(shadow.scale, target);
        dragShadowAnimator.setDuration(160);
        dragShadowAnimator.addUpdateListener(a -> {
            if (currentDragShadow != shadow) return;
            shadow.scale = (float) a.getAnimatedValue();
            try {
                appsGrid.updateDragShadow(shadow);
            } catch (Exception e) {
                Log.w("MainActivity", "updateDragShadow failed", e);
            }
        });
        dragShadowAnimator.start();
    }

    private static int indexOfPackage(List<AppInfo> list, String packageName) {
        if (packageName == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (packageName.equals(list.get(i).packageName)) return i;
        }
        return -1;
    }

    /** Move o app solto para a pasta (ou tira de todas as pastas, se for "Todos"). */
    private void dropAppOnCategory(String packageName, String key) {
        if (packageName == null) return;
        Set<String> one = new HashSet<>();
        if (dragGroup.contains(packageName)) one.addAll(dragGroup);
        one.add(packageName);
        if ("All Apps".equals(key)) {
            int removed = removeAppsFromCategoriesSync(one);
            Toast.makeText(this, getString(R.string.main_toast_removed_from_categories, removed), Toast.LENGTH_SHORT).show();
        } else {
            int moved = moveAppsToCategorySync(key, one);
            Toast.makeText(this, getString(R.string.main_toast_moved_to_category, moved, displayCategoryName(key)), Toast.LENGTH_SHORT).show();
        }
        selectedApps.removeAll(one);
        updateBulkActionBarVisibility();
        filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
    }

    /** Fundo do item: azul quando e a categoria aberta, cinza claro no hover, transparente no resto. */
    private void styleSideCategoryItem(View item, boolean hovered) {
        styleSideCategoryItem(item, hovered, false);
    }

    /** dropTarget: um app esta sendo arrastado em cima desta pasta (ganha um contorno claro). */
    private void styleSideCategoryItem(View item, boolean hovered, boolean dropTarget) {
        float d = getResources().getDisplayMetrics().density;
        boolean selected = item.getTag() != null && item.getTag().equals(currentCategory);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(12 * d);
        if (selected) {
            bg.setColor(Color.parseColor("#6B8EFF"));
        } else if (hovered) {
            bg.setColor(Color.parseColor("#4D4E53"));
        } else {
            bg.setColor(Color.TRANSPARENT);
        }
        if (dropTarget) bg.setStroke((int) (2 * d), Color.parseColor("#B8C8FF"));
        item.setBackground(bg);
        item.setElevation(hovered && !selected ? 6 * d : 0f);
    }

    private void refreshSideCategorySelection() {
        android.view.ViewGroup list = findViewById(R.id.sideCategoryList);
        if (list == null) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            styleSideCategoryItem(list.getChildAt(i), false);
        }
    }

    /** Dispara o clique do botao correspondente na barra de categorias. */
    private void clickCategoryBarButton(String key) {
        if (categoryBar != null) {
            for (int i = 0; i < categoryBar.getChildCount(); i++) {
                View child = categoryBar.getChildAt(i);
                if (child instanceof Button && key.equals(categoryButtonKey((Button) child))) {
                    child.performClick();
                    return;
                }
            }
        }
        if (searchEditText != null) searchEditText.setText("");
        switchToCategoryAnimated(key);
    }

    /**
     * Escolhe o icone pelo nome da categoria (portugues ou ingles, sem ligar para acento).
     * Nome desconhecido fica com a pasta simples. Vale tambem para categorias criadas depois.
     */
    private int iconForCategory(String name) {
        String n = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        if (containsAny(n, "jogo", "game", "gaming")) return R.drawable.ic_cat_games;
        if (containsAny(n, "video", "filme", "movie", "cinema", "media", "midia", "tv", "stream", "serie")) return R.drawable.ic_cat_video;
        if (containsAny(n, "music", "musica", "audio", "radio")) return R.drawable.ic_cat_music;
        if (containsAny(n, "ferramenta", "tool", "utilit", "util")) return R.drawable.ic_cat_tools;
        if (containsAny(n, "fitness", "exercicio", "treino", "esporte", "sport", "academia", "workout")) return R.drawable.ic_cat_fitness;
        if (containsAny(n, "social", "amigo", "friend", "chat")) return R.drawable.ic_cat_social;
        return R.drawable.ic_cat_folder;
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    // Ordem dos apps (vale para todas as pastas)
    private static final String KEY_SORT_MODE = "sort_mode";
    private static final String KEY_CUSTOM_BASE = "sort_custom_base";
    private static final String SORT_CUSTOM = "custom";
    private static final String SORT_NAME = "name";
    private static final String SORT_RECENT = "recent";
    private static final String SORT_PLAYED = "played";
    private static final String[] SORT_MODES = {SORT_CUSTOM, SORT_NAME, SORT_RECENT, SORT_PLAYED};

    private String getSortMode() {
        return prefs.getString(KEY_SORT_MODE, SORT_RECENT);
    }

    private String sortModeLabel(String mode) {
        switch (mode) {
            case SORT_CUSTOM: return getString(R.string.main_sort_custom);
            case SORT_NAME: return getString(R.string.main_sort_name);
            case SORT_PLAYED: return getString(R.string.main_sort_played);
            default: return getString(R.string.main_sort_recent);
        }
    }

    /** Menu "Ordenar por", simples como o do Windows. */
    private void showSortMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        String current = getSortMode();
        for (int i = 0; i < SORT_MODES.length; i++) {
            menu.getMenu().add(0, i, i, sortModeLabel(SORT_MODES[i]))
                    .setCheckable(true)
                    .setChecked(SORT_MODES[i].equals(current));
        }
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(item -> {
            String mode = SORT_MODES[item.getItemId()];
            if (!mode.equals(getSortMode())) {
                prefs.edit().putString(KEY_SORT_MODE, mode).apply();
                refreshCategoryDropdownLabel();
                animateGridTransition(() -> filterApps(searchEditText != null ? searchEditText.getText().toString() : ""));
            }
            return true;
        });
        menu.show();
    }

    /** Coloca a lista na ordem escolhida. */
    private void sortForCurrentMode(List<AppInfo> list, String category) {
        String mode = getSortMode();
        if (SORT_CUSTOM.equals(mode)) {
            // Personalizada: parte da ordem que estava antes e aplica o que foi arrastado
            sortByMode(list, prefs.getString(KEY_CUSTOM_BASE, SORT_RECENT));
            applySavedOrder(list, category);
        } else {
            sortByMode(list, mode);
        }
    }

    private void sortByMode(List<AppInfo> list, String mode) {
        if (SORT_NAME.equals(mode)) {
            final CustomLabelManager labels = CustomLabelManager.getInstance(this);
            list.sort((a, b) -> labels.getDisplayLabel(a.packageName, a.label != null ? a.label : a.packageName)
                    .compareToIgnoreCase(labels.getDisplayLabel(b.packageName, b.label != null ? b.label : b.packageName)));
        } else if (SORT_PLAYED.equals(mode)) {
            Map<String, Long> played = new HashMap<>();
            try {
                if (playtimeTracker != null) played = playtimeTracker.getAllTimePlaytime();
            } catch (Exception e) {
                Log.w("MainActivity", "playtime for sorting failed", e);
            }
            final Map<String, Long> p = played != null ? played : new HashMap<>();
            list.sort((a, b) -> Long.compare(
                    p.containsKey(b.packageName) ? p.get(b.packageName) : 0L,
                    p.containsKey(a.packageName) ? p.get(a.packageName) : 0L));
        } else if (SORT_RECENT.equals(mode)) {
            list.sort((a, b) -> Long.compare(b.installTime, a.installTime));
        }
        // SORT_CUSTOM sozinho nao reordena
    }

    /**
     * O usuario arrastou um app: a ordem vira Personalizada a partir do que esta na tela.
     * As outras pastas continuam como estavam (mesma ordem de antes, sem arrasto salvo).
     */
    private void switchToCustomOrder() {
        SharedPreferences.Editor editor = prefs.edit();
        String mode = getSortMode();
        if (!SORT_CUSTOM.equals(mode)) {
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith(KEY_ORDER_PREFIX)) editor.remove(key);
            }
            editor.putString(KEY_CUSTOM_BASE, mode);
            editor.putString(KEY_SORT_MODE, SORT_CUSTOM);
        }
        editor.apply();
        saveCurrentOrder();
        refreshCategoryDropdownLabel();
    }

    private void refreshCategoryDropdownLabel() {
        TextView label = findViewById(R.id.txtCategoryDropdown);
        TextView title = findViewById(R.id.txtHeaderTitle);
        String name = currentCategory != null ? currentCategory : "All Apps";
        if (label != null) label.setText(sortModeLabel(getSortMode()));
        if (title != null) title.setText(name.equals("All Apps") ? getString(R.string.main_header_all) : displayCategoryName(name));
    }

    /**
     * Maps a logical category key (stored in prefs / compared in logic) to the text
     * shown on screen. User-created categories are shown exactly as typed.
     */
    private String displayCategoryName(String key) {
        if (key == null) return "";
        switch (key) {
            case "All Apps":
                return getString(R.string.main_category_all_apps);
            case "Meta Apps":
                return getString(R.string.main_category_meta_apps);
            case "Uncategorized":
                return getString(R.string.main_category_uncategorized);
            default:
                return key;
        }
    }

    /** Logical key of a category-bar button: its tag when set, otherwise its text. */
    private String categoryButtonKey(Button b) {
        Object tag = b.getTag();
        return tag instanceof String ? (String) tag : b.getText().toString();
    }

    private void updateCategoryButtonStates(String selectedCategory) {
        refreshCategoryDropdownLabel();
        refreshSideCategorySelection();
        if (categoryBar == null) return;

        for (int i = 0; i < categoryBar.getChildCount(); i++) {
            View child = categoryBar.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                if (categoryButtonKey(btn).equals(selectedCategory)) {
                    btn.setBackgroundColor(Color.parseColor("#6B8EFF"));
                } else {
                    try {
                        android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.category_button_background);
                        if (drawable != null) {
                            btn.setBackground(drawable);
                        } else {
                            btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
                        }
                    } catch (Exception e) {
                        btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
                    }
                }
            }
        }
    }

    private void updateSearchStatus(String statusText, boolean isSearching) {
        runOnUiThread(() -> {
            TextView categoryTitle = findViewById(R.id.categoryTitle);
            TextView searchStatus = findViewById(R.id.searchStatus);

            if (categoryTitle != null) {
                categoryTitle.setVisibility(View.GONE);
            }

            if (searchStatus != null) {
                if (isSearching && !statusText.isEmpty()) {
                    searchStatus.setText(statusText);
                    searchStatus.setVisibility(View.VISIBLE);
                    searchStatus.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    searchStatus.setVisibility(View.GONE);
                }
            }
        });
    }

    private void toggleAppSelection(AppInfo app) {
        runOnUiThread(() -> {
            try {
                if (selectedApps.contains(app.packageName)) {
                    selectedApps.remove(app.packageName);
                } else {
                    selectedApps.add(app.packageName);
                }

                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }

                updateBulkActionBarVisibility();

            } catch (Exception e) {
                // Ignore selection errors
            }
        });
    }

    private void showMultiAssignCategoryDialog() {
        if (selectedApps.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_no_apps_selected), Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> categoryNames = new ArrayList<>(categories.keySet());
        if (categoryNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_no_categories), Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.main_move_dialog_title, selectedApps.size()));

        String[] categoriesArray = categoryNames.toArray(new String[0]);
        builder.setItems(categoriesArray, (dialog, which) -> {
            String selectedCategory = categoriesArray[which];
            moveMultipleAppsToCategory(selectedCategory, new HashSet<>(selectedApps));
        });

        builder.setNegativeButton(getString(R.string.main_btn_cancel), null);
        ThemedDialog.showThemed(builder.create());
    }

    private void moveMultipleAppsToCategory(String targetCategory, Set<String> appsToMove) {
        runOnUiThread(() -> {
            try {
                int movedCount = 0;
                int alreadyInCategory = 0;
                int notFoundCount = 0;

                for (String packageName : appsToMove) {
                    AppInfo foundApp = null;
                    for (AppInfo app : appList) {
                        if (app.packageName.equals(packageName)) {
                            foundApp = app;
                            break;
                        }
                    }

                    if (foundApp == null) {
                        notFoundCount++;
                        continue;
                    }

                    Set<String> currentSet = categories.get(targetCategory);
                    if (currentSet != null && currentSet.contains(foundApp.packageName)) {
                        alreadyInCategory++;
                        continue;
                    }

                    for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                        if (entry.getValue().contains(foundApp.packageName)) {
                            Set<String> oldSet = new HashSet<>(entry.getValue());
                            oldSet.remove(foundApp.packageName);
                            categoryPrefs.edit().putStringSet("cat_" + entry.getKey(), oldSet).apply();
                            break;
                        }
                    }

                    Set<String> newSet = new HashSet<>(categories.get(targetCategory));
                    newSet.add(foundApp.packageName);
                    categoryPrefs.edit().putStringSet("cat_" + targetCategory, newSet).apply();
                    // Update in-memory map so next iteration sees the updated set
                    categories.get(targetCategory).add(foundApp.packageName);

                    prefs.edit().putString("cat_" + foundApp.packageName, targetCategory).apply();
                    foundApp.category = targetCategory;
                    movedCount++;
                }

                loadCategories();

                // Clear the selection now that the move is done. Without this,
                // the bulk action bar keeps stale selection state and the
                // moved app tiles stay visually selected in the grid.
                selectedApps.clear();
                updateBulkActionBarVisibility();

                // Rebuild the displayed list against the freshly-updated
                // categories map. When viewing "All Apps", filterApps("")
                // excludes anything that's now in a category — which is
                // exactly what needs to happen so the moved tiles vanish.
                // Without this, the RecyclerView keeps showing the moved
                // apps in All Apps until the user manually switches categories.
                filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }

                String message = getString(R.string.main_toast_moved_to_category, movedCount, displayCategoryName(targetCategory));
                if (alreadyInCategory > 0) {
                    message += "\n" + getString(R.string.main_already_in_category, alreadyInCategory);
                }
                if (notFoundCount > 0) {
                    message += "\n" + getString(R.string.main_apps_not_found, notFoundCount);
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.main_toast_error_moving, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeSelectedAppsFromCategories(Set<String> appsToRemove) {
        runOnUiThread(() -> {
            try {
                int removedCount = 0;

                for (String packageName : appsToRemove) {
                    AppInfo foundApp = null;
                    for (AppInfo app : appList) {
                        if (app.packageName.equals(packageName)) {
                            foundApp = app;
                            break;
                        }
                    }

                    if (foundApp == null) continue;

                    for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                        if (entry.getValue().contains(foundApp.packageName)) {
                            Set<String> categorySet = new HashSet<>(entry.getValue());
                            categorySet.remove(foundApp.packageName);
                            categoryPrefs.edit().putStringSet("cat_" + entry.getKey(), categorySet).apply();

                            prefs.edit().remove("cat_" + foundApp.packageName).apply();
                            foundApp.category = "Uncategorized";
                            removedCount++;
                            break;
                        }
                    }
                }

                loadCategories();

                Toast.makeText(this, getString(R.string.main_toast_removed_from_categories, removedCount), Toast.LENGTH_SHORT).show();

                filterApps(searchEditText != null ? searchEditText.getText().toString() : "");

            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.main_toast_error_removing, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Tira a selecao sem aviso (opcoes somem ao arrastar ou clicar fora). */
    private void clearSelectionQuietly() {
        selectedApps.clear();
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
        updateBulkActionBarVisibility();
    }

    /**
     * Clicar fora da coluna de opcoes fecha as opcoes. Clicar em outro app
     * continua marcando/desmarcando ele (para escolher varios de uma vez).
     */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && !selectedApps.isEmpty()
                && bulkActionBar != null && bulkActionBar.getVisibility() == View.VISIBLE
                && !isTouchInside(bulkActionBar, ev) && !isTouchOnAppCard(ev)) {
            clearSelectionQuietly();
        }
        return super.dispatchTouchEvent(ev);
    }

    private static boolean isTouchInside(View v, android.view.MotionEvent ev) {
        if (v == null || !v.isShown()) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = ev.getRawX(), y = ev.getRawY();
        return x >= loc[0] && x < loc[0] + v.getWidth() && y >= loc[1] && y < loc[1] + v.getHeight();
    }

    private boolean isTouchOnAppCard(android.view.MotionEvent ev) {
        if (appsGrid == null || !isTouchInside(appsGrid, ev)) return false;
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View card = appsGrid.getChildAt(i).findViewById(R.id.cardApp);
            if (isTouchInside(card, ev)) return true;
        }
        return false;
    }

    private void clearSelection() {
        selectedApps.clear();
        appAdapter.notifyDataSetChanged();
        updateBulkActionBarVisibility();
        Toast.makeText(this, getString(R.string.main_toast_selection_cleared), Toast.LENGTH_SHORT).show();
    }

    private void selectAllApps() {
        selectedApps.clear();
        for (AppInfo app : filteredList) {
            selectedApps.add(app.packageName);
        }
        appAdapter.notifyDataSetChanged();
        updateBulkActionBarVisibility();
        Toast.makeText(this, getString(R.string.main_toast_selected_all, filteredList.size()), Toast.LENGTH_SHORT).show();
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        private List<AppInfo> apps;
        private SharedPreferences prefs;

        public AppAdapter(List<AppInfo> apps) {
            this.apps = apps;
            this.prefs = MainActivity.this.getSharedPreferences("VRLPrefs", MODE_PRIVATE);
        }


        @Override
        public long getItemId(int position) {
            return apps.get(position).packageName.hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);

            ViewHolder holder = new ViewHolder(view);
            // Apply hover/focus/press animations to the card for VR polish
            applyCardInteractionEffects(holder.cardView);
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AppInfo app = apps.get(position);

            // CRITICAL: reset the card's animation state on every bind.
            // Recycled cards can carry stale translation/alpha/scale/rotation
            // from a previously-running entry/exit animation that got
            // interrupted by recycling. Without this reset, cards can appear
            // stuck off-position, covering other cards, or invisible.
            holder.cardView.animate().cancel();
            holder.cardView.setTranslationX(0f);
            holder.cardView.setTranslationY(0f);
            holder.cardView.setTranslationZ(0f);
            holder.cardView.setAlpha(1f);
            holder.cardView.setScaleX(1f);
            holder.cardView.setScaleY(1f);
            holder.cardView.setRotation(0f);
            // O app que esta sendo arrastado sai do lugar (fica so a imagem flutuando)
            holder.itemView.setAlpha(app.packageName.equals(draggingPackage) ? 0f : 1f);

            // Apply current theme to this card (handles recycled views)
            com.neto.orbitalauncher.theme.Theme theme =
                    com.neto.orbitalauncher.theme.ThemeManager.getInstance(MainActivity.this).getCurrentTheme();
            holder.cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
            holder.cardView.setCardElevation(0f);
            // Sem brilho/retangulo claro de hover, foco ou clique no item
            holder.cardView.setRippleColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            holder.cardView.setForeground(null);
            holder.cardView.setStateListAnimator(null);
            holder.appName.setTextColor(theme.textPrimary);

            // Apply custom label if user renamed this app
            CustomLabelManager labelManager = CustomLabelManager.getInstance(MainActivity.this);
            String displayLabel = labelManager.getDisplayLabel(app.packageName,
                    app.label != null ? app.label : app.packageName);
            holder.appName.setText(displayLabel);
            holder.appVersion.setVisibility(View.GONE); // Hide the version badge completely

            // Bolinha de selecao: aparece em todos os apps quando ha 2 ou mais marcados
            if (selectedApps.size() >= 2) {
                holder.selectCircle.setVisibility(View.VISIBLE);
                holder.selectCircle.setImageResource(selectedApps.contains(app.packageName)
                        ? R.drawable.ic_select_on : R.drawable.ic_select_off);
            } else {
                holder.selectCircle.setVisibility(View.GONE);
            }

            loadAppIcon(holder, app);

            // Selected card visual treatment - works in edit mode OR when
            // we have an active multi-selection from a long-press "Select"
            boolean isSelected = selectedApps.contains(app.packageName);
            if (isSelected) {
                holder.cardView.setStrokeWidth(5);
                holder.cardView.setStrokeColor(theme.accentPrimary);
            } else {
                holder.cardView.setStrokeWidth(0);
            }

            if (isEditMode) {
                holder.cardView.setOnClickListener(v -> toggleAppSelection(app));
                holder.cardView.setOnLongClickListener(v -> {
                    showEditOptions(app);
                    return true;
                });
            } else {
                // Tap: launch if no selection active, otherwise toggle selection
                holder.cardView.setOnClickListener(v -> {
                    if (!selectedApps.isEmpty()) {
                        toggleAppSelection(app);
                    } else {
                        v.postDelayed(() -> launchApp(app), 240);
                    }
                });
                // Long-press: just enter selection mode for this app. No menu.
                // All single-app and bulk actions live on the floating bar
                // that appears when something is selected.
                // Segurando, da para arrastar o app ate uma pasta da barra lateral.
                holder.cardView.setOnLongClickListener(v -> {
                    // Segurar um app ja marcado nao desmarca: assim da para arrastar varios juntos
                    if (!selectedApps.contains(app.packageName)) toggleAppSelection(app);
                    startAppDrag(v, app);
                    return true;
                });
            }
        }

        /**
         * RESTORED: loadAppIcon now uses the restored icon scale system
         * Icon width = selected scale from ICON_SCALES_DP (82, 99, 125, 165, 236 dp)
         * Icon height = width * 0.5625 (always 16:9 landscape)
         */
        private void loadAppIcon(ViewHolder holder, AppInfo app) {
            float density = holder.itemView.getResources().getDisplayMetrics().density;

            // Use the restored icon scale system (old launcher logic).
            // iconScaleOverride lets the hover-fix trick temporarily render
            // icons at a smaller scale, then notify back to actual scale.
            int scaleIndex = (iconScaleOverride >= 0)
                    ? iconScaleOverride
                    : prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
            int iconSizeDp = ICON_SCALES_DP[scaleIndex];
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconWidthPx * 0.5625f);  // 16:9 LANDSCAPE — always

            ViewGroup.LayoutParams params = holder.appIcon.getLayoutParams();
            params.width = iconWidthPx;
            params.height = iconHeightPx;
            holder.appIcon.setLayoutParams(params);
            holder.appIcon.requestLayout();

            if (ENABLE_IMAGE_CACHING && iconCache.containsKey(app.packageName)) {
                holder.appIcon.setImageDrawable(iconCache.get(app.packageName));
                return;
            }

            Glide.with(MainActivity.this)
                    .load(app.githubIconUrl)
                    .apply(new RequestOptions()
                            .placeholder(app.icon)
                            .error(app.icon)
                            .centerCrop()
                            .override(iconWidthPx, iconHeightPx)
                            .skipMemoryCache(false)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate())
                    .into(holder.appIcon);
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardView;
            ImageView appIcon;
            TextView appName;
            ImageView selectCircle;
            TextView appVersion;

            public ViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.cardApp);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                selectCircle = itemView.findViewById(R.id.selectCircle);
                appVersion = itemView.findViewById(R.id.appVersion);
            }
        }
    }

    private void showVROptions(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(app.label);

        // Build detailed info string
        String appInfo = "📱 " + app.label + "\n" +
                "────────────────\n" +
                getString(R.string.main_info_version, app.versionName) + "\n" +
                getString(R.string.main_info_installed, app.firstInstallDate) + "\n" +
                getString(R.string.main_info_updated, app.lastUpdateDate) + "\n" +
                getString(R.string.main_info_type, getString(app.isStoreApp ? R.string.main_type_store
                        : (app.isSideloaded ? R.string.main_type_sideloaded : R.string.main_type_system))) + "\n" +
                "────────────────\n\n" +
                getString(R.string.main_info_playtime_header) + "\n" +
                "  " + getString(R.string.main_time_today) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeToday) + "\n" +
                "  " + getString(R.string.main_time_this_week) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeWeek) + "\n" +
                "  " + getString(R.string.main_time_this_month) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeMonth) + "\n" +
                "  " + getString(R.string.main_time_all_time) + ": " + PlaytimeTracker.formatPlaytime(app.playtimeAllTime) + "\n" +
                (app.isCurrentlyRunning ? "\n" + getString(R.string.main_currently_running) : "");

        boolean isInCategory = false;
        String currentAppCategory = null;
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            if (entry.getValue().contains(app.packageName)) {
                isInCategory = true;
                currentAppCategory = entry.getKey();
                break;
            }
        }

        final boolean finalIsInCategory = isInCategory;
        final String finalCategory = currentAppCategory;

        // Display texts and, in parallel, stable action ids (the dispatch must not
        // depend on the translated text).
        List<String> optionsList = new ArrayList<>();
        final List<String> actionIds = new ArrayList<>();
        optionsList.add(getString(R.string.main_opt_launch));
        actionIds.add("launch");
        optionsList.add(getString(R.string.main_opt_playtime_stats));
        actionIds.add("playtime");
        optionsList.add(getString(R.string.main_opt_rename));
        actionIds.add("rename");

        // Multi-select entry point. Once selection is active, the floating
        // bar will appear and tapping cards will toggle their selection.
        if (selectedApps.contains(app.packageName)) {
            optionsList.add(getString(R.string.main_opt_deselect));
        } else {
            optionsList.add(getString(R.string.main_opt_select));
        }
        actionIds.add("toggle_select");

        if (isInCategory) {
            optionsList.add(getString(R.string.main_opt_remove_from, displayCategoryName(currentAppCategory)));
            actionIds.add("remove_category");
        } else {
            optionsList.add(getString(R.string.main_opt_add_to_category));
            actionIds.add("add_category");
        }

        optionsList.add(getString(R.string.main_opt_app_info));
        actionIds.add("app_info");

        if (!app.isSystemApp) {
            optionsList.add(getString(R.string.main_opt_uninstall));
            actionIds.add("uninstall");
        }

        String[] options = optionsList.toArray(new String[0]);

        builder.setItems(options, (dialog, which) -> {
            String selectedAction = actionIds.get(which);

            if (selectedAction.equals("launch")) {
                launchApp(app);
            } else if (selectedAction.equals("playtime")) {
                showPlaytimeDetails(app);
            } else if (selectedAction.equals("rename")) {
                showRenameDialog(app);
            } else if (selectedAction.equals("toggle_select")) {
                toggleAppSelection(app);
            } else if (selectedAction.equals("remove_category")) {
                removeFromCategory(app, finalCategory);
            } else if (selectedAction.equals("add_category")) {
                showAssignCategoryDialog(app);
            } else if (selectedAction.equals("app_info")) {
                // Show detailed app info with install/update dates
                ThemedDialog.showThemed(new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.main_app_details_title))
                        .setMessage(appInfo)
                        .setPositiveButton(getString(R.string.main_btn_settings), (d, w) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(android.net.Uri.parse("package:" + app.packageName));
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(this, getString(R.string.main_toast_cannot_open_app_settings), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(getString(R.string.main_btn_close), null)
                        .create());
            } else if (selectedAction.equals("uninstall")) {
                uninstallApp(app);
            }
        });
        ThemedDialog.showThemed(builder.create());
    }

    /**
     * Show a dialog to rename an app's display label.
     * Empty input resets to original name.
     */
    private void showRenameDialog(AppInfo app) {
        CustomLabelManager labelManager = CustomLabelManager.getInstance(this);
        String currentDisplayLabel = labelManager.getDisplayLabel(app.packageName, app.label);
        boolean hasCustom = labelManager.hasCustomLabel(app.packageName);

        // Build the input view
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        android.widget.TextView instructions = new android.widget.TextView(this);
        instructions.setText(hasCustom
                ? getString(R.string.main_rename_instructions_custom, currentDisplayLabel, app.label)
                : getString(R.string.main_rename_instructions, app.label));
        instructions.setTextSize(12);
        instructions.setPadding(0, 0, 0, padding);
        container.addView(instructions);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(app.label);
        input.setText(hasCustom ? currentDisplayLabel : "");
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        container.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_rename_title))
                .setView(container)
                .setPositiveButton(getString(R.string.main_btn_save), (d, w) -> {
                    String newName = input.getText().toString().trim();
                    labelManager.setCustomLabel(app.packageName, newName);

                    String message;
                    if (newName.isEmpty()) {
                        message = getString(R.string.main_toast_reset_to_original, app.label);
                    } else {
                        message = getString(R.string.main_toast_renamed_to, newName);
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                    // Refresh the adapter to show new label
                    if (appAdapter != null) {
                        appAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(getString(R.string.main_btn_cancel), null);

        // Add Reset button if there's a custom label
        if (hasCustom) {
            builder.setNeutralButton(getString(R.string.main_btn_reset_original), (d, w) -> {
                labelManager.setCustomLabel(app.packageName, null);
                Toast.makeText(this, getString(R.string.main_toast_reset_to, app.label), Toast.LENGTH_SHORT).show();
                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }
            });
        }

        ThemedDialog.showThemed(builder.create());
    }

    private void showEditOptions(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.main_edit_title, app.label));

        boolean isInCategory = false;
        String currentAppCategory = null;
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            if (entry.getValue().contains(app.packageName)) {
                isInCategory = true;
                currentAppCategory = entry.getKey();
                break;
            }
        }

        List<String> optionsList = new ArrayList<>();
        final List<String> actionIds = new ArrayList<>();

        if (selectedApps.size() > 0) {
            optionsList.add(getString(R.string.main_opt_move_selected, selectedApps.size()));
            actionIds.add("move_selected");
            optionsList.add(getString(R.string.main_opt_uninstall_selected, selectedApps.size()));
            actionIds.add("uninstall_selected");
            optionsList.add(getString(R.string.main_opt_clear_selection, selectedApps.size()));
            actionIds.add("clear_selection");
        } else {
            optionsList.add(getString(R.string.main_opt_select_all));
            actionIds.add("select_all");
        }

        if (isInCategory) {
            optionsList.add(getString(R.string.main_opt_remove_from, displayCategoryName(currentAppCategory)));
            actionIds.add("remove_category");
        } else {
            optionsList.add(getString(R.string.main_opt_add_to_category));
            actionIds.add("add_category");
        }

        optionsList.add(getString(R.string.main_opt_rename));
        actionIds.add("rename");
        optionsList.add(getString(R.string.main_opt_hide_app));
        actionIds.add("hide");
        optionsList.add(getString(R.string.main_opt_unhide_all));
        actionIds.add("unhide_all");
        optionsList.add(getString(R.string.main_opt_app_settings));
        actionIds.add("app_settings");

        String[] options = optionsList.toArray(new String[0]);

        final String finalCategory = currentAppCategory;

        builder.setItems(options, (dialog, which) -> {
            String selectedAction = actionIds.get(which);

            if (selectedAction.equals("move_selected")) {
                showMultiAssignCategoryDialog();
            } else if (selectedAction.equals("uninstall_selected")) {
                confirmAndUninstallSelected();
            } else if (selectedAction.equals("clear_selection")) {
                clearSelection();
            } else if (selectedAction.equals("select_all")) {
                selectAllApps();
            } else if (selectedAction.equals("remove_category")) {
                removeFromCategory(app, finalCategory);
            } else if (selectedAction.equals("add_category")) {
                showAssignCategoryDialog(app);
            } else if (selectedAction.equals("rename")) {
                showRenameDialog(app);
            } else if (selectedAction.equals("hide")) {
                hideApp(app);
            } else if (selectedAction.equals("unhide_all")) {
                unhideAllApps();
            } else if (selectedAction.equals("app_settings")) {
                openAppSettings(app.packageName);
            }
        });
        ThemedDialog.showThemed(builder.create());
    }

    private void showAssignCategoryDialog(AppInfo app) {
        List<String> categoryNames = new ArrayList<>(categories.keySet());
        if (categoryNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_no_categories), Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.main_assign_title, app.label));

        String[] categoriesArray = categoryNames.toArray(new String[0]);
        builder.setItems(categoriesArray, (dialog, which) -> {
            String selectedCategory = categoriesArray[which];

            Set<String> currentSet = categories.get(selectedCategory);
            if (currentSet != null && currentSet.contains(app.packageName)) {
                Toast.makeText(this, getString(R.string.main_toast_already_in, app.label, displayCategoryName(selectedCategory)), Toast.LENGTH_SHORT).show();
                return;
            }

            String foundOldCategory = null;
            for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                if (entry.getValue().contains(app.packageName)) {
                    foundOldCategory = entry.getKey();
                    break;
                }
            }

            final String finalCategory = selectedCategory;
            final String finalOldCategory = foundOldCategory;

            if (foundOldCategory != null) {
                AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
                confirmBuilder.setTitle(getString(R.string.main_move_app_title))
                        .setMessage(getString(R.string.main_move_app_message, app.label, displayCategoryName(foundOldCategory), displayCategoryName(selectedCategory)))
                        .setPositiveButton(getString(R.string.main_btn_move), (d, w) -> {
                            moveAppToCategory(app, finalOldCategory, finalCategory);
                        })
                        .setNegativeButton(getString(R.string.main_btn_cancel), null);
                ThemedDialog.showThemed(confirmBuilder.create());
            } else {
                AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
                confirmBuilder.setTitle(getString(R.string.main_add_to_category_title))
                        .setMessage(getString(R.string.main_add_to_category_message, app.label, displayCategoryName(selectedCategory)))
                        .setPositiveButton(getString(R.string.main_btn_add), (d, w) -> {
                            addAppToCategory(app, finalCategory);
                        })
                        .setNegativeButton(getString(R.string.main_btn_cancel), null);
                ThemedDialog.showThemed(confirmBuilder.create());
            }
        });

        builder.setNegativeButton(getString(R.string.main_btn_cancel), null);
        ThemedDialog.showThemed(builder.create());
    }

    private void moveAppToCategory(AppInfo app, String oldCategory, String newCategory) {
        Set<String> oldSet = new HashSet<>(categories.get(oldCategory));
        oldSet.remove(app.packageName);
        categoryPrefs.edit().putStringSet("cat_" + oldCategory, oldSet).apply();

        Set<String> newSet = new HashSet<>(categories.get(newCategory));
        newSet.add(app.packageName);
        categoryPrefs.edit().putStringSet("cat_" + newCategory, newSet).apply();

        prefs.edit().putString("cat_" + app.packageName, newCategory).apply();
        app.category = newCategory;

        loadCategories();

        // If we're currently viewing the old category, stay in it
        // If we're viewing the new category, stay in it
        // No need to change currentCategory

        filterApps(searchEditText != null ? searchEditText.getText().toString() : "");

        Toast.makeText(this, getString(R.string.main_toast_app_moved, app.label, displayCategoryName(newCategory)), Toast.LENGTH_SHORT).show();
    }

    private void addAppToCategory(AppInfo app, String category) {
        Set<String> set = new HashSet<>(categories.get(category));
        set.add(app.packageName);
        categoryPrefs.edit().putStringSet("cat_" + category, set).apply();

        prefs.edit().putString("cat_" + app.packageName, category).apply();
        app.category = category;

        loadCategories();

        if (currentCategory.equals("All Apps")) {
            filterApps(searchEditText.getText().toString());
        } else if (currentCategory.equals(category)) {
            filterApps(searchEditText.getText().toString());
        }

        Toast.makeText(this, getString(R.string.main_toast_app_added, app.label, displayCategoryName(category)), Toast.LENGTH_SHORT).show();
    }

    private void removeFromCategory(AppInfo app, String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.main_remove_title))
                .setMessage(getString(R.string.main_remove_message, app.label, displayCategoryName(category)))
                .setPositiveButton(getString(R.string.main_btn_remove), (dialog, which) -> {
                    Set<String> set = new HashSet<>(categories.get(category));
                    set.remove(app.packageName);
                    categoryPrefs.edit().putStringSet("cat_" + category, set).apply();

                    prefs.edit().remove("cat_" + app.packageName).apply();
                    app.category = "Uncategorized";

                    // Stay in current category - don't force reset to All Apps
                    loadCategories();
                    filterApps(searchEditText != null ? searchEditText.getText().toString() : "");

                    // If we're viewing the category we just removed from, refresh the view
                    if (currentCategory.equals(category)) {
                        filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
                    }

                    Toast.makeText(this, getString(R.string.main_toast_app_removed, app.label, displayCategoryName(category)), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.main_btn_cancel), null)
                .show();
    }

    private void hideApp(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.main_hide_title))
                .setMessage(getString(R.string.main_hide_message, app.label))
                .setPositiveButton(getString(R.string.main_btn_hide), (dialog, which) -> {
                    prefs.edit().putBoolean("hidden_" + app.packageName, true).apply();
                    appList.remove(app);
                    filterApps(searchEditText.getText().toString());
                    Toast.makeText(this, getString(R.string.main_toast_app_hidden, app.label), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.main_btn_cancel), null)
                .show();
    }

    private void unhideAllApps() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.main_unhide_title))
                .setMessage(getString(R.string.main_unhide_message))
                .setPositiveButton(getString(R.string.main_btn_yes), (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (String key : prefs.getAll().keySet()) {
                        if (key.startsWith("hidden_")) {
                            editor.remove(key);
                        }
                    }
                    editor.apply();

                    loadUserApps();
                    filterApps(searchEditText.getText().toString());
                    Toast.makeText(this, getString(R.string.main_toast_all_restored), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.main_btn_cancel), null)
                .show();
    }

    private void uninstallApp(AppInfo app) {
        String packageToUninstall = app.packageName;

        appList.remove(app);
        filteredList.remove(app);
        appAdapter.notifyDataSetChanged();

        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.parse("package:" + packageToUninstall));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + packageToUninstall));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                Toast.makeText(this, getString(R.string.main_toast_open_app_info_uninstall), Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, getString(R.string.main_toast_cannot_uninstall, app.label), Toast.LENGTH_SHORT).show();
                loadUserApps();
                filterApps(searchEditText.getText().toString());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_UNINSTALL) {
            // Continue with the next pending uninstall regardless of whether
            // this one was confirmed - the queue is the user's intent and
            // skipping one (cancel) shouldn't abort the rest.
            launchNextUninstall();
        }
    }

    @Override
    public void onBackPressed() {
        if (isEditMode && !selectedApps.isEmpty()) {
            clearSelection();
            return;
        }

        if (!searchEditText.getText().toString().isEmpty()) {
            searchEditText.setText("");
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.main_exit_title))
                    .setMessage(getString(R.string.main_exit_message))
                    .setPositiveButton(getString(R.string.main_btn_exit), (dialog, which) -> finish())
                    .setNegativeButton(getString(R.string.main_btn_stay), null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Atualizacao automatica: toda vez que o launcher volta para a tela, confere o GitHub
        // (no maximo uma vez por dia, e so se estiver ligada em Configuracoes)
        scheduleAutoUpdateCheck();

        // Force compositor refresh when returning from a game.
        // Simulates the screen off/on that fixes the loading overlay
        // getting stuck on top of a running game.
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Evolve:CompositorRefresh");
            wl.acquire(200);
            wl.release();
        } catch (Exception e) {
            Log.e("MainActivity", "WakeLock refresh failed", e);
        }

        // Re-apply theme to view hierarchy in case user changed it in Settings
        View themeRootView = findViewById(android.R.id.content);
        if (themeRootView != null) {
            ThemeApplier.applyThemeToHierarchy(themeRootView);
        }

        // Update the layout background (this respects user's background settings + theme image)
        updateBackground();

        // Restore last selected category
        String savedCategory = prefs.getString(KEY_LAST_CATEGORY, "All Apps");
        if (!savedCategory.equals(currentCategory)) {
            currentCategory = savedCategory;
            if (!currentCategory.equals("All Apps") && !currentCategory.equals("Meta Apps")
                    && !categories.containsKey(currentCategory)) {
                currentCategory = "All Apps";
                saveCurrentCategory();
            }
            updateCategoryButtonStates(currentCategory);
            filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
        }

        refreshAll(); // One-time refresh

        // CRITICAL: Restart periodic status updates
        startStatusUpdates();

        // One-time updates (harmless redundancy)
        updateTime();
        updateIPAddress();
        updateWifiSignal();
        updateBatteryLevel(null);

        // Check permission but don't show dialog if already granted
        if (!prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            // Only check if we haven't already verified
            if (playtimeTracker.hasPermission()) {
                prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
                startPlaytimeUpdates();
            }
        }

        // --- DYNAMIC REGISTRATION OF SYSTEM PACKAGE BROADCASTS ---
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package"); // Important for package broadcasts

        registerReceiver(appChangeListener, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop status updates
        if (statusHandler != null && statusUpdateRunnable != null) {
            statusHandler.removeCallbacks(statusUpdateRunnable);
        }

        // Unregister the app change listener
        try {
            unregisterReceiver(appChangeListener);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, safe to ignore
        }
    }

    /**
     * Handle Quest panel resize. The manifest declares
     * configChanges="screenSize|screenLayout|smallestScreenSize|..." so
     * Android does NOT recreate the activity or trigger an automatic
     * layout pass when the panel is resized. Without this override, Quest
     * enlarges the window but the launcher content stays at its previous
     * dimensions — because no view ever gets told to remeasure.
     *
     * The fix is to manually kick off a layout pass on the root view.
     * That cascades through match_parent children, appsGrid picks up its
     * new width, and the OnLayoutChangeListener in setupRecyclerView()
     * fires to recalculate the column count.
     *
     * Prerequisite: MainActivity must have android:resizeableActivity="true"
     * in the manifest. Without it, Android gives the app a fixed-size draw
     * surface regardless of container size and no amount of requestLayout
     * calls can force it to grow.
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Kick off a layout pass on the root view so children reflow to
        // the new window dimensions.
        View root = findViewById(R.id.mainLayout);
        if (root != null) {
            root.requestLayout();
            root.invalidate();
        }

        // The OnLayoutChangeListener on appsGrid will handle column
        // recount and adapter refresh once the new width propagates.
        // If the grid doesn't naturally get a layout change (rare), poke
        // it directly as a fallback.
        if (appsGrid != null) {
            appsGrid.post(() -> {
                int newColumns = calculateOptimalColumns();
                GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
                if (glm != null && glm.getSpanCount() != newColumns) {
                    glm.setSpanCount(newColumns);
                    if (gridSpacingDecoration != null) {
                        gridSpacingDecoration.setSpanCount(newColumns);
                    }
                    if (appAdapter != null) {
                        appAdapter.notifyDataSetChanged();
                    }
                    appsGrid.requestLayout();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // "Reabrir ao fechar": fechou no X -> pede para reabrir (o servico ignora se ha jogo aberto)
        try {
            if (isFinishing() && getSharedPreferences("VRLPrefs", MODE_PRIVATE).getBoolean("reopen_on_close", false)
                    && EvolveAccessibilityService.instance != null) {
                EvolveAccessibilityService.instance.requestReopenLauncher();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "reopen request failed", e);
        }

        if (statusHandler != null && statusUpdateRunnable != null) {
            statusHandler.removeCallbacks(statusUpdateRunnable);
        }

        // Unregister category change receiver
        try {
            unregisterReceiver(categoryChangeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }

        try {
            unregisterReceiver(appChangeListener);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered, ignore
        }

        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered, ignore
        }

        instance = null;

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        if (iconCache != null) {
            iconCache.clear();
        }
    }
}