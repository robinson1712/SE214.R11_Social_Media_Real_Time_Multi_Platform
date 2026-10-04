package com.example.ui_mobile.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.ui_mobile.MainActivity;
import com.example.ui_mobile.R;
import com.example.ui_mobile.api.ApiClient;
import com.example.ui_mobile.api.ApiResponse;
import com.example.ui_mobile.api.AuthData;
import com.example.ui_mobile.api.RegisterBody;
import com.example.ui_mobile.api.SessionStore;
import com.example.ui_mobile.ui.BaseActivity;
import com.example.ui_mobile.ui.Ui;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends BaseActivity {

    private static final String[] STRENGTH_LABELS = {"Chưa nhập", "Yếu", "Trung bình", "Khá", "Mạnh"};
    private static final int[] STRENGTH_COLORS = {0, 0xFFE57373, 0xFFF2B35C, 0xFF7C83E0, 0xFF22B07D};

    private boolean passwordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        padForSystemBars(findViewById(R.id.register_root), true, true);

        EditText name = findViewById(R.id.register_name);
        EditText email = findViewById(R.id.register_email);
        EditText password = findViewById(R.id.register_password);
        EditText confirm = findViewById(R.id.register_confirm);
        CheckBox terms = findViewById(R.id.register_terms);
        ImageView toggle = findViewById(R.id.register_toggle_password);

        terms.setText(Html.fromHtml("Tôi đồng ý với <font color='#4648B0'>Điều khoản dịch vụ</font> và "
                + "<font color='#4648B0'>Chính sách quyền riêng tư</font> của Aura.", Html.FROM_HTML_MODE_COMPACT));

        toggle.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            LoginActivity.togglePassword(password, toggle, passwordVisible);
        });

        password.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                renderStrength(strengthOf(s.toString()));
            }
        });

        findViewById(R.id.register_submit).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) {
                name.setError("Vui lòng nhập họ tên");
                return;
            }
            if (email.getText().toString().trim().isEmpty()) {
                email.setError("Vui lòng nhập email hoặc số điện thoại");
                return;
            }
            if (password.getText().length() < 8) {
                password.setError("Mật khẩu tối thiểu 8 ký tự");
                return;
            }
            if (!password.getText().toString().equals(confirm.getText().toString())) {
                confirm.setError("Mật khẩu xác nhận không khớp");
                return;
            }
            if (!terms.isChecked()) {
                Ui.toast(this, "Bạn cần đồng ý với Điều khoản dịch vụ");
                return;
            }
            submitRegister(name.getText().toString().trim(),
                    email.getText().toString().trim(),
                    password.getText().toString(),
                    v);
        });

        findViewById(R.id.register_google).setOnClickListener(v -> Ui.toast(this, "Đăng ký với Google"));
        findViewById(R.id.register_apple).setOnClickListener(v -> Ui.toast(this, "Đăng ký với Apple"));
        findViewById(R.id.register_go_login).setOnClickListener(v -> finish());
    }

    private void submitRegister(String fullName, String email, String password, View submit) {
        submit.setEnabled(false);
        ApiClient.service(this).register(new RegisterBody(email, password, fullName))
                .enqueue(new Callback<ApiResponse<AuthData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<AuthData>> call, Response<ApiResponse<AuthData>> response) {
                        runOnUiThread(() -> {
                            submit.setEnabled(true);
                            if (isFinishing()) return;
                            if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                                new SessionStore(RegisterActivity.this).save(response.body().data);
                                Ui.toast(RegisterActivity.this, "Chào mừng bạn đến với Aura 🌿");
                                startActivity(new Intent(RegisterActivity.this, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                            } else {
                                Ui.toast(RegisterActivity.this, ApiClient.errorMessage(response));
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<AuthData>> call, Throwable t) {
                        runOnUiThread(() -> {
                            submit.setEnabled(true);
                            Ui.toast(RegisterActivity.this, "Không kết nối được máy chủ");
                        });
                    }
                });
    }

    private static int strengthOf(String pw) {
        if (pw.isEmpty()) return 0;
        int score = 0;
        if (pw.length() >= 8) score++;
        if (pw.matches(".*[A-Z].*") && pw.matches(".*[a-z].*")) score++;
        if (pw.matches(".*\\d.*")) score++;
        if (pw.matches(".*[^A-Za-z0-9].*")) score++;
        return Math.max(1, score);
    }

    private void renderStrength(int level) {
        ViewGroup bars = findViewById(R.id.register_strength_bars);
        for (int i = 0; i < bars.getChildCount(); i++) {
            bars.getChildAt(i).getBackground().mutate().setTint(
                    i < level ? STRENGTH_COLORS[level] : getColor(R.color.surface_alt));
        }
        TextView label = findViewById(R.id.register_strength_label);
        label.setText("Độ mạnh: " + STRENGTH_LABELS[level]);
    }
}
