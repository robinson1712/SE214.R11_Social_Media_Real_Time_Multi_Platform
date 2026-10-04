package com.example.ui_mobile.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ui_mobile.MainActivity;
import com.example.ui_mobile.R;
import com.example.ui_mobile.chat.ChatActivity;
import com.example.ui_mobile.ui.Ui;

public class NotificationsFragment extends Fragment {

    private static final String PRIMARY = "#4648B0";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setHtml(view, R.id.notif_like_text, name("Lê Thu Hà") + " và 18 người khác đã thả tim cho khoảnh khắc mới của bạn:");
        setHtml(view, R.id.notif_like_meta, "3 phút trước  •  <font color='" + PRIMARY + "'>Khoảnh khắc thương nhất</font>");
        setHtml(view, R.id.notif_comment_text, name("Trần Minh Quân") + " đã phản hồi bài viết của bạn:");
        setHtml(view, R.id.notif_live_text, "Cộng đồng Nhiếp ảnh &amp; Lối sống đang phát trực tiếp buổi workshop: "
                + name("\"Nhiếp ảnh đời thường &amp; Nghệ thuật tìm thấy sự tĩnh tại\""));

        View sectionNew = view.findViewById(R.id.notif_section_new);
        View sectionInvites = view.findViewById(R.id.notif_section_invites);
        View sectionEarlier = view.findViewById(R.id.notif_section_earlier);

        Ui.singleChoiceChips(view.findViewById(R.id.notif_filters), 0, index -> {
            // 0 = all, 1 = unread, 2 = connection requests, 3 = interactions
            sectionNew.setVisibility(index == 0 || index == 1 || index == 3 ? View.VISIBLE : View.GONE);
            sectionInvites.setVisibility(index == 0 || index == 2 ? View.VISIBLE : View.GONE);
            sectionEarlier.setVisibility(index == 0 || index == 3 ? View.VISIBLE : View.GONE);
        });

        view.findViewById(R.id.notif_mark_read).setOnClickListener(v -> {
            hideUnreadDots((ViewGroup) view);
            ((TextView) view.findViewById(R.id.notif_filter_unread)).setText("Chưa đọc");
            Ui.toast(requireContext(), "Đã đánh dấu tất cả là đã đọc");
        });

        view.findViewById(R.id.notif_like).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(MainActivity.TAB_PROFILE));
        view.findViewById(R.id.notif_reply).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ChatActivity.class)
                        .putExtra(ChatActivity.EXTRA_NAME, "Trần Minh Quân")));
        view.findViewById(R.id.notif_live).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(MainActivity.TAB_REELS));
        view.findViewById(R.id.notif_journey).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(MainActivity.TAB_PROFILE));

        TextView result = view.findViewById(R.id.notif_invite_result);
        View actions = view.findViewById(R.id.notif_invite_actions);
        view.findViewById(R.id.notif_accept).setOnClickListener(v -> {
            actions.setVisibility(View.GONE);
            result.setVisibility(View.VISIBLE);
            result.setText("Hai bạn đã kết nối. Gửi một lời chào nhé!");
            result.setOnClickListener(x -> startActivity(new Intent(requireContext(), ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_NAME, "Nguyễn Hoàng Yến")));
            ((TextView) view.findViewById(R.id.notif_invite_count)).setText("0 lời mời");
        });
        view.findViewById(R.id.notif_later).setOnClickListener(v -> {
            actions.setVisibility(View.GONE);
            result.setVisibility(View.VISIBLE);
            result.setText("Đã chuyển lời mời vào mục Để sau");
        });
    }

    private static String name(String s) {
        return "<font color='" + PRIMARY + "'>" + s + "</font>";
    }

    private static void setHtml(View root, int id, String html) {
        ((TextView) root.findViewById(id)).setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
    }

    private static void hideUnreadDots(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if ("unread".equals(child.getTag())) {
                child.animate().alpha(0f).setDuration(200).start();
            } else if (child instanceof ViewGroup) {
                hideUnreadDots((ViewGroup) child);
            }
        }
    }
}
