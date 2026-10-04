package com.example.ui_mobile.groups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.data.Post;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.PostBinder;
import com.example.ui_mobile.ui.Ui;
import com.google.android.material.button.MaterialButton;

public class GroupFragment extends Fragment {

    private static final String[] MEMBER_PREVIEW = {"Linh Đan", "Khánh Linh", "Hoàng Nam", "Mai An"};

    private boolean joined = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LinearLayout avatars = view.findViewById(R.id.group_member_avatars);
        int size = Ui.dp(requireContext(), 30);
        for (int i = 0; i < MEMBER_PREVIEW.length; i++) {
            AvatarView a = new AvatarView(requireContext(), null);
            a.setRing(true);
            a.setName(MEMBER_PREVIEW[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(-Ui.dp(requireContext(), 10));
            avatars.addView(a, lp);
        }

        Ui.singleChoiceChips(view.findViewById(R.id.group_tabs), 0, index -> { });

        MaterialButton joinedBtn = view.findViewById(R.id.group_joined);
        joinedBtn.setOnClickListener(v -> {
            joined = !joined;
            joinedBtn.setText(joined ? "Đã tham gia" : "Tham gia");
            joinedBtn.setIconResource(joined ? R.drawable.ic_check : R.drawable.ic_add);
            Ui.toast(requireContext(), joined ? "Chào mừng bạn trở lại nhóm 🌿" : "Bạn đã rời nhóm");
        });
        view.findViewById(R.id.group_invite).setOnClickListener(v ->
                Ui.toast(requireContext(), "Đã sao chép lời mời tham gia nhóm"));
        view.findViewById(R.id.group_share).setOnClickListener(v ->
                Ui.share(requireContext(), "Tham gia Cộng đồng Yêu Thiết Kế & Lối Sống Tối Giản trên Aura"));
        view.findViewById(R.id.group_search).setOnClickListener(v ->
                Ui.toast(requireContext(), "Tìm kiếm trong nhóm"));
        view.findViewById(R.id.group_see_all).setOnClickListener(v ->
                Ui.toast(requireContext(), "18.2K thành viên"));
        view.findViewById(R.id.group_composer).setOnClickListener(v ->
                Ui.toast(requireContext(), "Viết bài trong nhóm"));

        LinearLayout posts = view.findViewById(R.id.group_posts);
        for (Post p : MockData.groupPosts()) PostBinder.addTo(posts, p);
    }
}
