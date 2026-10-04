package com.example.ui_mobile.chat;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.Conversation;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.BaseActivity;
import com.example.ui_mobile.ui.Ui;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class ChatListActivity extends BaseActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_UNREAD = 1;
    private static final int FILTER_CLOSE = 2;
    private static final int FILTER_GROUPS = 3;

    private final List<Conversation> conversations = MockData.conversations();
    private LinearLayout container;
    private TextView empty;
    private String query = "";
    private int filter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        padForSystemBars(findViewById(R.id.chat_list_root), true, true);

        container = findViewById(R.id.chat_list_conversations);
        empty = findViewById(R.id.chat_list_empty);

        bindOnline(findViewById(R.id.chat_list_online));

        Ui.singleChoiceChips(findViewById(R.id.chat_list_filters), 0, index -> {
            filter = index;
            renderConversations();
        });

        EditText search = findViewById(R.id.chat_list_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = fold(s.toString());
                renderConversations();
            }
        });

        findViewById(R.id.chat_list_back).setOnClickListener(v -> finish());
        findViewById(R.id.chat_list_tune).setOnClickListener(v -> Ui.toast(this, "Tùy chỉnh hộp thư"));
        findViewById(R.id.chat_list_mark_read).setOnClickListener(v -> {
            for (Conversation c : conversations) c.unread = 0;
            renderConversations();
            Ui.toast(this, "Đã đánh dấu tất cả là đã đọc");
        });
        findViewById(R.id.chat_list_new).setOnClickListener(v -> Ui.toast(this, "Soạn tin nhắn mới"));
        findViewById(R.id.chat_list_greet).setOnClickListener(v -> openChat("Nhóm bạn Đại học"));
        findViewById(R.id.chat_list_later).setOnClickListener(v ->
                findViewById(R.id.chat_list_suggestion).setVisibility(View.GONE));
    }

    private void bindOnline(LinearLayout row) {
        LayoutInflater inflater = LayoutInflater.from(this);

        View mood = inflater.inflate(R.layout.item_online, row, false);
        ImageView moodIcon = new ImageView(this);
        moodIcon.setImageResource(R.drawable.ic_mood);
        moodIcon.setBackgroundResource(R.drawable.bg_circle_lavender);
        moodIcon.setColorFilter(getColor(R.color.primary));
        int pad = Ui.dp(this, 18);
        moodIcon.setPadding(pad, pad, pad, pad);
        ((LinearLayout) mood).removeViewAt(0);
        ((LinearLayout) mood).addView(moodIcon, 0, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        ((TextView) mood.findViewById(R.id.online_name)).setText("Cảm xúc bạn");
        TextView moodStatus = mood.findViewById(R.id.online_status);
        moodStatus.setText("Cập nhật ngay");
        moodStatus.setBackground(null);
        mood.setOnClickListener(v -> Ui.toast(this, "Chia sẻ cảm xúc hôm nay"));
        row.addView(mood);

        for (String[] f : MockData.ONLINE_FRIENDS) {
            View item = inflater.inflate(R.layout.item_online, row, false);
            ((AvatarView) item.findViewById(R.id.online_avatar)).setName(f[0]);
            ((TextView) item.findViewById(R.id.online_name)).setText(f[0]);
            ((TextView) item.findViewById(R.id.online_status)).setText(f[1]);
            item.setOnClickListener(v -> openChat(f[0]));
            row.addView(item);
        }
    }

    private void renderConversations() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int shown = 0;
        for (Conversation c : conversations) {
            if (!matches(c)) continue;
            View item = inflater.inflate(R.layout.item_conversation, container, false);
            bindConversation(item, c);
            container.addView(item);
            shown++;
        }
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matches(Conversation c) {
        if (filter == FILTER_UNREAD && c.unread == 0) return false;
        if (filter == FILTER_GROUPS && !c.group) return false;
        if (filter == FILTER_CLOSE && (c.group || !c.online)) return false;
        return query.isEmpty() || fold(c.name).contains(query);
    }

    private void bindConversation(View v, Conversation c) {
        AvatarView avatar = v.findViewById(R.id.conv_avatar);
        View group = v.findViewById(R.id.conv_group);
        avatar.setVisibility(c.group ? View.GONE : View.VISIBLE);
        group.setVisibility(c.group ? View.VISIBLE : View.GONE);
        avatar.setName(c.name);
        avatar.setOnline(c.online);

        ((TextView) v.findViewById(R.id.conv_name)).setText(c.name);
        TextView members = v.findViewById(R.id.conv_members);
        members.setVisibility(c.group ? View.VISIBLE : View.GONE);
        members.setText(c.members + " tv");

        TextView time = v.findViewById(R.id.conv_time);
        time.setText(c.time);
        time.setTextColor(getColor(c.unread > 0 || c.typing ? R.color.primary : R.color.text_tertiary));

        TextView message = v.findViewById(R.id.conv_message);
        if (c.typing) {
            message.setText("Đang soạn tin nhắn •••");
            message.setTextColor(getColor(R.color.primary));
            message.setTypeface(null, Typeface.ITALIC);
        } else {
            message.setText(c.lastMessage);
            message.setTextColor(getColor(c.unread > 0 ? R.color.text_primary : R.color.text_secondary));
            message.setTypeface(null, c.unread > 0 ? Typeface.BOLD : Typeface.NORMAL);
        }
        message.setCompoundDrawablesRelativeWithIntrinsicBounds(
                c.attachment ? R.drawable.ic_attach : 0, 0, 0, 0);

        TextView unread = v.findViewById(R.id.conv_unread);
        unread.setVisibility(c.unread > 0 ? View.VISIBLE : View.GONE);
        unread.setText(String.valueOf(c.unread));
        v.findViewById(R.id.conv_seen).setVisibility(c.seen && c.unread == 0 ? View.VISIBLE : View.GONE);

        v.setOnClickListener(x -> {
            c.unread = 0;
            openChat(c.name);
        });
    }

    private void openChat(String name) {
        startActivity(new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_NAME, name));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-render so unread badges cleared by opening a chat are reflected.
        renderConversations();
    }

    /** Lower-cases and strips Vietnamese diacritics so "ha" matches "Hà". */
    private static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').trim();
    }
}
