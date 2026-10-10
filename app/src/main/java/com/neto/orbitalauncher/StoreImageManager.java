package com.neto.orbitalauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Origem das imagens dos apps.
 *
 * "evolve": colecao do Evolve (JarJarBlinkz/LauncherIcons), como sempre foi.
 * "meta":   foto da loja da Meta. A lista de fotos vem do MetaMetadata (threethan),
 *           que copia os dados da loja todo dia. A foto e baixada e guardada no Quest;
 *           enquanto nao existe, aparece o icone original do app.
 */
public final class StoreImageManager {

    private static final String TAG = "StoreImageManager";

    public static final String PREF_IMAGE_SOURCE = "image_source";
    public static final String SOURCE_EVOLVE = "evolve";
    public static final String SOURCE_META = "meta";

    private static final String EVOLVE_BASE_URL =
            "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/";
    private static final String META_DATA_BASE_URL =
            "https://raw.githubusercontent.com/threethan/MetaMetadata/main/data/common/";

    private static final String KEY_IMAGE_PREFIX = "store_img_";
    // Evita baixar tudo de novo se a internet cair e voltar varias vezes seguidas
    private static final long MIN_SYNC_INTERVAL_MS = 10 * 60 * 1000L;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean syncing = new AtomicBoolean(false);
    private static long lastSyncAt = 0;

    private StoreImageManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("VRLPrefs", Context.MODE_PRIVATE);
    }

    public static String getSource(Context context) {
        return prefs(context).getString(PREF_IMAGE_SOURCE, SOURCE_EVOLVE);
    }

    public static boolean isMeta(Context context) {
        return SOURCE_META.equals(getSource(context));
    }

    private static final String KEY_APP_SOURCE_PREFIX = "img_src_";

    /** Escolha feita so para este app (null = segue o padrao das Configuracoes). */
    public static String getAppSource(Context context, String packageName) {
        return prefs(context).getString(KEY_APP_SOURCE_PREFIX + packageName, null);
    }

    public static void setAppSource(Context context, String packageName, String source) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (source == null) e.remove(KEY_APP_SOURCE_PREFIX + packageName);
        else e.putString(KEY_APP_SOURCE_PREFIX + packageName, source);
        e.apply();
    }

    /** Este app usa a foto da loja da Meta? (escolha do app, senao o padrao) */
    public static boolean usesMeta(Context context, String packageName) {
        String own = getAppSource(context, packageName);
        return own != null ? SOURCE_META.equals(own) : isMeta(context);
    }

    public static void setSource(Context context, String source) {
        prefs(context).edit().putString(PREF_IMAGE_SOURCE, source).apply();
        lastSyncAt = 0;
    }

    /** Apaga as fotos guardadas da loja para baixar tudo de novo na proxima conexao. */
    public static void clearSaved(Context context) {
        File[] files = imageDir(context).listFiles();
        if (files != null) for (File f : files) f.delete();
        SharedPreferences.Editor e = prefs(context).edit();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith(KEY_IMAGE_PREFIX)) e.remove(key);
        }
        e.apply();
        lastSyncAt = 0;
    }

    public static String evolveUrl(String packageName) {
        return EVOLVE_BASE_URL + packageName + ".jpg";
    }

    private static File imageDir(Context context) {
        File dir = new File(context.getFilesDir(), "store_images");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Foto da loja ja guardada no Quest, ou null se ainda nao foi baixada. */
    public static File getSavedImage(Context context, String packageName) {
        File f = new File(imageDir(context), packageName + ".img");
        return f.exists() && f.length() > 0 ? f : null;
    }

    /**
     * O que carregar no Glide para este app: um File (loja da Meta),
     * uma URL (Evolve) ou null (usar o icone original).
     */
    public static Object imageModel(Context context, String packageName) {
        if (usesMeta(context, packageName)) return getSavedImage(context, packageName);
        return evolveUrl(packageName);
    }

    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Busca na loja da Meta as fotos de todos os apps e guarda as novas ou mudadas.
     * Chama onChanged (na tela principal) se alguma foto foi trocada.
     */
    public static void sync(Context context, List<String> packageNames, boolean force, Runnable onChanged) {
        if (packageNames.isEmpty() || !isOnline(context)) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastSyncAt < MIN_SYNC_INTERVAL_MS) return;
        if (!syncing.compareAndSet(false, true)) return;
        if (!force) lastSyncAt = now;

        Context app = context.getApplicationContext();
        executor.execute(() -> {
            boolean changed = false;
            try {
                for (String pkg : packageNames) {
                    if (!usesMeta(app, pkg)) continue;
                    try {
                        if (syncOne(app, pkg)) changed = true;
                    } catch (Exception e) {
                        Log.w(TAG, "No store image for " + pkg + ": " + e.getMessage());
                    }
                }
            } finally {
                syncing.set(false);
            }
            if (changed && onChanged != null) {
                new Handler(Looper.getMainLooper()).post(onChanged);
            }
        });
    }

    private static boolean syncOne(Context context, String pkg) throws Exception {
        byte[] json = download(META_DATA_BASE_URL + pkg + ".json", 256 * 1024);
        if (json == null) return false;
        JSONObject obj = new JSONObject(new String(json, "UTF-8"));
        String url = obj.optString("landscape", "");
        if (url.isEmpty()) url = obj.optString("hero", "");
        if (url.isEmpty()) return false;

        // A URL da loja muda a parte depois do "?" todo dia; a foto so mudou se o caminho mudou
        String key = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        SharedPreferences p = prefs(context);
        File target = new File(imageDir(context), pkg + ".img");
        if (key.equals(p.getString(KEY_IMAGE_PREFIX + pkg, null)) && target.exists() && target.length() > 0) {
            return false;
        }

        byte[] image = download(url, 8 * 1024 * 1024);
        if (image == null || image.length == 0) return false;
        File tmp = new File(imageDir(context), pkg + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(image);
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            return false;
        }
        p.edit().putString(KEY_IMAGE_PREFIX + pkg, key).apply();
        return true;
    }

    private static byte[] download(String address, int maxBytes) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "OrbitaLauncher");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > maxBytes) return null;
                }
                return buf.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }
}
