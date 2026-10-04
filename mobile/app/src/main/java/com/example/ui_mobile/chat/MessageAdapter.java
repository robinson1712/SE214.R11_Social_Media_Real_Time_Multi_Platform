package com.example.ui_mobile.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.Message;
import com.example.ui_mobile.ui.AvatarView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {

    private static final int TYPE_IN = 0;
    private static final int TYPE_OUT = 1;

    private final List<Message> messages;
    private final String partnerName;

    public MessageAdapter(List<Message> messages, String partnerName) {
        this.messages = messages;
        this.partnerName = partnerName;
    }

    public void add(Message m) {
        messages.add(m);
        notifyItemInserted(messages.size() - 1);
        // The previous incoming bubble may lose its avatar (only the last in a run shows it).
        if (messages.size() > 1) notifyItemChanged(messages.size() - 2);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).outgoing ? TYPE_OUT : TYPE_IN;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_OUT ? R.layout.item_message_out : R.layout.item_message_in;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Message m = messages.get(position);
        h.text.setText(m.text);

        // Group consecutive bubbles from the same side: time + avatar only on the last one.
        boolean lastOfRun = position == messages.size() - 1
                || messages.get(position + 1).outgoing != m.outgoing;
        h.time.setText(m.time);
        h.time.setVisibility(lastOfRun ? View.VISIBLE : View.GONE);
        if (h.avatar != null) {
            h.avatar.setName(partnerName);
            h.avatar.setVisibility(lastOfRun ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView time;
        final AvatarView avatar;

        Holder(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.msg_text);
            time = v.findViewById(R.id.msg_time);
            avatar = v.findViewById(R.id.msg_avatar);
        }
    }
}
