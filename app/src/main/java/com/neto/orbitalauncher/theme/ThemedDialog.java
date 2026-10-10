package com.neto.orbitalauncher.theme;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.neto.orbitalauncher.R;

/**
 * Janelas de aviso (AlertDialog) com a identidade visual do Orbita:
 * painel cinza arredondado (20dp), titulo branco, mensagem em cinza claro
 * e botoes em azul. O tema Theme.Orbita.Dialog.Alert (themes.xml) ja aplica
 * o mesmo visual em todos os dialogos; este helper reforca as cores depois
 * que o dialogo aparece (inclusive em views personalizadas via setView).
 */
public class ThemedDialog {

    /** Tag que faz o helper deixar a view com as cores que ela ja tem. */
    private static final String TAG_IGNORE = "theme_ignore";

    /**
     * Apply the Orbita look to all parts of the dialog
     */
    public static void apply(AlertDialog dialog) {
        if (dialog == null || !dialog.isShowing()) return;

        Context context = dialog.getContext();
        Window window = dialog.getWindow();
        if (window == null) return;

        // Painel cinza arredondado (20dp) com margem, igual ao tema dos dialogos
        window.setBackgroundDrawableResource(R.drawable.dialog_bg_inset);

        int text = ContextCompat.getColor(context, R.color.orbita_text);
        int textSecondary = ContextCompat.getColor(context, R.color.orbita_text_secondary);
        int textMuted = ContextCompat.getColor(context, R.color.orbita_text_muted);
        int accent = ContextCompat.getColor(context, R.color.orbita_accent);
        int border = ContextCompat.getColor(context, R.color.orbita_border);

        // Percorre a arvore do dialogo tirando fundos pretos e acertando os textos
        View decorView = window.getDecorView();
        if (decorView != null) {
            forceColors(decorView, text, textMuted, accent, border);
        }

        // Titulo branco, mensagem em cinza claro, botoes em azul
        themeTitleMessageButtons(dialog, text, textSecondary, accent);

        // Itens de lista (setItems)
        themeListItems(dialog, text, textMuted, accent, border);
    }

    /**
     * Recursively apply the Orbita colors to every view
     */
    private static void forceColors(View view, int text, int textMuted, int accent, int border) {
        if (view == null) return;
        boolean ignore = view.getTag() != null && TAG_IGNORE.equals(view.getTag().toString());

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            // Fundos solidos dentro do dialogo cobririam os cantos arredondados:
            // deixa transparente para o painel cinza aparecer
            if (!ignore && view.getBackground() instanceof ColorDrawable) {
                int bgColor = ((ColorDrawable) view.getBackground()).getColor();
                if (bgColor != Color.TRANSPARENT) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                }
            }

            for (int i = 0; i < group.getChildCount(); i++) {
                forceColors(group.getChildAt(i), text, textMuted, accent, border);
            }
            return;
        }

        if (ignore) return;

        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(text);
            et.setHintTextColor(textMuted);
            et.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        } else if (view instanceof TextView && !(view instanceof Button)) {
            TextView tv = (TextView) view;
            tv.setTextColor(text);
            tv.setLinkTextColor(accent);
        }

        // Divisores e outras views com cor solida escura: cinza da borda
        if (view.getBackground() instanceof ColorDrawable) {
            int bgColor = ((ColorDrawable) view.getBackground()).getColor();
            if (bgColor != Color.TRANSPARENT) {
                int red = (bgColor >> 16) & 0xFF;
                int green = (bgColor >> 8) & 0xFF;
                int blue = bgColor & 0xFF;
                int brightness = (red + green + blue) / 3;
                // Muito escuro/claro (ex.: amostras de cor) fica como esta
                if (brightness > 20 && brightness < 100) {
                    view.setBackgroundColor(border);
                }
            }
        }
    }

    /**
     * Title, message and the bottom buttons
     */
    private static void themeTitleMessageButtons(AlertDialog dialog, int text, int textSecondary, int accent) {
        int[] which = {
                DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL
        };
        for (int w : which) {
            Button btn = dialog.getButton(w);
            if (btn != null) {
                btn.setTextColor(accent);
                btn.setAllCaps(false);
            }
        }

        // androidx AlertDialog usa R.id.alertTitle do appcompat; o do framework fica como reserva
        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title == null) {
            int titleId = dialog.getContext().getResources()
                    .getIdentifier("alertTitle", "id", "android");
            if (titleId > 0) title = dialog.findViewById(titleId);
        }
        if (title != null) {
            title.setTextColor(text);
        }

        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(textSecondary);
            message.setLinkTextColor(accent);
        }
    }

    /**
     * Theme list items in setItems()-based dialogs
     */
    private static void themeListItems(AlertDialog dialog, int text, int textMuted, int accent, int border) {
        ListView listView = dialog.getListView();
        if (listView != null) {
            // Transparente para manter os cantos arredondados do painel
            listView.setBackgroundColor(Color.TRANSPARENT);
            listView.setDivider(null);
            listView.setDividerHeight(0);

            // Re-color list children after layout
            listView.post(() -> {
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (child != null) {
                        forceColors(child, text, textMuted, accent, border);
                    }
                }
            });
        }
    }

    /**
     * Show a dialog with the Orbita look applied automatically
     */
    public static AlertDialog showThemed(AlertDialog dialog) {
        dialog.setOnShowListener(d -> apply((AlertDialog) d));
        dialog.show();
        apply(dialog);
        return dialog;
    }
}
