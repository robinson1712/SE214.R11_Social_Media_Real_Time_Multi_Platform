package com.example.ui_mobile.profile;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ui_mobile.R;
import com.example.ui_mobile.auth.LoginActivity;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.PostBinder;
import com.example.ui_mobile.ui.Ui;
import com.google.android.material.card.MaterialCardView;

public class ProfileFragment extends Fragment {

    private static final int[] FRIEND_SCENES = {
            R.drawable.scene_room, R.drawable.scene_desk, R.drawable.scene_coffee,
            R.drawable.scene_greenhouse, R.drawable.scene_lavender, R.drawable.scene_matcha
    };
    private static final int[] GALLERY = {
            R.drawable.scene_dawn, R.drawable.scene_coffee, R.drawable.scene_lavender,
            R.drawable.scene_desk, R.drawable.scene_mist, R.drawable.scene_ceramic,
            R.drawable.scene_greenhouse, R.drawable.scene_room, R.drawable.scene_matcha
    };

    private FrameLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        content = view.findViewById(R.id.profile_content);
        bindHighlights(view.findViewById(R.id.profile_highlights));
        bindFriends(view.findViewById(R.id.profile_friends));
        bindTabs(view.findViewById(R.id.profile_tabs));

        view.findViewById(R.id.profile_add_moment).setOnClickListener(v ->
                Ui.toast(requireContext(), "Thêm một khoảnh khắc vào hồ sơ"));
        view.findViewById(R.id.profile_edit).setOnClickListener(v ->
                Ui.toast(requireContext(), "Chỉnh sửa hồ sơ"));
        view.findViewById(R.id.profile_more).setOnClickListener(v ->
                Ui.share(requireContext(), "Ghé thăm salon của " + MockData.ME + " trên Aura: minhdesign.aura.vn"));
        view.findViewById(R.id.profile_friends_all).setOnClickListener(v ->
                Ui.toast(requireContext(), "860 người bạn"));
        view.findViewById(R.id.profile_logout).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
    }

    private void bindHighlights(LinearLayout row) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View create = inflater.inflate(R.layout.item_highlight, row, false);
        create.findViewById(R.id.highlight_ring).setBackgroundResource(R.drawable.bg_circle_alt);
        create.findViewById(R.id.highlight_image).setBackgroundResource(R.color.surface_alt);
        create.findViewById(R.id.highlight_icon).setVisibility(View.VISIBLE);
        ((TextView) create.findViewById(R.id.highlight_label)).setText("Tạo mới");
        create.setOnClickListener(v -> Ui.toast(requireContext(), "Tạo khoảnh khắc lưu giữ mới"));
        row.addView(create);

        for (String[] h : MockData.HIGHLIGHTS) {
            View item = inflater.inflate(R.layout.item_highlight, row, false);
            item.findViewById(R.id.highlight_image).setBackgroundResource(MockData.sceneOf(h[1]));
            ((TextView) item.findViewById(R.id.highlight_label)).setText(h[0]);
            item.setOnClickListener(v -> Ui.toast(requireContext(), h[0]));
            row.addView(item);
        }
    }

    private void bindFriends(LinearLayout grid) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        LinearLayout row = null;
        for (int i = 0; i < MockData.FRIENDS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            String[] f = MockData.FRIENDS[i];
            View item = inflater.inflate(R.layout.item_friend, row, false);
            item.findViewById(R.id.friend_bg).setBackgroundResource(FRIEND_SCENES[i % FRIEND_SCENES.length]);
            ((AvatarView) item.findViewById(R.id.friend_avatar)).setName(f[0]);
            ((TextView) item.findViewById(R.id.friend_name)).setText(f[0]);
            ((TextView) item.findViewById(R.id.friend_mutual)).setText(f[1]);
            row.addView(item);
        }
    }

    private void bindTabs(LinearLayout tabs) {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            final int index = i;
            tabs.getChildAt(i).setOnClickListener(v -> selectTab(tabs, index));
        }
        selectTab(tabs, 0);
    }

    private void selectTab(LinearLayout tabs, int index) {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean selected = i == index;
            t.setBackgroundResource(selected ? R.drawable.bg_segment_selected : 0);
            t.setTextColor(requireContext().getColor(selected ? R.color.primary : R.color.text_secondary));
            t.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }

        content.removeAllViews();
        switch (index) {
            case 0: {
                LinearLayout list = new LinearLayout(requireContext());
                list.setOrientation(LinearLayout.VERTICAL);
                content.addView(list);
                PostBinder.addTo(list, MockData.profilePinnedPost());
                break;
            }
            case 1:
                content.addView(buildGallery());
                break;
            default:
                content.addView(buildEmpty(index == 2
                        ? "Chưa có video nào — hãy quay một reel đầu tiên nhé 🎬"
                        : "Những bài viết bạn lưu trữ sẽ xuất hiện ở đây"));
        }
    }

    private View buildGallery() {
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int gap = Ui.dp(requireContext(), 3);
        int cell = (getResources().getDisplayMetrics().widthPixels - Ui.dp(requireContext(), 24)) / 3;
        LinearLayout row = null;
        for (int i = 0; i < GALLERY.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(requireContext());
                grid.addView(row);
            }
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setRadius(Ui.dp(requireContext(), 10));
            card.setCardElevation(0);
            card.setStrokeWidth(0);
            View img = new View(requireContext());
            img.setBackgroundResource(GALLERY[i]);
            card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cell - gap * 2, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(card, lp);
        }
        return grid;
    }

    private View buildEmpty(String text) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextColor(requireContext().getColor(R.color.text_secondary));
        t.setTextSize(14);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_card);
        int p = Ui.dp(requireContext(), 32);
        t.setPadding(p, p, p, p);
        return t;
    }
}
