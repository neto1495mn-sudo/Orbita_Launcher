package com.neto.orbitalauncher;

import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import com.neto.orbitalauncher.theme.ThemedDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tempo de jogo: barra lateral com os periodos (Hoje / Semana / Mes / Total / Voltar) e duas vistas:
 * "Lista" (resumo + lista de apps) e "Destaque" (mais jogado em cartao grande + resumo + demais apps).
 */
public class PlaytimeStatsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "VRLPrefs";
    // Lido tambem pela MainActivity: mantido em sincronia com a permissao real
    private static final String KEY_PERMISSION_GRANTED = "usage_stats_permission_granted";
    private static final String KEY_VIEW_MODE = "playtime_view_mode";
    private static final String VIEW_LIST = "list";
    private static final String VIEW_HIGHLIGHT = "highlight";

    // GitHub cover images URL
    private static final String GITHUB_ICON_BASE_URL =
            "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/";

    private static final int GRID_COLUMNS = 3;

    private PlaytimeTracker playtimeTracker;
    private SharedPreferences prefs;

    // Barra lateral
    private TextView rangeToday, rangeWeek, rangeMonth, rangeAllTime;

    // Cabecalho
    private TextView txtLastUpdated;
    private ImageView btnRefresh, btnResetStats, btnViewToggle;

    // Permissao
    private View permissionCard;

    // Vista Lista
    private View viewList;
    private TextView txtTotalPlaytime, txtMostPlayed, txtGameCount;
    private View currentlyPlayingCard;
    private TextView txtCurrentlyPlaying, txtCurrentPlaytime;
    private RecyclerView playtimeList;

    // Vista Destaque
    private View viewHighlight;
    private View hlHeroCard;
    private ImageView hlHeroThumb;
    private TextView hlHeroName, hlHeroTime, hlTotal, hlGames, hlNowName, hlNowTime, hlOthersLabel;
    private View hlNowCard;
    private RecyclerView playtimeGrid;

    private TextView txtEmpty;

    private PlaytimeAdapter listAdapter;
    private PlaytimeAdapter gridAdapter;

    private PlaytimeTracker.TimeRange currentRange = PlaytimeTracker.TimeRange.TODAY;
    private List<PlaytimeTracker.PlaytimeEntry> entries = new ArrayList<>();
    private final List<PlaytimeTracker.PlaytimeEntry> otherEntries = new ArrayList<>();
    private PlaytimeTracker.PlaytimeEntry heroEntry;
    private long totalEntriesMs = 0;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private boolean highlightView = false;
    private boolean hasAccess = false;
    private boolean dataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(R.color.orbita_panel);
        setContentView(R.layout.activity_playtime_stats);

        playtimeTracker = new PlaytimeTracker(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        highlightView = VIEW_HIGHLIGHT.equals(prefs.getString(KEY_VIEW_MODE, VIEW_LIST));

        bindViews();

        // Periodos
        rangeToday.setOnClickListener(v -> selectRange(PlaytimeTracker.TimeRange.TODAY));
        rangeWeek.setOnClickListener(v -> selectRange(PlaytimeTracker.TimeRange.WEEK));
        rangeMonth.setOnClickListener(v -> selectRange(PlaytimeTracker.TimeRange.MONTH));
        rangeAllTime.setOnClickListener(v -> selectRange(PlaytimeTracker.TimeRange.ALL_TIME));
        findViewById(R.id.navBack).setOnClickListener(v -> finish());
        updateRangeSelection();

        // Listas
        listAdapter = new PlaytimeAdapter(R.layout.item_playtime_stat, false);
        playtimeList.setLayoutManager(new LinearLayoutManager(this));
        playtimeList.setAdapter(listAdapter);

        gridAdapter = new PlaytimeAdapter(R.layout.item_playtime_card, true);
        playtimeGrid.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        playtimeGrid.setAdapter(gridAdapter);

        // Cabecalho
        btnRefresh.setOnClickListener(v -> refreshData());
        btnViewToggle.setOnClickListener(v -> {
            highlightView = !highlightView;
            prefs.edit().putString(KEY_VIEW_MODE, highlightView ? VIEW_HIGHLIGHT : VIEW_LIST).apply();
            updateContentVisibility();
        });
        btnResetStats.setOnClickListener(v -> confirmReset());

        hlHeroCard.setOnClickListener(v -> {
            if (heroEntry != null) showAppDetails(heroEntry);
        });

        findViewById(R.id.btnGrantPermission).setOnClickListener(v -> openUsageAccessSettings());

        // O estado da permissao e os dados sao carregados em onResume (que roda logo apos onCreate).
    }

    private void bindViews() {
        rangeToday = findViewById(R.id.rangeToday);
        rangeWeek = findViewById(R.id.rangeWeek);
        rangeMonth = findViewById(R.id.rangeMonth);
        rangeAllTime = findViewById(R.id.rangeAllTime);

        txtLastUpdated = findViewById(R.id.txtLastUpdated);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnResetStats = findViewById(R.id.btnResetStats);
        btnViewToggle = findViewById(R.id.btnViewToggle);

        permissionCard = findViewById(R.id.permissionCard);

        viewList = findViewById(R.id.viewList);
        txtTotalPlaytime = findViewById(R.id.txtTotalPlaytime);
        txtMostPlayed = findViewById(R.id.txtMostPlayed);
        txtGameCount = findViewById(R.id.txtGameCount);
        currentlyPlayingCard = findViewById(R.id.currentlyPlayingCard);
        txtCurrentlyPlaying = findViewById(R.id.txtCurrentlyPlaying);
        txtCurrentPlaytime = findViewById(R.id.txtCurrentPlaytime);
        playtimeList = findViewById(R.id.playtimeList);

        viewHighlight = findViewById(R.id.viewHighlight);
        hlHeroCard = findViewById(R.id.hlHeroCard);
        hlHeroThumb = findViewById(R.id.hlHeroThumb);
        hlHeroName = findViewById(R.id.hlHeroName);
        hlHeroTime = findViewById(R.id.hlHeroTime);
        hlTotal = findViewById(R.id.hlTotal);
        hlGames = findViewById(R.id.hlGames);
        hlNowCard = findViewById(R.id.hlNowCard);
        hlNowName = findViewById(R.id.hlNowName);
        hlNowTime = findViewById(R.id.hlNowTime);
        hlOthersLabel = findViewById(R.id.hlOthersLabel);
        playtimeGrid = findViewById(R.id.playtimeGrid);

        txtEmpty = findViewById(R.id.txtEmpty);
    }

    // ------------------------------------------------------------------
    // Periodos (barra lateral)
    // ------------------------------------------------------------------

    private void selectRange(PlaytimeTracker.TimeRange range) {
        if (range == currentRange && dataLoaded) return;
        currentRange = range;
        updateRangeSelection();
        refreshData();
    }

    private void updateRangeSelection() {
        rangeToday.setSelected(currentRange == PlaytimeTracker.TimeRange.TODAY);
        rangeWeek.setSelected(currentRange == PlaytimeTracker.TimeRange.WEEK);
        rangeMonth.setSelected(currentRange == PlaytimeTracker.TimeRange.MONTH);
        rangeAllTime.setSelected(currentRange == PlaytimeTracker.TimeRange.ALL_TIME);
    }

    // ------------------------------------------------------------------
    // Permissao de acesso ao uso
    // ------------------------------------------------------------------

    /** Checa a permissao de verdade (AppOps + consulta do tracker) e guarda o resultado para a MainActivity. */
    private boolean checkUsageAccess() {
        boolean granted = false;
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (appOps != null) {
                int mode;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                            Process.myUid(), getPackageName());
                } else {
                    mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                            Process.myUid(), getPackageName());
                }
                if (mode == AppOpsManager.MODE_DEFAULT) {
                    // Concedida via "pm grant" (ADB): o AppOps fica no padrao e vale a permissao
                    granted = checkCallingOrSelfPermission("android.permission.PACKAGE_USAGE_STATS")
                            == PackageManager.PERMISSION_GRANTED;
                } else {
                    granted = mode == AppOpsManager.MODE_ALLOWED;
                }
            }
        } catch (Exception ignored) {
        }
        if (!granted) granted = playtimeTracker.hasPermission();
        prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, granted).apply();
        return granted;
    }

    private void openUsageAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        } catch (Exception ignored) {
        }
        // Sem tela de configuracoes disponivel: mostra o comando ADB
        Toast.makeText(this, getString(R.string.play_grant_manually), Toast.LENGTH_LONG).show();
        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle(R.string.play_adb_title)
                .setMessage(getString(R.string.play_adb_message,
                        "adb shell pm grant " + getPackageName() + " android.permission.PACKAGE_USAGE_STATS"))
                .setPositiveButton(R.string.play_ok, null)
                .create());
    }

    // ------------------------------------------------------------------
    // Visibilidade das vistas
    // ------------------------------------------------------------------

    private void updateContentVisibility() {
        boolean empty = dataLoaded && entries.isEmpty();

        permissionCard.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
        viewList.setVisibility(hasAccess && !highlightView && !empty ? View.VISIBLE : View.GONE);
        viewHighlight.setVisibility(hasAccess && highlightView && !empty ? View.VISIBLE : View.GONE);
        txtEmpty.setVisibility(hasAccess && empty ? View.VISIBLE : View.GONE);

        int actions = hasAccess ? View.VISIBLE : View.GONE;
        btnRefresh.setVisibility(actions);
        btnResetStats.setVisibility(actions);
        btnViewToggle.setVisibility(actions);
        txtLastUpdated.setVisibility(actions);

        // O botao mostra a vista para a qual ele leva
        btnViewToggle.setImageResource(highlightView ? R.drawable.playtime_ic_list : R.drawable.ic_view_module);
        btnViewToggle.setContentDescription(getString(highlightView ? R.string.play_view_list : R.string.play_view_highlight));
    }

    // ------------------------------------------------------------------
    // Dados
    // ------------------------------------------------------------------

    private void refreshData() {
        if (!hasAccess) return;

        final long clearedAt = playtimeTracker.getStatsClearedAt();
        final PlaytimeTracker.TimeRange requested = currentRange;

        playtimeTracker.getPlaytimeLeaderboard(50, requested, leaderboard -> runOnUiThread(() -> {
            if (isFinishing() || requested != currentRange) return;
            entries = leaderboard != null ? leaderboard : new ArrayList<>();
            dataLoaded = true;
            onEntriesChanged();
            updateLastUpdated();

            if (clearedAt > System.currentTimeMillis() - 60000) {
                Toast.makeText(PlaytimeStatsActivity.this, getString(R.string.play_stats_cleared_note), Toast.LENGTH_SHORT).show();
            }
        }));
    }

    /** Recalcula resumo, destaque e listas a partir de {@link #entries}. */
    private void onEntriesChanged() {
        totalEntriesMs = 0;
        heroEntry = null;
        for (PlaytimeTracker.PlaytimeEntry e : entries) {
            totalEntriesMs += e.playtime;
            if (heroEntry == null || e.playtime > heroEntry.playtime) heroEntry = e;
        }
        otherEntries.clear();
        for (PlaytimeTracker.PlaytimeEntry e : entries) {
            if (e != heroEntry) otherEntries.add(e);
        }

        long totalMs = playtimeTracker.getTotalPlaytime(currentRange);
        String total = PlaytimeTracker.formatPlaytime(totalMs);
        String games = String.valueOf(playtimeTracker.getActiveAppsCount(currentRange));

        // Vista Lista
        txtTotalPlaytime.setText(total);
        txtMostPlayed.setText(heroEntry != null ? displayName(heroEntry) : getString(R.string.play_none));
        txtGameCount.setText(games);

        // Vista Destaque
        hlTotal.setText(total);
        hlGames.setText(games);
        if (heroEntry != null) {
            hlHeroName.setText(displayName(heroEntry));
            hlHeroTime.setText(heroEntry.getFormattedPlaytime());
            loadThumb(hlHeroThumb, heroEntry.getPackageName(), 800, 450);
        } else {
            hlHeroName.setText(R.string.play_none);
            hlHeroTime.setText(R.string.play_zero_hours);
            hlHeroThumb.setImageDrawable(null);
        }
        hlOthersLabel.setVisibility(otherEntries.isEmpty() ? View.GONE : View.VISIBLE);

        listAdapter.notifyDataSetChanged();
        gridAdapter.notifyDataSetChanged();
        updateContentVisibility();
    }

    private void updateLastUpdated() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String text = getString(R.string.play_last_updated, sdf.format(new Date()));
        if (currentRange == PlaytimeTracker.TimeRange.ALL_TIME) {
            text += "  ·  " + getString(R.string.play_all_time_note);
        }
        txtLastUpdated.setText(text);
    }

    private void confirmReset() {
        ThemedDialog.showThemed(new AlertDialog.Builder(PlaytimeStatsActivity.this)
                .setTitle(R.string.play_clear_title)
                .setMessage(R.string.play_clear_message)
                .setPositiveButton(R.string.play_clear_all, (d, w) -> {
                    playtimeTracker.clearAllStats();
                    entries = new ArrayList<>();
                    onEntriesChanged();

                    txtTotalPlaytime.setText(R.string.play_zero_hours);
                    hlTotal.setText(R.string.play_zero_hours);
                    txtGameCount.setText("0");
                    hlGames.setText("0");

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    txtLastUpdated.setText(getString(R.string.play_cleared_at, sdf.format(new Date())));

                    Toast.makeText(PlaytimeStatsActivity.this, getString(R.string.play_all_cleared), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.play_cancel, null)
                .create());
    }

    // ------------------------------------------------------------------
    // Jogando agora
    // ------------------------------------------------------------------

    private void startPeriodicUpdates() {
        stopPeriodicUpdates();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentlyPlaying();
                updateHandler.postDelayed(this, 5000);
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (updateRunnable != null) updateHandler.removeCallbacks(updateRunnable);
    }

    private void updateCurrentlyPlaying() {
        String label = null;
        String time = null;
        if (hasAccess) {
            String currentPackage = playtimeTracker.getCurrentRunningApp();
            if (currentPackage != null) {
                PlaytimeTracker.AppInfo info = playtimeTracker.getAppInfo(currentPackage);
                if (info != null) {
                    label = info.label;
                    Long today = playtimeTracker.getTodayPlaytime().get(currentPackage);
                    time = PlaytimeTracker.formatPlaytime(today != null ? today : 0L);
                }
            }
        }
        if (label == null) {
            currentlyPlayingCard.setVisibility(View.GONE);
            hlNowCard.setVisibility(View.GONE);
            return;
        }
        txtCurrentlyPlaying.setText(label);
        txtCurrentPlaytime.setText(time);
        hlNowName.setText(label);
        hlNowTime.setText(time);
        currentlyPlayingCard.setVisibility(View.VISIBLE);
        hlNowCard.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        // Re-checa sempre: o usuario pode ter acabado de voltar das configuracoes
        boolean wasGranted = hasAccess;
        hasAccess = checkUsageAccess();
        updateContentVisibility();
        if (hasAccess) {
            if (!wasGranted) dataLoaded = false;
            refreshData();
            startPeriodicUpdates();
        } else {
            stopPeriodicUpdates();
            currentlyPlayingCard.setVisibility(View.GONE);
            hlNowCard.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPeriodicUpdates();
    }

    // ------------------------------------------------------------------
    // Miniaturas
    // ------------------------------------------------------------------

    private String getGitHubIconUrl(String packageName) {
        return GITHUB_ICON_BASE_URL + packageName + ".jpg";
    }

    private void loadThumb(ImageView target, String packageName, int width, int height) {
        Drawable appIcon;
        try {
            appIcon = getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            appIcon = getDrawable(android.R.drawable.sym_def_app_icon);
        }
        Glide.with(this)
                .load(getGitHubIconUrl(packageName))
                .apply(new RequestOptions()
                        .placeholder(appIcon)
                        .error(appIcon)
                        .centerCrop()
                        .override(width, height)
                        .skipMemoryCache(false)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate())
                .into(target);
    }

    private String displayName(PlaytimeTracker.PlaytimeEntry entry) {
        String name = entry.getAppName();
        return name == null || name.isEmpty() ? entry.getPackageName() : name;
    }

    // ------------------------------------------------------------------
    // Adapter (lista e grade de "demais apps")
    // ------------------------------------------------------------------

    private class PlaytimeAdapter extends RecyclerView.Adapter<PlaytimeAdapter.ViewHolder> {
        private final int layoutId;
        private final boolean othersOnly;

        PlaytimeAdapter(int layoutId, boolean othersOnly) {
            this.layoutId = layoutId;
            this.othersOnly = othersOnly;
        }

        private List<PlaytimeTracker.PlaytimeEntry> data() {
            return othersOnly ? otherEntries : entries;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final PlaytimeTracker.PlaytimeEntry entry = data().get(position);

            holder.txtAppName.setText(displayName(entry));
            holder.txtPlaytime.setText(entry.getFormattedPlaytime());
            loadThumb(holder.imgGameCover, entry.getPackageName(), 400, 225);

            if (holder.txtRank != null) {
                holder.txtRank.setText(String.valueOf(position + 1));
            }
            if (holder.txtPercentage != null) {
                int percent = totalEntriesMs > 0 ? (int) ((entry.playtime * 100) / totalEntriesMs) : 0;
                holder.txtPercentage.setText(getString(R.string.play_share_format, percent));
            }

            holder.itemView.setOnClickListener(v -> showAppDetails(entry));
        }

        @Override
        public int getItemCount() {
            return data().size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView imgGameCover;
            final TextView txtAppName;
            final TextView txtPlaytime;
            final TextView txtPercentage;
            final TextView txtRank;

            ViewHolder(View itemView) {
                super(itemView);
                imgGameCover = itemView.findViewById(R.id.imgGameCover);
                txtAppName = itemView.findViewById(R.id.txtAppName);
                txtPlaytime = itemView.findViewById(R.id.txtPlaytime);
                txtPercentage = itemView.findViewById(R.id.txtPercentage);
                txtRank = itemView.findViewById(R.id.txtRank);
            }
        }
    }

    // ------------------------------------------------------------------
    // Detalhes do app
    // ------------------------------------------------------------------

    private String typeLabel(PlaytimeTracker.AppInfo info) {
        if (info.systemApp) return getString(R.string.play_type_system);
        if (info.storeApp) return getString(R.string.play_type_store);
        if (info.sideloaded) return getString(R.string.play_type_sideloaded);
        return getString(R.string.play_unknown);
    }

    private String rangeLabel(PlaytimeTracker.TimeRange range) {
        switch (range) {
            case TODAY: return getString(R.string.play_range_today);
            case WEEK: return getString(R.string.play_range_week);
            case MONTH: return getString(R.string.play_range_month);
            case ALL_TIME: return getString(R.string.play_range_all_time);
            default: return "";
        }
    }

    private void showAppDetails(PlaytimeTracker.PlaytimeEntry entry) {
        if (entry == null || entry.appInfo == null) return;

        PlaytimeTracker.AppInfo info = entry.appInfo;
        String buildVersion = "v" + info.versionCode + "+" + info.versionName;
        String installSource = info.storeApp
                ? getString(R.string.play_source_store)
                : (info.sideloaded ? getString(R.string.play_source_sideloaded) : getString(R.string.play_source_system));

        String details = getString(R.string.play_details_format,
                info.label,
                info.packageName,
                buildVersion,
                typeLabel(info),
                info.firstInstallDate,
                info.lastUpdateDate,
                rangeLabel(currentRange),
                entry.getFormattedPlaytimeDetailed(),
                entry.getFormattedPlaytime(),
                installSource);

        ThemedDialog.showThemed(new AlertDialog.Builder(PlaytimeStatsActivity.this)
                .setTitle(R.string.play_app_details)
                .setMessage(details)
                .setPositiveButton(R.string.play_app_info, (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + info.packageName));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(PlaytimeStatsActivity.this, getString(R.string.play_cannot_open_app_info), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.play_close, null)
                .create());
    }
}
