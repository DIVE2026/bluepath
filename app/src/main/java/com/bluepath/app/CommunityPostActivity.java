package com.bluepath.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bluepath.app.repository.BluePathRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunityPostActivity extends AppCompatActivity {
    public static final String EXTRA_CATEGORY = "community_category";
    public static final String EXTRA_POST_ID = "community_post_id";
    public static final String EXTRA_POST_TITLE = "community_post_title";
    public static final String EXTRA_POST_BODY = "community_post_body";
    public static final String EXTRA_POST_TAGS = "community_post_tags";
    public static final String RICH_BODY_MARKER = "<!--bluepath-rich-v1-->";
    private static final String[] FREE_TAGS = {"후기", "정보공유", "진로고민", "자격증·시험", "모임·번개"};
    private static final String[] QUESTION_TAGS = {"진로고민", "자격증·시험", "학습자료", "입시", "현직에게"};

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
    private TextView headerTitle;
    private Button freeCategoryButton;
    private Button questionCategoryButton;
    private EditText tagSearchInput;
    private LinearLayout tagSuggestionRow;
    private final Set<String> selectedTags = new LinkedHashSet<>();
    private String category;
    private String postId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        repository = new BluePathRepository(this);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (!"question".equals(category)) category = "free";
        postId = clean(getIntent().getStringExtra(EXTRA_POST_ID));

        setContentView(buildScreen());
        loadDraftForEditing();
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

        boolean editing = !postId.isEmpty();
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        String boardName = "question".equals(category) ? "질문 게시판" : "자유 게시판";
        headerTitle = text(boardName + (editing ? " 글 수정" : " 글쓰기"), 20, Color.WHITE, true);
        TextView subtitle = text(editing ? "게시글의 제목과 내용을 수정합니다" : "파일 링크와 서식을 포함해 새 글을 작성합니다", 11, Color.parseColor("#C9FFFF"), false);
        heading.addView(headerTitle);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(18));

        if (editing) {
            form.addView(text(boardName, 13, OCEAN, true));
        } else {
            form.addView(buildCategorySelector());
        }

        TextView titleLabel = text("제목", 13, TEXT, true);
        LinearLayout.LayoutParams titleLabelParams = new LinearLayout.LayoutParams(-1, -2);
        titleLabelParams.setMargins(0, dp(16), 0, dp(7));
        form.addView(titleLabel, titleLabelParams);

        titleInput = input("제목을 입력하세요");
        titleInput.setSingleLine(true);
        form.addView(titleInput, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView tagLabel = text("태그 · 최대 5개", 13, TEXT, true);
        LinearLayout.LayoutParams tagLabelParams = new LinearLayout.LayoutParams(-1, -2);
        tagLabelParams.setMargins(0, dp(16), 0, dp(7));
        form.addView(tagLabel, tagLabelParams);

        tagSearchInput = input("#을 입력해 태그 검색");
        tagSearchInput.setSingleLine(true);
        form.addView(tagSearchInput, new LinearLayout.LayoutParams(-1, dp(48)));

        HorizontalScrollView tagScroll = new HorizontalScrollView(this);
        tagScroll.setHorizontalScrollBarEnabled(false);
        tagSuggestionRow = new LinearLayout(this);
        tagSuggestionRow.setOrientation(LinearLayout.HORIZONTAL);
        tagSuggestionRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tagScroll.addView(tagSuggestionRow, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams tagScrollParams = new LinearLayout.LayoutParams(-1, dp(42));
        tagScrollParams.setMargins(0, dp(6), 0, 0);
        form.addView(tagScroll, tagScrollParams);
        refreshTagSuggestions();
        tagSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) { refreshTagSuggestions(); }
        });

        TextView bodyLabel = text("내용", 13, TEXT, true);
        LinearLayout.LayoutParams bodyLabelParams = new LinearLayout.LayoutParams(-1, -2);
        bodyLabelParams.setMargins(0, dp(18), 0, dp(7));
        form.addView(bodyLabel, bodyLabelParams);

        bodyInput = input("내용을 입력하세요");
        bodyInput.setSingleLine(false);
        bodyInput.setGravity(Gravity.TOP | Gravity.START);
        bodyInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        bodyInput.setMinLines(10);

        form.addView(buildEditorToolbar());
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.setMargins(0, dp(8), 0, 0);
        form.addView(bodyInput, bodyParams);

        TextView guide = text("글꼴·크기·강조·정렬·목록·링크·국기를 지원합니다. 파일은 공개 URL 링크로 추가할 수 있습니다.", 12, MUTED, false);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(-1, -2);
        guideParams.setMargins(0, dp(10), 0, dp(12));
        form.addView(guide, guideParams);

        submitButton = primaryButton(editing ? "수정 저장" : "등록하기");
        submitButton.setOnClickListener(v -> submitPost());
        form.addView(submitButton, new LinearLayout.LayoutParams(-1, dp(54)));

        root.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private View buildCategorySelector() {
        LinearLayout segment = new LinearLayout(this);
        segment.setOrientation(LinearLayout.HORIZONTAL);
        segment.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E3EEF1"));
        bg.setCornerRadius(dp(22));
        segment.setBackground(bg);
        segment.setPadding(dp(4), dp(4), dp(4), dp(4));

        freeCategoryButton = new Button(this);
        questionCategoryButton = new Button(this);
        setupCategoryButton(freeCategoryButton, "자유 게시판", "free");
        setupCategoryButton(questionCategoryButton, "질문 게시판", "question");
        segment.addView(freeCategoryButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        segment.addView(questionCategoryButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        refreshCategoryButtons();
        return segment;
    }

    private void setupCategoryButton(Button button, String labelText, String value) {
        button.setText(labelText);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setOnClickListener(v -> {
            category = value;
            retainTagsForCurrentCategory();
            refreshCategoryButtons();
            refreshTagSuggestions();
            if (headerTitle != null) {
                headerTitle.setText(("question".equals(category) ? "질문 게시판" : "자유 게시판") + " 글쓰기");
            }
        });
    }

    private void refreshCategoryButtons() {
        styleCategoryButton(freeCategoryButton, "free".equals(category));
        styleCategoryButton(questionCategoryButton, "question".equals(category));
    }

    private void styleCategoryButton(Button button, boolean selected) {
        button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setTextColor(selected ? NAVY : MUTED);
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(18));
            button.setBackground(bg);
            button.setElevation(dp(1));
        } else {
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setElevation(0);
        }
    }

    private View buildEditorToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setBackground(rounded(Color.WHITE, Color.parseColor("#B8D7DF"), 12));
        toolbar.setPadding(dp(8), dp(7), dp(8), dp(7));

        LinearLayout selectors = new LinearLayout(this);
        selectors.setOrientation(LinearLayout.HORIZONTAL);
        selectors.setGravity(Gravity.CENTER_VERTICAL);

        Spinner fontSpinner = editorSpinner(new String[]{"글꼴", "고딕", "명조", "고정폭"});
        fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) return;
                String family = position == 2 ? "serif" : position == 3 ? "monospace" : "sans-serif";
                applyCharacterSpan(new TypefaceSpan(family));
                fontSpinner.setSelection(0, false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        selectors.addView(fontSpinner, new LinearLayout.LayoutParams(0, dp(42), 1));

        Spinner sizeSpinner = editorSpinner(new String[]{"크기", "12", "14", "16", "18", "22", "28"});
        sizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) return;
                int size = Integer.parseInt(parent.getItemAtPosition(position).toString());
                applyCharacterSpan(new AbsoluteSizeSpan(size, true));
                sizeSpinner.setSelection(0, false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        sizeParams.setMargins(dp(7), 0, 0, 0);
        selectors.addView(sizeSpinner, sizeParams);
        toolbar.addView(selectors);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        addToolButton(buttons, "B", "굵게", v -> applyCharacterSpan(new StyleSpan(Typeface.BOLD)));
        addToolButton(buttons, "I", "기울임", v -> applyCharacterSpan(new StyleSpan(Typeface.ITALIC)));
        addToolButton(buttons, "U", "밑줄", v -> applyCharacterSpan(new UnderlineSpan()));
        addToolButton(buttons, "S", "취소선", v -> applyCharacterSpan(new StrikethroughSpan()));
        addToolButton(buttons, "A", "글자색", this::showColorPalette);
        addToolButton(buttons, "≡", "왼쪽 정렬", v -> applyAlignment(LayoutAlignment.LEFT));
        addToolButton(buttons, "≣", "가운데 정렬", v -> applyAlignment(LayoutAlignment.CENTER));
        addToolButton(buttons, "≡›", "오른쪽 정렬", v -> applyAlignment(LayoutAlignment.RIGHT));
        addToolButton(buttons, "•", "글머리표", v -> prefixSelectedLines("• "));
        addToolButton(buttons, "1.", "번호 목록", v -> numberSelectedLines());
        addToolButton(buttons, "❝", "인용", v -> prefixSelectedLines("> "));
        addToolButton(buttons, "🔗", "링크", v -> showLinkDialog(false));
        addToolButton(buttons, "📎", "파일 링크", v -> showLinkDialog(true));
        addToolButton(buttons, "🏳", "국기", this::showFlagPalette);
        addToolButton(buttons, "Tx", "서식 지우기", v -> clearFormatting());

        scroll.addView(buttons);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(48));
        scrollParams.setMargins(0, dp(6), 0, 0);
        toolbar.addView(scroll, scrollParams);
        return toolbar;
    }

    private Spinner editorSpinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(rounded(Color.parseColor("#F8FCFD"), Color.parseColor("#C7DDE3"), 10));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        return spinner;
    }

    private void addToolButton(LinearLayout row, String label, String description, View.OnClickListener listener) {
        Button button = secondaryEditorButton(label);
        button.setContentDescription(description);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(label.length() > 1 ? 52 : 44), dp(42));
        params.setMargins(0, 0, dp(5), 0);
        row.addView(button, params);
    }

    private void loadDraftForEditing() {
        if (postId.isEmpty()) return;
        titleInput.setText(clean(getIntent().getStringExtra(EXTRA_POST_TITLE)));
        ArrayList<String> tags = getIntent().getStringArrayListExtra(EXTRA_POST_TAGS);
        if (tags != null) selectedTags.addAll(tags);
        retainTagsForCurrentCategory();
        refreshTagSuggestions();
        String body = clean(getIntent().getStringExtra(EXTRA_POST_BODY));
        if (body.startsWith(RICH_BODY_MARKER)) {
            Spanned rich = Html.fromHtml(body.substring(RICH_BODY_MARKER.length()), Html.FROM_HTML_MODE_LEGACY);
            bodyInput.setText(rich);
        } else {
            bodyInput.setText(body);
        }
        bodyInput.setSelection(bodyInput.length());
    }

    private void submitPost() {
        String title = titleInput.getText().toString().trim();
        String plainBody = bodyInput.getText().toString().trim();
        if (title.length() < 2 || plainBody.length() < 2) {
            Toast.makeText(this, "제목과 내용을 2자 이상 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        String htmlBody = RICH_BODY_MARKER + Html.toHtml(bodyInput.getText(), Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL);
        if (htmlBody.length() > 8000) {
            Toast.makeText(this, "서식을 포함한 본문은 8,000자 이하여야 합니다.", Toast.LENGTH_LONG).show();
            return;
        }

        setSubmitting(true);
        List<String> tags = new ArrayList<>(selectedTags);
        executor.execute(() -> {
            try {
                if (postId.isEmpty()) repository.createCommunityPost(category, title, htmlBody, tags);
                else repository.updateCommunityPost(postId, title, htmlBody, tags);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    Toast.makeText(this, postId.isEmpty() ? "게시글을 등록했습니다." : "게시글을 수정했습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setSubmitting(false);
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                    Toast.makeText(this, (postId.isEmpty() ? "글 작성 실패: " : "글 수정 실패: ") + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setSubmitting(boolean submitting) {
        titleInput.setEnabled(!submitting);
        bodyInput.setEnabled(!submitting);
        submitButton.setEnabled(!submitting);
        submitButton.setText(submitting ? "저장 중…" : postId.isEmpty() ? "등록하기" : "수정 저장");
    }

    private void retainTagsForCurrentCategory() {
        Set<String> available = new LinkedHashSet<>();
        for (String tag : availableTags()) available.add(tag);
        selectedTags.retainAll(available);
    }

    private String[] availableTags() {
        return "question".equals(category) ? QUESTION_TAGS : FREE_TAGS;
    }

    private void refreshTagSuggestions() {
        if (tagSuggestionRow == null) return;
        tagSuggestionRow.removeAllViews();
        String query = tagSearchInput == null ? "" : tagSearchInput.getText().toString().trim();
        while (query.startsWith("#")) query = query.substring(1);
        String normalizedQuery = query.trim();
        for (String tag : availableTags()) {
            if (!normalizedQuery.isEmpty() && !tag.contains(normalizedQuery)) continue;
            tagSuggestionRow.addView(tagChoiceChip(tag, selectedTags.contains(tag)));
        }
        if (tagSuggestionRow.getChildCount() == 0) {
            tagSuggestionRow.addView(text("선택할 수 있는 태그가 없습니다", 11, MUTED, false));
        }
    }

    private TextView tagChoiceChip(String tag, boolean selected) {
        TextView chip = text("#" + tag, 11, tagTextColor(tag), selected);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(tagBackgroundColor(tag));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(selected ? 2 : 1), selected ? OCEAN : Color.parseColor("#D2E1E5"));
        chip.setBackground(background);
        chip.setContentDescription((selected ? "태그 선택 해제 " : "태그 선택 ") + tag);
        chip.setOnClickListener(v -> {
            if (selectedTags.contains(tag)) {
                selectedTags.remove(tag);
            } else {
                if (selectedTags.size() >= 5) {
                    toast("태그는 최대 5개까지 선택할 수 있습니다.");
                    return;
                }
                selectedTags.add(tag);
            }
            if (tagSearchInput != null && !tagSearchInput.getText().toString().isEmpty()) {
                tagSearchInput.setText("");
            } else {
                refreshTagSuggestions();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
        params.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private int tagBackgroundColor(String tag) {
        switch (tag) {
            case "후기": return Color.parseColor("#DDF6ED");
            case "정보공유": return Color.parseColor("#DDF4F8");
            case "진로고민": return Color.parseColor("#FFF1C9");
            case "자격증·시험": return Color.parseColor("#E9E7FA");
            case "모임·번개": return Color.parseColor("#FCE4EC");
            case "학습자료": return Color.parseColor("#E1EEFF");
            case "입시": return Color.parseColor("#FFE8D6");
            case "현직에게": return Color.parseColor("#DDF3F0");
            default: return Color.parseColor("#EEF4F6");
        }
    }

    private int tagTextColor(String tag) {
        switch (tag) {
            case "후기": return Color.parseColor("#16765D");
            case "정보공유": return Color.parseColor("#0E7490");
            case "진로고민": return Color.parseColor("#8A5B00");
            case "자격증·시험": return Color.parseColor("#5952A3");
            case "모임·번개": return Color.parseColor("#A23A62");
            case "학습자료": return Color.parseColor("#2E66A3");
            case "입시": return Color.parseColor("#A35423");
            case "현직에게": return Color.parseColor("#177369");
            default: return TEXT;
        }
    }

    private void applyCharacterSpan(Object span) {
        int[] range = selectionOrCurrentParagraph();
        if (range[0] == range[1]) {
            toast("서식을 적용할 텍스트를 먼저 선택해 주세요.");
            return;
        }
        bodyInput.getText().setSpan(span, range[0], range[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        bodyInput.setSelection(range[0], range[1]);
    }

    private void applyAlignment(LayoutAlignment alignment) {
        int[] range = paragraphRange();
        if (range[0] == range[1]) return;
        android.text.Layout.Alignment value = android.text.Layout.Alignment.ALIGN_NORMAL;
        if (alignment == LayoutAlignment.CENTER) value = android.text.Layout.Alignment.ALIGN_CENTER;
        if (alignment == LayoutAlignment.RIGHT) value = android.text.Layout.Alignment.ALIGN_OPPOSITE;
        bodyInput.getText().setSpan(new AlignmentSpan.Standard(value), range[0], range[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        bodyInput.setSelection(range[0], range[1]);
    }

    private int[] selectionOrCurrentParagraph() {
        int start = Math.max(0, bodyInput.getSelectionStart());
        int end = Math.max(0, bodyInput.getSelectionEnd());
        if (start > end) { int swap = start; start = end; end = swap; }
        if (start != end) return new int[]{start, end};
        return paragraphRange();
    }

    private int[] paragraphRange() {
        Editable editable = bodyInput.getText();
        int length = editable.length();
        if (length == 0) return new int[]{0, 0};
        int cursor = Math.max(0, Math.min(bodyInput.getSelectionStart(), length));
        int start = cursor;
        while (start > 0 && editable.charAt(start - 1) != '\n') start--;
        int end = cursor;
        while (end < length && editable.charAt(end) != '\n') end++;
        return new int[]{start, end};
    }

    private void prefixSelectedLines(String prefix) {
        Editable editable = bodyInput.getText();
        int[] range = selectionOrCurrentParagraph();
        int start = range[0];
        int end = range[1];
        if (start == end) return;
        String selected = editable.subSequence(start, end).toString();
        String[] lines = selected.split("\\n", -1);
        StringBuilder replaced = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) replaced.append('\n');
            replaced.append(prefix).append(lines[i]);
        }
        editable.replace(start, end, replaced);
        bodyInput.setSelection(start, start + replaced.length());
    }

    private void numberSelectedLines() {
        Editable editable = bodyInput.getText();
        int[] range = selectionOrCurrentParagraph();
        int start = range[0];
        int end = range[1];
        if (start == end) return;
        String[] lines = editable.subSequence(start, end).toString().split("\\n", -1);
        StringBuilder replaced = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) replaced.append('\n');
            replaced.append(i + 1).append(". ").append(lines[i]);
        }
        editable.replace(start, end, replaced);
        bodyInput.setSelection(start, start + replaced.length());
    }

    private void clearFormatting() {
        Editable editable = bodyInput.getText();
        int[] range = selectionOrCurrentParagraph();
        if (range[0] == range[1]) return;
        Object[] spans = editable.getSpans(range[0], range[1], Object.class);
        for (Object span : spans) {
            if (span instanceof StyleSpan || span instanceof UnderlineSpan || span instanceof StrikethroughSpan
                    || span instanceof ForegroundColorSpan || span instanceof TypefaceSpan || span instanceof AbsoluteSizeSpan
                    || span instanceof AlignmentSpan || span instanceof URLSpan) {
                editable.removeSpan(span);
            }
        }
        bodyInput.setSelection(range[0], range[1]);
    }

    private void showColorPalette(View anchor) {
        int[] colors = {
                TEXT, Color.BLACK, Color.parseColor("#B42318"), Color.parseColor("#E11D48"),
                Color.parseColor("#D97706"), Color.parseColor("#047857"), OCEAN, Color.parseColor("#4F46E5")
        };
        LinearLayout palette = popupRow();
        PopupWindow popup = popupWindow(palette, dp(8 + colors.length * 42), dp(54));
        for (int color : colors) {
            TextView chip = new TextView(this);
            chip.setText("●");
            chip.setTextSize(24);
            chip.setTextColor(color);
            chip.setGravity(Gravity.CENTER);
            chip.setContentDescription(String.format(Locale.ROOT, "글자색 #%06X", 0xFFFFFF & color));
            chip.setOnClickListener(v -> {
                applyCharacterSpan(new ForegroundColorSpan(color));
                popup.dismiss();
            });
            palette.addView(chip, new LinearLayout.LayoutParams(dp(42), dp(48)));
        }
        showPopupAbove(popup, anchor, colors.length * 42 + 8);
    }

    private void showFlagPalette(View anchor) {
        String[] flags = {"🇰🇷", "🇺🇸", "🇯🇵", "🇨🇳", "🇬🇧", "🇫🇷", "🇩🇪", "🇺🇳"};
        LinearLayout palette = popupRow();
        PopupWindow popup = popupWindow(palette, dp(8 + flags.length * 42), dp(54));
        for (String flag : flags) {
            TextView chip = new TextView(this);
            chip.setText(flag);
            chip.setTextSize(22);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> {
                insertAtCursor(flag);
                popup.dismiss();
            });
            palette.addView(chip, new LinearLayout.LayoutParams(dp(42), dp(48)));
        }
        showPopupAbove(popup, anchor, flags.length * 42 + 8);
    }

    private void showLinkDialog(boolean fileLink) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText label = input(fileLink ? "표시할 파일 이름" : "표시할 링크 문구");
        label.setSingleLine(true);
        EditText url = input(fileLink ? "공개 파일 URL https://…" : "웹 주소 https://…");
        url.setSingleLine(true);
        fields.addView(label, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, dp(52));
        urlParams.setMargins(0, dp(8), 0, 0);
        fields.addView(url, urlParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(fileLink ? "파일 링크 추가" : "링크 추가")
                .setMessage(fileLink ? "사진·문서·영상·압축파일 등 모든 종류의 공개 URL을 추가할 수 있습니다." : null)
                .setView(fields)
                .setNegativeButton("취소", null)
                .setPositiveButton("추가", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = normalizeUrl(url.getText().toString());
            if (value.isEmpty()) {
                url.setError("http 또는 https 주소를 입력해 주세요.");
                return;
            }
            String display = label.getText().toString().trim();
            if (display.isEmpty()) display = fileLink ? "첨부 파일" : value;
            insertLinkedText(display, value, fileLink ? " 📎" : "");
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void insertLinkedText(String display, String url, String suffix) {
        int start = Math.max(0, bodyInput.getSelectionStart());
        int end = Math.max(0, bodyInput.getSelectionEnd());
        if (start > end) { int swap = start; start = end; end = swap; }
        String text = display + suffix;
        Spannable linked = new android.text.SpannableString(text);
        linked.setSpan(new URLSpan(url), 0, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        linked.setSpan(new ForegroundColorSpan(OCEAN), 0, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        bodyInput.getText().replace(start, end, linked);
        bodyInput.setSelection(start + text.length());
    }

    private void insertAtCursor(String text) {
        int start = Math.max(0, bodyInput.getSelectionStart());
        int end = Math.max(0, bodyInput.getSelectionEnd());
        if (start > end) { int swap = start; start = end; end = swap; }
        bodyInput.getText().replace(start, end, text);
        bodyInput.setSelection(start + text.length());
    }

    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? value : "";
    }

    private LinearLayout popupRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(4), dp(3), dp(4), dp(3));
        row.setBackground(rounded(Color.WHITE, Color.parseColor("#C7DDE3"), 24));
        return row;
    }

    private PopupWindow popupWindow(View content, int width, int height) {
        PopupWindow popup = new PopupWindow(content, width, height, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));
        return popup;
    }

    private void showPopupAbove(PopupWindow popup, View anchor, int widthDp) {
        int popupWidth = dp(widthDp);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int desiredLeft = Math.max(dp(8), Math.min(location[0], screenWidth - popupWidth - dp(8)));
        int xOffset = desiredLeft - location[0];
        popup.showAsDropDown(anchor, xOffset, -anchor.getHeight() - dp(62));
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.WHITE, Color.parseColor("#B8D7DF"), 14));
        return input;
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

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(rounded(Color.TRANSPARENT, Color.parseColor("#66FFFFFF"), 14));
        return button;
    }

    private Button secondaryEditorButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground(rounded(Color.parseColor("#F8FCFD"), Color.parseColor("#C7DDE3"), 10));
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

    private String clean(String value) {
        return value == null ? "" : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum LayoutAlignment { LEFT, CENTER, RIGHT }
}
