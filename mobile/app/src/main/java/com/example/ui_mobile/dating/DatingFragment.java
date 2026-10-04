package com.example.ui_mobile.dating;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ui_mobile.R;
import com.example.ui_mobile.chat.ChatActivity;
import com.example.ui_mobile.data.DatingProfile;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.ui.Anim;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.Ui;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class DatingFragment extends Fragment {

    private final List<DatingProfile> profiles = MockData.datingProfiles();
    private int index;
    private int previous = -1;
    private View card;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dating, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        card = view.findViewById(R.id.dating_card);
        render(view);

        view.findViewById(R.id.dating_pass).setOnClickListener(v -> swipe(view, -1));
        view.findViewById(R.id.dating_like).setOnClickListener(v -> {
            Anim.pop(v);
            // Every second like is a mutual match in the mock so the flow can be demoed.
            if (index % 2 == 1) {
                Ui.toast(requireContext(), "Hai bạn đã đồng điệu với " + profiles.get(index).name + " 💜");
            }
            swipe(view, 1);
        });
        view.findViewById(R.id.dating_super).setOnClickListener(v -> {
            Anim.pop(v);
            Ui.toast(requireContext(), "Đã gửi Siêu đồng điệu tới " + profiles.get(index).name + " ✨");
            swipe(view, 1);
        });
        view.findViewById(R.id.dating_rewind).setOnClickListener(v -> {
            if (previous < 0) {
                Ui.toast(requireContext(), "Chưa có hồ sơ nào để quay lại");
                return;
            }
            index = previous;
            previous = -1;
            render(view);
            card.setAlpha(0f);
            card.animate().alpha(1f).setDuration(200).start();
        });
        view.findViewById(R.id.dating_note).setOnClickListener(v -> {
            DatingProfile p = profiles.get(index);
            startActivity(new Intent(requireContext(), ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_NAME, p.name));
        });
        view.findViewById(R.id.dating_filter).setOnClickListener(v ->
                Ui.toast(requireContext(), "Bộ lọc: độ tuổi, khoảng cách, sở thích"));
    }

    private void swipe(View root, int direction) {
        card.animate()
                .translationX(direction * card.getWidth() * 1.2f)
                .rotation(direction * 12f)
                .alpha(0f)
                .setDuration(260)
                .withEndAction(() -> {
                    previous = index;
                    index = (index + 1) % profiles.size();
                    render(root);
                    card.setTranslationX(0f);
                    card.setRotation(0f);
                    card.setScaleX(0.94f);
                    card.setScaleY(0.94f);
                    card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
                })
                .start();
    }

    private void render(View root) {
        DatingProfile p = profiles.get(index);
        root.findViewById(R.id.dating_photo).setBackgroundResource(p.scene);
        ((AvatarView) root.findViewById(R.id.dating_avatar)).setName(p.name);
        ((TextView) root.findViewById(R.id.dating_name)).setText(p.name + ", " + p.age);
        ((TextView) root.findViewById(R.id.dating_job)).setText(p.job);
        ((TextView) root.findViewById(R.id.dating_distance)).setText(p.distance);
        ((TextView) root.findViewById(R.id.dating_match)).setText("● " + p.match + "% Đồng điệu tâm hồn");
        ((TextView) root.findViewById(R.id.dating_song)).setText(p.song);
        ((TextView) root.findViewById(R.id.dating_prompt)).setText(p.prompt);
        ((TextView) root.findViewById(R.id.dating_answer)).setText(p.answer);
        ((TextView) root.findViewById(R.id.dating_common)).setText("Góc nhìn chung: " + p.common);

        ChipGroup tags = root.findViewById(R.id.dating_tags);
        tags.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (String tag : p.tags) {
            TextView t = (TextView) inflater.inflate(R.layout.item_tag, tags, false);
            t.setText(tag);
            tags.addView(t);
        }
    }
}
