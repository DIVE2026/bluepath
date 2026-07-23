package com.bluepath.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bluepath.app.network.ApiClient;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.repository.BluePathRepository;
import com.bluepath.app.storage.UserStore;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunityPostDetailActivity extends AppCompatActivity {
    public static final String EXTRA_POST_ID = "community_post_id";

    private final int NAVY = Color.parseColor("#06223F");
    private final int OCEAN = Color.parseColor("#0E7490");
    private final int BG = Color.parseColor("#F7FAFC");
    private final int TEXT = Color.parseColor("#17324D");
    private final int MUTED = Color.parseColor("#64748B");
    private final int LINE = Color.parseColor("#D7E3E8");
    private final int ACTIVE = Color.parseColor("#E1F7F7");
    private final int DANGER = Color.parseColor("#B42318");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BluePathRepository repository;
    private UserStore store;
    private String postId;
    private ApiModels.CommunityPostDto post;
    private LinearLayout root;
    private LinearLayout content;
    private EditText commentInput;
    private Button commentSubmit;
    private TextView replyNotice;
    private String replyParentId;
    private String replyTargetName;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        repository = new BluePathRepository(this);
        store = new UserStore(this);
        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null || postId.trim().isEmpty()) {
            finish();
            return;
        }
        setContentView(buildBaseScreen());
        loadPost(true);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildBaseScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(14), dp(8));
        header.setBackgroundColor(NAVY);

        TextView back = actionText("‹", Color.WHITE, 32, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("게시글 목록으로 돌아가기");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = actionText("게시글", Color.WHITE, 20, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView refresh = actionText("새로고침", Color.parseColor("#C9FFFF"), 13, false);
        refresh.setOnClickListener(v -> loadPost(true));
        header.addView(refresh, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void loadPost(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading) renderLoading();
        executor.execute(() -> {
            try {
                ApiModels.CommunityPostDto loaded = repository.communityPost(postId);
                runOnUiThread(() -> {
                    post = loaded;
                    loading = false;
                    renderPost();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    renderError("게시글을 불러오지 못했습니다: " + safeMessage(e));
                });
            }
        });
    }

    private void renderLoading() {
        content.removeAllViews();
        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, dp(40), 0, dp(12));
        content.addView(progress, params);
        TextView text = text("게시글을 불러오고 있습니다", 14, MUTED, false);
        text.setGravity(Gravity.CENTER);
        content.addView(text);
    }

    private void renderError(String message) {
        content.removeAllViews();
        TextView error = text(message, 14, DANGER, false);
        error.setPadding(0, dp(24), 0, dp(18));
        content.addView(error);
        Button retry = primaryButton("다시 시도");
        retry.setOnClickListener(v -> loadPost(true));
        content.addView(retry, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void renderPost() {
        content.removeAllViews();
        if (post == null) return;

        TextView category = text("question".equals(post.category) ? "질문 게시판" : "자유 게시판", 12, OCEAN, true);
        content.addView(category);

        TextView title = text(post.title, 25, TEXT, true);
        title.setPadding(0, dp(8), 0, dp(12));
        content.addView(title);

        LinearLayout authorRow = horizontal();
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        authorRow.addView(avatar(post.author == null ? "" : post.author.nickname,
                post.author == null ? "" : post.author.profileImageUrl, dp(38)),
                new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout authorInfo = new LinearLayout(this);
        authorInfo.setOrientation(LinearLayout.VERTICAL);
        authorInfo.setPadding(dp(10), 0, 0, 0);
        String nickname = post.author == null ? "알 수 없음" : post.author.nickname;
        authorInfo.addView(text(nickname, 14, TEXT, true));
        authorInfo.addView(text(readableDate(post.createdAt), 11, MUTED, false));
        authorRow.addView(authorInfo, new LinearLayout.LayoutParams(0, -2, 1));

        if (post.author != null && !safe(store.getNickname()).equals(safe(post.author.nickname))) {
            TextView follow = inlineAction(post.author.isFollowing ? "팔로잉" : "팔로우");
            follow.setOnClickListener(v -> toggleFollow(post.author.userId));
            authorRow.addView(follow);
        }
        content.addView(authorRow);
        content.addView(divider(dp(14), dp(18)));

        TextView body = text(post.body, 16, TEXT, false);
        body.setLineSpacing(0, 1.35f);
        body.setTextIsSelectable(true);
        content.addView(body);

        if (!safe(post.imageUrl).isEmpty()) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.parseColor("#E8EEF1"));
            Glide.with(this).load(ApiClient.resolveMediaUrl(post.imageUrl)).fitCenter().into(image);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(260));
            imageParams.setMargins(0, dp(18), 0, 0);
            content.addView(image, imageParams);
        }

        LinearLayout.LayoutParams reactionParams = new LinearLayout.LayoutParams(-1, -2);
        reactionParams.setMargins(0, dp(16), 0, dp(4));
        content.addView(reactionBar("post", post.id, post.reactions), reactionParams);

        LinearLayout postActions = horizontal();
        postActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (post.canEdit) {
            TextView edit = inlineAction("수정");
            edit.setOnClickListener(v -> showEditPostDialog());
            TextView delete = inlineAction("삭제");
            delete.setTextColor(DANGER);
            delete.setOnClickListener(v -> confirmDeletePost());
            postActions.addView(edit);
            postActions.addView(delete);
        } else {
            TextView report = inlineAction("신고");
            report.setOnClickListener(v -> showReportDialog("post", post.id));
            TextView block = inlineAction("작성자 차단");
            block.setOnClickListener(v -> confirmBlockUser(post.author.userId, post.author.nickname));
            postActions.addView(report);
            postActions.addView(block);
        }
        content.addView(postActions);
        content.addView(divider(dp(18), dp(18)));

        int commentCount = post.comments == null ? 0 : post.comments.size();
        content.addView(text("댓글 " + commentCount, 18, TEXT, true));
        if (post.comments == null || post.comments.isEmpty()) {
            TextView empty = text("아직 댓글이 없습니다. 첫 댓글을 남겨 보세요.", 14, MUTED, false);
            empty.setPadding(0, dp(16), 0, dp(14));
            content.addView(empty);
        } else {
            addCommentChildren(content, null, 0);
        }

        content.addView(divider(dp(18), dp(14)));
        replyNotice = text("", 12, OCEAN, true);
        replyNotice.setVisibility(replyParentId == null ? View.GONE : View.VISIBLE);
        if (replyParentId != null) replyNotice.setText(replyTargetName + "님에게 답글 작성 중 · 취소");
        replyNotice.setOnClickListener(v -> cancelReply());
        content.addView(replyNotice);

        LinearLayout composer = horizontal();
        composer.setGravity(Gravity.BOTTOM);
        commentInput = new EditText(this);
        commentInput.setHint(replyParentId == null ? "댓글을 입력하세요" : replyTargetName + "님에게 답글 입력");
        commentInput.setTextColor(TEXT);
        commentInput.setHintTextColor(Color.parseColor("#94A3B8"));
        commentInput.setTextSize(14);
        commentInput.setGravity(Gravity.TOP | Gravity.START);
        commentInput.setMinLines(3);
        commentInput.setMaxLines(6);
        configureKoreanTextInput(commentInput, true);
        commentInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        commentInput.setBackground(rounded(Color.WHITE, LINE, 8));
        composer.addView(commentInput, new LinearLayout.LayoutParams(0, dp(92), 1));

        commentSubmit = primaryButton("등록");
        commentSubmit.setOnClickListener(v -> submitComment());
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(dp(72), dp(48));
        submitParams.setMargins(dp(8), 0, 0, 0);
        composer.addView(commentSubmit, submitParams);
        content.addView(composer);
    }

    private void addCommentChildren(LinearLayout container, String parentId, int depth) {
        if (post == null || post.comments == null) return;
        for (ApiModels.CommunityCommentDto comment : post.comments) {
            boolean matches = parentId == null ? comment.parentId == null : parentId.equals(comment.parentId);
            if (!matches) continue;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(0, dp(14), 0, dp(12));

            if (depth > 0) {
                View replyLine = new View(this);
                replyLine.setBackgroundColor(Color.parseColor("#B9D8DE"));
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(2), -1);
                lineParams.setMargins(dp(Math.min(depth, 3) * 12), 0, dp(10), 0);
                item.addView(replyLine, lineParams);
            }

            LinearLayout commentContent = new LinearLayout(this);
            commentContent.setOrientation(LinearLayout.VERTICAL);

            LinearLayout head = horizontal();
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(avatar(comment.author.nickname, comment.author.profileImageUrl, dp(30)),
                    new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView name = text(comment.author.nickname, 13, TEXT, true);
            name.setPadding(dp(8), 0, dp(8), 0);
            head.addView(name);
            head.addView(text(readableDate(comment.createdAt), 11, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
            if (!safe(store.getNickname()).equals(safe(comment.author.nickname))) {
                TextView follow = inlineAction(comment.author.isFollowing ? "팔로잉" : "팔로우");
                follow.setTextSize(11);
                follow.setOnClickListener(v -> toggleFollow(comment.author.userId));
                head.addView(follow);
            }
            commentContent.addView(head);

            TextView body = text(comment.body, 14, TEXT, false);
            body.setLineSpacing(0, 1.25f);
            body.setPadding(dp(38), dp(7), 0, dp(5));
            commentContent.addView(body);

            LinearLayout actions = horizontal();
            actions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            actions.setPadding(dp(30), 0, 0, 0);
            actions.addView(reactionBar("comment", comment.id, comment.reactions), new LinearLayout.LayoutParams(0, -2, 1));
            TextView reply = inlineAction("답글");
            reply.setOnClickListener(v -> beginReply(comment.id, comment.author.nickname));
            actions.addView(reply);
            if (comment.canEdit) {
                TextView edit = inlineAction("수정");
                edit.setOnClickListener(v -> showEditCommentDialog(comment));
                TextView delete = inlineAction("삭제");
                delete.setTextColor(DANGER);
                delete.setOnClickListener(v -> confirmDeleteComment(comment.id));
                actions.addView(edit);
                actions.addView(delete);
            } else {
                TextView report = inlineAction("신고");
                report.setOnClickListener(v -> showReportDialog("comment", comment.id));
                actions.addView(report);
            }
            commentContent.addView(actions);
            item.addView(commentContent, new LinearLayout.LayoutParams(0, -2, 1));
            container.addView(item, new LinearLayout.LayoutParams(-1, -2));
            container.addView(thinDivider());
            addCommentChildren(container, comment.id, depth + 1);
        }
    }

    private View reactionBar(String targetType, String targetId, List<ApiModels.ReactionSummary> reactions) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout line = horizontal();
        line.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        if (reactions != null) {
            for (ApiModels.ReactionSummary summary : reactions) {
                if (summary == null || summary.count <= 0 || safe(summary.emoji).isEmpty()) continue;
                TextView reaction = text(summary.emoji + " " + summary.count, 13, TEXT, false);
                reaction.setGravity(Gravity.CENTER);
                reaction.setPadding(dp(8), dp(4), dp(8), dp(4));
                reaction.setBackground(rounded(summary.reactedByMe ? ACTIVE : Color.TRANSPARENT, LINE, 16));
                reaction.setOnClickListener(v -> toggleReaction(targetType, targetId, summary.emoji));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
                params.setMargins(0, 0, dp(5), 0);
                line.addView(reaction, params);
            }
        }

        TextView addReaction = text("공감", 12, MUTED, false);
        addReaction.setGravity(Gravity.CENTER);
        addReaction.setPadding(dp(9), dp(4), dp(9), dp(4));
        addReaction.setBackground(rounded(Color.TRANSPARENT, LINE, 16));
        addReaction.setOnClickListener(v -> showReactionPicker(targetType, targetId));
        line.addView(addReaction, new LinearLayout.LayoutParams(-2, dp(34)));

        scroll.addView(line);
        return scroll;
    }

    private void showReactionPicker(String targetType, String targetId) {
        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🌊"};
        new AlertDialog.Builder(this)
                .setTitle("공감 선택")
                .setItems(emojis, (dialog, which) -> toggleReaction(targetType, targetId, emojis[which]))
                .setNegativeButton("취소", null)
                .show();
    }

    private void submitComment() {
        String value = commentInput.getText().toString().trim();
        if (value.isEmpty()) {
            toast("댓글 내용을 입력해 주세요.");
            return;
        }
        commentSubmit.setEnabled(false);
        commentSubmit.setText("등록 중");
        final String parent = replyParentId;
        executor.execute(() -> {
            try {
                repository.createCommunityComment(postId, value, parent);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    replyParentId = null;
                    replyTargetName = null;
                    loadPost(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    commentSubmit.setEnabled(true);
                    commentSubmit.setText("등록");
                    toast("댓글 작성 실패: " + safeMessage(e));
                });
            }
        });
    }

    private void beginReply(String parentId, String authorName) {
        replyParentId = parentId;
        replyTargetName = authorName;
        renderPost();
        commentInput.requestFocus();
    }

    private void cancelReply() {
        replyParentId = null;
        replyTargetName = null;
        renderPost();
    }

    private void toggleReaction(String targetType, String targetId, String emoji) {
        executor.execute(() -> {
            try {
                repository.toggleReaction(targetType, targetId, emoji);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    loadPost(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("공감 처리 실패: " + safeMessage(e)));
            }
        });
    }

    private void toggleFollow(String userId) {
        executor.execute(() -> {
            try {
                repository.toggleFollow(userId);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    loadPost(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("팔로우 처리 실패: " + safeMessage(e)));
            }
        });
    }

    private void showEditPostDialog() {
        LinearLayout fields = dialogFields();
        EditText title = dialogInput("제목", post.title, true);
        EditText body = dialogInput("내용", post.body, false);
        fields.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, dp(180));
        bodyParams.setMargins(0, dp(10), 0, 0);
        fields.addView(body, bodyParams);
        new AlertDialog.Builder(this)
                .setTitle("게시글 수정")
                .setView(fields)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String t = title.getText().toString().trim();
                    String b = body.getText().toString().trim();
                    if (t.length() < 2 || b.length() < 2) {
                        toast("제목과 내용을 2자 이상 입력해 주세요.");
                        return;
                    }
                    executor.execute(() -> {
                        try {
                            repository.updateCommunityPost(postId, t, b);
                            runOnUiThread(() -> {
                                setResult(RESULT_OK);
                                loadPost(false);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> toast("게시글 수정 실패: " + safeMessage(e)));
                        }
                    });
                }).show();
    }

    private void confirmDeletePost() {
        new AlertDialog.Builder(this)
                .setTitle("게시글 삭제")
                .setMessage("게시글과 댓글을 모두 삭제하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> executor.execute(() -> {
                    try {
                        repository.deleteCommunityPost(postId);
                        runOnUiThread(() -> {
                            setResult(RESULT_OK);
                            finish();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("게시글 삭제 실패: " + safeMessage(e)));
                    }
                })).show();
    }

    private void showEditCommentDialog(ApiModels.CommunityCommentDto comment) {
        EditText input = dialogInput("댓글", comment.body, false);
        input.setMinLines(4);
        new AlertDialog.Builder(this)
                .setTitle("댓글 수정")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    executor.execute(() -> {
                        try {
                            repository.updateCommunityComment(comment.id, value);
                            runOnUiThread(() -> {
                                setResult(RESULT_OK);
                                loadPost(false);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> toast("댓글 수정 실패: " + safeMessage(e)));
                        }
                    });
                }).show();
    }

    private void confirmDeleteComment(String commentId) {
        new AlertDialog.Builder(this)
                .setTitle("댓글 삭제")
                .setMessage("이 댓글과 답글을 삭제하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> executor.execute(() -> {
                    try {
                        repository.deleteCommunityComment(commentId);
                        runOnUiThread(() -> {
                            setResult(RESULT_OK);
                            loadPost(false);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("댓글 삭제 실패: " + safeMessage(e)));
                    }
                })).show();
    }

    private void showReportDialog(String targetType, String targetId) {
        EditText input = dialogInput("신고 사유", "", false);
        input.setHint("신고 사유를 2자 이상 입력하세요");
        new AlertDialog.Builder(this)
                .setTitle("신고")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("신고", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.length() < 2) {
                        toast("신고 사유를 2자 이상 입력해 주세요.");
                        return;
                    }
                    executor.execute(() -> {
                        try {
                            repository.reportCommunity(targetType, targetId, reason);
                            runOnUiThread(() -> toast("신고를 접수했습니다."));
                        } catch (Exception e) {
                            runOnUiThread(() -> toast("신고 실패: " + safeMessage(e)));
                        }
                    });
                }).show();
    }

    private void confirmBlockUser(String userId, String nickname) {
        new AlertDialog.Builder(this)
                .setTitle("사용자 차단")
                .setMessage(nickname + "님을 차단하시겠습니까? 해당 사용자의 글과 댓글이 보이지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("차단", (dialog, which) -> executor.execute(() -> {
                    try {
                        repository.toggleBlock(userId);
                        runOnUiThread(() -> {
                            setResult(RESULT_OK);
                            finish();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("차단 처리 실패: " + safeMessage(e)));
                    }
                })).show();
    }

    private LinearLayout dialogFields() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18), dp(8), dp(18), 0);
        return fields;
    }

    private EditText dialogInput(String hint, String value, boolean singleLine) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setTextSize(14);
        input.setSingleLine(singleLine);
        configureKoreanTextInput(input, !singleLine);
        if (!singleLine) input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(rounded(Color.WHITE, LINE, 8));
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

    private View avatar(String nickname, String imageUrl, int size) {
        if (!safe(imageUrl).isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(ApiClient.resolveMediaUrl(imageUrl)).circleCrop().into(image);
            return image;
        }
        TextView fallback = text(safe(nickname).isEmpty() ? "B" : safe(nickname).substring(0, 1), 14, Color.WHITE, true);
        fallback.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(OCEAN);
        fallback.setBackground(background);
        return fallback;
    }

    private LinearLayout horizontal() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        return line;
    }

    private TextView inlineAction(String value) {
        TextView action = text(value, 12, MUTED, false);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(8), dp(7), dp(8), dp(7));
        return action;
    }

    private TextView actionText(String value, int color, int size, boolean bold) {
        TextView view = text(value, size, color, bold);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(13);
        button.setBackground(rounded(OCEAN, OCEAN, 10));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private View divider(int top, int bottom) {
        View line = thinDivider();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(0, top, 0, bottom);
        line.setLayoutParams(params);
        return line;
    }

    private View thinDivider() {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return line;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private String readableDate(String value) {
        if (value == null || value.trim().isEmpty()) return "방금 전";
        String result = value.replace('T', ' ');
        return result.length() > 16 ? result.substring(0, 16) : result;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
