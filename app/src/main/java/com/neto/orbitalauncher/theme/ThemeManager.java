package com.neto.orbitalauncher.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;

/**
 * Paleta unica do app (o sistema de temas foi removido).
 * Mantem o nome ThemeManager so para os helpers de desenho continuarem funcionando,
 * mas existe um unico visual fixo: painel cinza-escuro estilo biblioteca do Quest.
 */
public class ThemeManager {
    private static ThemeManager instance;

    private final Context context;
    private final Theme fixedTheme;

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.fixedTheme = buildFixedTheme();
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    /** Sempre devolve a mesma paleta fixa. */
    public Theme getCurrentTheme() {
        return fixedTheme;
    }

    private static Theme buildFixedTheme() {
        Theme t = new Theme();
        t.id = "evolve";
        t.name = "Evolve";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#2B2C2F");   // barra lateral / fundo de telas
        t.bgSecondary = Color.parseColor("#3A3B3F"); // painel principal
        t.bgTertiary = Color.parseColor("#55565B");  // pilulas e botoes
        t.accentPrimary = Color.parseColor("#4C8DFF");
        t.accentSecondary = Color.parseColor("#6BA1FF");
        t.accentTertiary = Color.parseColor("#A9C8FF");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#D0D1D4");
        t.textMuted = Color.parseColor("#9A9BA0");
        t.borderPrimary = Color.parseColor("#4A4B50");
        t.borderAccent = Color.parseColor("#4C8DFF");
        t.buttonCornerRadius = 20;
        t.cardCornerRadius = 16;
        t.dialogCornerRadius = 24;
        t.borderWidth = 0;
        t.backgroundType = Theme.BackgroundType.SOLID;
        return t;
    }

    // ============================================
    // DRAWABLE GENERATORS
    // Generate drawables at runtime from current theme
    // ============================================

    /**
     * Create button background drawable (normal/pressed states)
     */
    public StateListDrawable createButtonDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Pressed state - brighter
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(dpToPx(t.buttonCornerRadius));
        pressed.setColor(adjustAlpha(t.accentPrimary, 80));
        if (t.borderWidth > 0) {
            pressed.setStroke(dpToPx(t.borderWidth), t.accentPrimary);
        }

        // Normal state
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(t.buttonCornerRadius));
        normal.setColor(t.bgSecondary);
        if (t.borderWidth > 0) {
            normal.setStroke(dpToPx(t.borderWidth), t.borderAccent);
        }

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Create accent button drawable (more vibrant)
     */
    public StateListDrawable createAccentButtonDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Pressed
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(dpToPx(t.buttonCornerRadius));
        pressed.setColor(t.accentPrimary);
        if (t.borderWidth > 0) {
            pressed.setStroke(dpToPx(t.borderWidth), t.textPrimary);
        }

        // Normal
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(t.buttonCornerRadius));
        normal.setColor(adjustAlpha(t.accentPrimary, 200));
        if (t.borderWidth > 0) {
            normal.setStroke(dpToPx(t.borderWidth), t.accentPrimary);
        }

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Create card/section background
     */
    public GradientDrawable createCardDrawable() {
        Theme t = getCurrentTheme();
        GradientDrawable card = new GradientDrawable();
        card.setShape(GradientDrawable.RECTANGLE);
        card.setCornerRadius(dpToPx(t.cardCornerRadius));
        card.setColor(t.bgSecondary);
        if (t.borderWidth > 0) {
            card.setStroke(dpToPx(t.borderWidth), t.borderPrimary);
        }
        return card;
    }

    /**
     * Create dialog/window background
     */
    public GradientDrawable createDialogDrawable() {
        Theme t = getCurrentTheme();
        GradientDrawable dialog = new GradientDrawable();
        dialog.setShape(GradientDrawable.RECTANGLE);
        dialog.setCornerRadius(dpToPx(t.dialogCornerRadius));
        dialog.setColor(t.bgPrimary);
        if (t.borderWidth > 0) {
            dialog.setStroke(dpToPx(t.borderWidth), t.borderAccent);
        }
        return dialog;
    }

    /**
     * Create chip/pill drawable
     */
    public StateListDrawable createChipDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Selected (active)
        GradientDrawable selected = new GradientDrawable();
        selected.setShape(GradientDrawable.RECTANGLE);
        selected.setCornerRadius(dpToPx(20));
        selected.setColor(t.accentPrimary);
        if (t.borderWidth > 0) {
            selected.setStroke(dpToPx(t.borderWidth), t.textPrimary);
        }

        // Normal
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(20));
        normal.setColor(t.bgSecondary);
        if (t.borderWidth > 0) {
            normal.setStroke(dpToPx(t.borderWidth), t.borderPrimary);
        }

        states.addState(new int[]{android.R.attr.state_selected}, selected);
        states.addState(new int[]{android.R.attr.state_activated}, selected);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Create edit text background
     */
    public StateListDrawable createEditTextDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Focused
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(dpToPx(10));
        focused.setColor(t.bgSecondary);
        focused.setStroke(dpToPx(Math.max(t.borderWidth, 1)), t.accentPrimary);

        // Normal
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(10));
        normal.setColor(t.bgTertiary);
        normal.setStroke(dpToPx(Math.max(t.borderWidth, 1)), t.borderPrimary);

        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    // ============================================
    // UTILITIES
    // ============================================

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Adjust alpha of a color (0-255)
     */
    public static int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Get color state list for text
     */
    public ColorStateList getTextColorStateList() {
        Theme t = getCurrentTheme();
        return ColorStateList.valueOf(t.textPrimary);
    }

    /**
     * Get color state list for secondary text
     */
    public ColorStateList getSecondaryTextColorStateList() {
        Theme t = getCurrentTheme();
        return ColorStateList.valueOf(t.textSecondary);
    }
}