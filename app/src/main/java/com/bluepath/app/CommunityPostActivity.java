package com.bluepath.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bluepath.app.network.ApiModels;
import com.bluepath.app.repository.BluePathRepository;
import com.bumptech.glide.Glide;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunityPostActivity extends AppCompatActivity {
    public static final String EXTRA_CATEGORY = "community_category";

    private final int NAVY = Color.parseColor("#06223F");
    private final int OCEAN = Color.parseColor("#0E7490");
    private final int BG = Color.parseColor("#F2FAFB");
    private final int TEXT = Color.parseColor("#17324D");
    private final int MUTED = Color.parseColor("#64748B");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BluePathRepository repository;
    private EditText titleInput;
    private EditText bodyInput;
    private Button submitButton;
    private Button imageButton;
    private Button removeImageButton;
    private ImageView imagePreview;
    private Uri selectedImageUri;
    private String category;
    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            selectedImageUri = uri;
            imagePreview.setVisibility(View.VISIBLE);
            removeImageButton.setVisibility(View.VISIBLE);
            imageButton.setText("사진 변경");
            Glide.with(this).load(uri).centerCrop().into(imagePreview);
        });

        repository = new BluePathRepository(this);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (!"question".equals(category)) category = "free";

        setContentView(buildScreen());
        titleInput.requestFocus();
        titleInput.postDelayed(() -> {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT);
        }, 180L);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackgroundColor(NAVY);

        Button back = secondaryButton("‹");
        back.setTextSize(28);
        back.setContentDescription("커뮤니티로 돌아가기");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("question".equals(category) ? "질문 게시판 글쓰기" : "자유 게시판 글쓰기", 20, Color.WHITE, true);
        TextView subtitle = text("제목과 본문, 사진을 함께 게시할 수 있습니다", 11, Color.parseColor("#C9FFFF"), false);
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(18));

        form.addView(text("question".equals(category) ? "질문 게시판" : "자유 게시판", 13, OCEAN, true));

        TextView titleLabel = text("제목", 13, TEXT, true);
        LinearLayout.LayoutParams titleLabelParams = new LinearLayout.LayoutParams(-1, -2);
        titleLabelParams.setMargins(0, dp(16), 0, dp(7));
        form.addView(titleLabel, titleLabelParams);

        titleInput = input("제목을 입력하세요");
        titleInput.setSingleLine(true);
        configureKoreanTextInput(titleInput, false);
        titleInput.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
        form.addView(titleInput, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView bodyLabel = text("내용", 13, TEXT, true);
        LinearLayout.LayoutParams bodyLabelParams = new LinearLayout.LayoutParams(-1, -2);
        bodyLabelParams.setMargins(0, dp(18), 0, dp(7));
        form.addView(bodyLabel, bodyLabelParams);

        bodyInput = input("내용을 입력하세요");
        bodyInput.setSingleLine(false);
        configureKoreanTextInput(bodyInput, true);
        bodyInput.setGravity(Gravity.TOP | Gravity.START);
        bodyInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        bodyInput.setMinLines(8);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        form.addView(bodyInput, bodyParams);

        TextView imageLabel = text("사진 첨부 (선택)", 13, TEXT, true);
        LinearLayout.LayoutParams imageLabelParams = new LinearLayout.LayoutParams(-1, -2);
        imageLabelParams.setMargins(0, dp(14), 0, dp(7));
        form.addView(imageLabel, imageLabelParams);

        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePreview.setVisibility(View.GONE);
        imagePreview.setBackground(rounded(Color.WHITE, Color.parseColor("#B8D7DF"), 12));
        form.addView(imagePreview, new LinearLayout.LayoutParams(-1, dp(180)));

        LinearLayout imageActions = new LinearLayout(this);
        imageActions.setOrientation(LinearLayout.HORIZONTAL);
        imageActions.setGravity(Gravity.CENTER_VERTICAL);
        imageButton = outlineButton("사진 선택");
        imageButton.setOnClickListener(v -> imagePicker.launch("image/*"));
        imageActions.addView(imageButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        removeImageButton = outlineButton("첨부 해제");
        removeImageButton.setVisibility(View.GONE);
        removeImageButton.setOnClickListener(v -> clearSelectedImage());
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        removeParams.setMargins(dp(8), 0, 0, 0);
        imageActions.addView(removeImageButton, removeParams);
        LinearLayout.LayoutParams imageActionsParams = new LinearLayout.LayoutParams(-1, -2);
        imageActionsParams.setMargins(0, dp(8), 0, 0);
        form.addView(imageActions, imageActionsParams);

        TextView guide = text("제목과 내용은 각각 2자 이상, 사진은 JPEG·PNG·WebP 8MB 이하만 가능합니다.", 12, MUTED, false);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(-1, -2);
        guideParams.setMargins(0, dp(10), 0, dp(12));
        form.addView(guide, guideParams);

        submitButton = primaryButton("등록하기");
        submitButton.setOnClickListener(v -> submitPost());
        form.addView(submitButton, new LinearLayout.LayoutParams(-1, dp(54)));

        root.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void clearSelectedImage() {
        selectedImageUri = null;
        imagePreview.setImageDrawable(null);
        imagePreview.setVisibility(View.GONE);
        removeImageButton.setVisibility(View.GONE);
        imageButton.setText("사진 선택");
    }

    private void submitPost() {
        String title = titleInput.getText().toString().trim();
        String body = bodyInput.getText().toString().trim();
        if (title.length() < 2 || body.length() < 2) {
            Toast.makeText(this, "제목과 내용을 2자 이상 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitting(true);
        executor.execute(() -> {
            try {
                ApiModels.CommunityPostDto post = repository.createCommunityPost(category, title, body);
                String imageWarning = "";
                if (selectedImageUri != null) {
                    try {
                        repository.uploadCommunityPostImage(post.id, selectedImageUri);
                    } catch (Exception imageError) {
                        imageWarning = " 게시글은 등록됐지만 사진 첨부에 실패했습니다: " + safeMessage(imageError);
                    }
                }
                String finalImageWarning = imageWarning;
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    Toast.makeText(this, finalImageWarning.isEmpty() ? "게시글을 등록했습니다." : finalImageWarning,
                            finalImageWarning.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setSubmitting(false);
                    Toast.makeText(this, "글 작성 실패: " + safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setSubmitting(boolean submitting) {
        titleInput.setEnabled(!submitting);
        bodyInput.setEnabled(!submitting);
        imageButton.setEnabled(!submitting);
        removeImageButton.setEnabled(!submitting);
        submitButton.setEnabled(!submitting);
        submitButton.setText(submitting ? "등록 중…" : "등록하기");
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.WHITE, Color.parseColor("#B8D7DF"), 14));
        configureKoreanTextInput(input, false);
        return input;
    }

    private void configureKoreanTextInput(EditText input, boolean multiLine) {
        int type = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        if (multiLine) type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
        input.setInputType(type);
        input.setTextLocale(Locale.KOREAN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            input.setImeHintLocales(new LocaleList(Locale.KOREAN, Locale.getDefault()));
        }
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | (multiLine ? EditorInfo.IME_ACTION_NONE : EditorInfo.IME_ACTION_DONE));
        input.setHorizontallyScrolling(!multiLine);
        input.setFocusableInTouchMode(true);
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(OCEAN, OCEAN, 14));
        return button;
    }

    private Button outlineButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(NAVY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(Color.WHITE, Color.parseColor("#B8D7DF"), 12));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(rounded(Color.TRANSPARENT, Color.parseColor("#66FFFFFF"), 14));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
