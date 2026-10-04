package com.example.ui_mobile.data;

public class Message {
    public final String text;
    public final boolean outgoing;
    public final String time;

    public Message(String text, boolean outgoing, String time) {
        this.text = text;
        this.outgoing = outgoing;
        this.time = time;
    }
}
