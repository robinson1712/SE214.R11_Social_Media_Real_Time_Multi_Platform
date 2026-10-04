package com.example.ui_mobile.api;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionStore {

    private static final String PREFS = "session";
    private static final String KEY_ACCESS = "accessToken";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(AuthData data) {
        prefs.edit()
                .putString(KEY_ACCESS, data.accessToken)
                .putString("refreshToken", data.refreshToken)
                .putString("accountId", data.accountId)
                .putString("email", data.email)
                .apply();
    }

    public String accessToken() {
        return prefs.getString(KEY_ACCESS, null);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
