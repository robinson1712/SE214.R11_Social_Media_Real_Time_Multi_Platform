package com.example.ui_mobile.fanpage;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.ui_mobile.R;
import com.example.ui_mobile.chat.ChatActivity;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.ui.Anim;
import com.example.ui_mobile.ui.BaseActivity;
import com.example.ui_mobile.ui.PostBinder;
import com.example.ui_mobile.ui.Ui;
import com.google.android.material.button.MaterialButton;

import java.time.LocalTime;

public class FanpageActivity extends BaseActivity {

    private static final String PAGE_NAME = "Gốm & Không Gian Tĩnh Tại";
    private static final String[][] COLLECTIONS = {
            {"Trà cụ mộc", "ceramic"},
            {"Bàn xoay tĩnh", "room"},
            {"Mẻ nung tro", "coffee"},
            {"Men sương mai", "mist"},
    };

    private boolean following = true;
    private int followers = 42500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fanpage);
        padForSystemBars(findViewById(R.id.page_root), true, true);

        renderFollow();
        bindHours();

        LinearLayout collections = findViewById(R.id.page_collections);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String[] c : COLLECTIONS) {
            View item = inflater.inflate(R.layout.item_collection, collections, false);
            item.findViewById(R.id.collection_image).setBackgroundResource(MockData.sceneOf(c[1]));
            ((TextView) item.findViewById(R.id.collection_label)).setText(c[0]);
            item.setOnClickListener(v -> Ui.toast(this, c[0]));
            collections.addView(item);
        }

        LinearLayout posts = findViewById(R.id.page_posts);
        View pinned = PostBinder.addTo(posts, MockData.fanpagePinnedPost());
        // We are already on the page, so the author header should not reopen it.
        pinned.findViewById(R.id.post_avatar).setOnClickListener(null);
        pinned.findViewById(R.id.post_author_block).setOnClickListener(null);

        Ui.singleChoiceChips(findViewById(R.id.page_tabs), 0, index -> { });

        TextView about = findViewById(R.id.page_about);
        findViewById(R.id.page_about_more).setOnClickListener(v -> {
            boolean expanded = about.getMaxLines() == Integer.MAX_VALUE;
            about.setMaxLines(expanded ? 3 : Integer.MAX_VALUE);
            ((TextView) v).setText(expanded ? "Xem chi tiết" : "Thu gọn");
        });

        findViewById(R.id.page_follow).setOnClickListener(v -> {
            following = !following;
            followers += following ? 1 : -1;
            renderFollow();
            Anim.pop(v);
        });
        findViewById(R.id.page_message).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_NAME, PAGE_NAME)));
        findViewById(R.id.page_book).setOnClickListener(v ->
                Ui.toast(this, "Đặt lịch workshop nặn gốm cuối tuần"));
        findViewById(R.id.page_join_group).setOnClickListener(v -> {
            ((MaterialButton) v).setText("Đã gửi");
            v.setEnabled(false);
            Ui.toast(this, "Đã gửi yêu cầu tham gia nhóm");
        });
        findViewById(R.id.page_back).setOnClickListener(v -> finish());
        findViewById(R.id.page_share_top).setOnClickListener(v ->
                Ui.share(this, PAGE_NAME + " trên Aura — @gom.tinhtai.studio"));
    }

    private void renderFollow() {
        MaterialButton follow = findViewById(R.id.page_follow);
        follow.setText(following ? "Đang theo dõi" : "Theo dõi");
        follow.setIconResource(following ? R.drawable.ic_check : R.drawable.ic_add);
        ((TextView) findViewById(R.id.page_stats)).setText("Nghệ thuật & Đời sống  •  "
                + Ui.compact(followers) + " người theo dõi  •  980 đang kết nối");
    }

    private void bindHours() {
        LocalTime now = LocalTime.now();
        boolean open = !now.isBefore(LocalTime.of(8, 30)) && now.isBefore(LocalTime.of(21, 0));
        String status = open
                ? "<font color='#22B07D'>● Đang mở cửa</font>"
                : "<font color='#C62828'>● Đã đóng cửa</font>";
        ((TextView) findViewById(R.id.page_hours)).setText(
                Html.fromHtml(status + "  •  08:30 - 21:00", Html.FROM_HTML_MODE_COMPACT));
    }
}
