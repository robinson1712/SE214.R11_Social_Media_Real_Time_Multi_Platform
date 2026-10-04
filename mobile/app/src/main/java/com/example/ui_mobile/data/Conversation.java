package com.example.ui_mobile.data;

public class Conversation {
    public final String name;
    public final String lastMessage;
    public final String time;
    public int unread;
    public boolean online;
    public boolean group;
    public int members;
    public boolean typing;
    public boolean loved;
    public boolean seen;
    public boolean attachment;

    public Conversation(String name, String lastMessage, String time) {
        this.name = name;
        this.lastMessage = lastMessage;
        this.time = time;
    }

    public Conversation unread(int n) { unread = n; return this; }
    public Conversation online() { online = true; return this; }
    public Conversation group(int memberCount) { group = true; members = memberCount; return this; }
    public Conversation typing() { typing = true; return this; }
    public Conversation loved() { loved = true; return this; }
    public Conversation seen() { seen = true; return this; }
    public Conversation attachment() { attachment = true; return this; }
}
