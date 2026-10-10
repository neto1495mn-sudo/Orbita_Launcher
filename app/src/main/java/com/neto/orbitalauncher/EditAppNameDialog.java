package com.neto.orbitalauncher;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

/**
 * Dialog for editing custom app names
 */
public class EditAppNameDialog {

    public interface OnNameSavedListener {
        void onNameSaved();
    }

    /**
     * Show dialog to edit app name
     * @param context Activity context
     * @param packageName The app's package name
     * @param currentName The current display name
     * @param defaultName The original APK name (for reset button)
     * @param appNameManager The name manager
     * @param listener Callback when name is saved
     */
    public static void show(Context context, String packageName, String currentName,
                            String defaultName, AppNameManager appNameManager,
                            OnNameSavedListener listener) {

        // Create container for the EditText with padding
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 20);

        // Show original name as hint
        TextView hintText = new TextView(context);
        hintText.setText(context.getString(R.string.main_edit_original, defaultName));
        hintText.setTextColor(ContextCompat.getColor(context, R.color.orbita_text_secondary));
        hintText.setTextSize(12);
        hintText.setPadding(0, 0, 0, 16);
        container.addView(hintText);

        // EditText for new name
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(currentName);
        input.setSelection(currentName.length()); // Cursor at end
        input.setHint(context.getString(R.string.main_edit_hint));
        // Cores do painel Orbita (texto branco, dica cinza, linha azul)
        input.setTextColor(ContextCompat.getColor(context, R.color.orbita_text));
        input.setHintTextColor(ContextCompat.getColor(context, R.color.orbita_text_muted));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.orbita_accent)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        input.setLayoutParams(params);
        container.addView(input);

        // Build dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.main_edit_dialog_title))
                .setView(container)
                .setPositiveButton(context.getString(R.string.main_btn_save), (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.main_toast_name_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    appNameManager.setCustomName(packageName, newName);
                    Toast.makeText(context, context.getString(R.string.main_toast_edit_renamed, newName), Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onNameSaved();
                    }
                })
                .setNegativeButton(context.getString(R.string.main_btn_cancel), null);

        // Only show reset button if custom name is currently set
        if (appNameManager.hasCustomName(packageName)) {
            builder.setNeutralButton(context.getString(R.string.main_btn_reset), (dialog, which) -> {
                appNameManager.clearCustomName(packageName);
                Toast.makeText(context, context.getString(R.string.main_toast_edit_reset, defaultName), Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onNameSaved();
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}