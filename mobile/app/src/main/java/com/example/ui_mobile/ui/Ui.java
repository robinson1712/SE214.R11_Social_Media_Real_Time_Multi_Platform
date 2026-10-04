package com.example.ui_mobile.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ui_mobile.R;

import java.util.Locale;

/** Small view helpers shared by all screens. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 1200 → "1.2K", 28400 → "28.4K". */
    public static String compact(int n) {
        if (n < 1000) return String.valueOf(n);
        float k = n / 1000f;
        String s = String.format(Locale.US, k >= 100 ? "%.0f" : "%.1f", k);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + "K";
    }

    public static void toast(Context c, CharSequence msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void share(Context c, String text) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        c.startActivity(Intent.createChooser(send, "Chia sẻ qua"));
    }

    /**
     * Makes the pill-style TextView children of {@code row} behave like single-choice chips.
     * The listener receives the index of the chip the user picked.
     */
    public static void singleChoiceChips(ViewGroup row, int selected, OnChipSelected listener) {
        for (int i = 0; i < row.getChildCount(); i++) {
            final int index = i;
            View child = row.getChildAt(i);
            styleChip(child, i == selected);
            child.setOnClickListener(v -> {
                for (int j = 0; j < row.getChildCount(); j++) styleChip(row.getChildAt(j), j == index);
                if (listener != null) listener.onSelected(index);
            });
        }
    }

    public static void styleChip(View chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_pill_primary : R.drawable.bg_pill);
        if (chip instanceof TextView) {
            Context c = chip.getContext();
            TextView tv = (TextView) chip;
            tv.setTextColor(c.getColor(selected ? R.color.white : R.color.text_primary));
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                    c.getColor(selected ? R.color.white : R.color.primary)));
        }
    }

    public interface OnChipSelected {
        void onSelected(int index);
    }
}
