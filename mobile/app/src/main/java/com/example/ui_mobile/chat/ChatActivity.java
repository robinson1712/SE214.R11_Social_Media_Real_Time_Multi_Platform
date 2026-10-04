package com.example.ui_mobile.chat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.Message;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.BaseActivity;
import com.example.ui_mobile.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One-to-one conversation. Replies are simulated locally; swap {@link #simulateReply()} for the
 * chat-service WebSocket/STOMP subscription when wiring the backend.
 */
public class ChatActivity extends BaseActivity {

    public static final String EXTRA_NAME = "name";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MessageAdapter adapter;
    private RecyclerView list;
    private TextView typing;
    private String partner;
    private int replyIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        padForSystemBars(findViewById(R.id.chat_root), true, true);

        partner = getIntent().getStringExtra(EXTRA_NAME);
        if (partner == null) partner = "Lê Thu Hà";

        ((AvatarView) findViewById(R.id.chat_avatar)).setName(partner);
        ((TextView) findViewById(R.id.chat_name)).setText(partner);
        typing = findViewById(R.id.chat_typing);
        typing.setText(partner + " đang soạn tin •••");

        list = findViewById(R.id.chat_messages);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        adapter = new MessageAdapter(MockData.messagesWith(partner), partner);
        list.setAdapter(adapter);

        EditText input = findViewById(R.id.chat_input);
        findViewById(R.id.chat_send).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            append(new Message(text, true, now()));
            simulateReply();
        });

        findViewById(R.id.chat_back).setOnClickListener(v -> finish());
        findViewById(R.id.chat_call).setOnClickListener(v -> Ui.toast(this, "Đang gọi " + partner + "..."));
        findViewById(R.id.chat_more).setOnClickListener(v -> Ui.toast(this, "Tùy chọn cuộc trò chuyện"));
        findViewById(R.id.chat_attach).setOnClickListener(v -> Ui.toast(this, "Gửi ảnh, file hoặc khoảnh khắc"));
    }

    private void append(Message m) {
        adapter.add(m);
        list.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void simulateReply() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> typing.setVisibility(View.VISIBLE), 600);
        handler.postDelayed(() -> {
            typing.setVisibility(View.GONE);
            String reply = MockData.AUTO_REPLIES[replyIndex++ % MockData.AUTO_REPLIES.length];
            append(new Message(reply, false, now()));
        }, 2200);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
