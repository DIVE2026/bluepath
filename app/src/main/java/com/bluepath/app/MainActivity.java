package com.bluepath.app;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.LocaleList;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.PaperItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.network.ApiClient;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.repository.BluePathRepository;
import com.bluepath.app.storage.UserStore;
import com.bluepath.app.util.MarineLlmClient;
import com.bluepath.app.util.NotificationHelper;
import com.bluepath.app.util.PortfolioPdfExporter;
import com.bluepath.app.util.PromotionRules;
import com.bluepath.app.util.RecommendationEngine;
import com.bluepath.app.util.SkillProfileCatalog;
import com.bluepath.app.view.ActivityHeatmapView;
import com.bluepath.app.view.MonthCalendarView;
import com.bluepath.app.view.OceanBackgroundView;
import com.bluepath.app.view.OceanSkillMapView;
import com.bluepath.app.view.TierShieldView;
import com.bluepath.app.view.TierTextFormatter;
import com.bluepath.app.viewmodel.BluePathViewModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;


import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    private static final String WAVE_MARK = "∿";
    private static final long QUIZ_TIME_LIMIT_MS = 30_000L;

    private final int NAVY = Color.parseColor("#06223F");
    private final int OCEAN = Color.parseColor("#0E7490");
    private final int CYAN = Color.parseColor("#18D6D2");
    private final int BG = Color.parseColor("#F2FAFB");
    private final int TEXT = Color.parseColor("#17324D");
    private final int MUTED = Color.parseColor("#64748B");
    private final int SUCCESS = Color.parseColor("#047857");
    private final int DANGER = Color.parseColor("#B42318");

    private UserStore store;
    private MarineLlmClient llmClient;
    private BluePathViewModel viewModel;
    private BluePathRepository cloudRepository;
    private ActivityResultLauncher<String[]> profileImagePicker;
    private ActivityResultLauncher<Intent> communityPostLauncher;
    private ActivityResultLauncher<Object> qrScanner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout content;
    private ScrollView contentScroll;
    private FrameLayout appRoot;
    private LinearLayout sidebar;
    private View sidebarScrim;
    private int currentTab = 0;
    private String learningSubTab = "video";
    private String communityCategory = "free";
    private int selectedActivityYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
    private boolean dashboardRefreshing = false;
    private long dashboardRefreshedAt = 0L;
    private boolean communityLoading = false;
    private boolean communityInitialized = false;
    private String communityError = "";
    private String communityQuery = "";
    private int communityOffset = 0;
    private boolean communityHasMore = true;
    private static final int COMMUNITY_PAGE_SIZE = 20;
    private List<ApiModels.CommunityPostDto> communityPosts = new ArrayList<>();
    private final List<AiSearchTurn> learningSearchConversation = new ArrayList<>();
    private final List<AiSearchTurn> scheduleSearchConversation = new ArrayList<>();
    private String learningSearchDraft = "";
    private String scheduleSearchDraft = "";
    private final Set<String> scheduleSelectedTags = new LinkedHashSet<>();
    private String scheduleStatusFilter = "전체";
    private String scheduleSelectedDate = "";
    private int scheduleCalendarYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
    private int scheduleCalendarMonth = Calendar.getInstance(Locale.KOREA).get(Calendar.MONTH);
    private boolean scheduleOnlineExpanded = false;

    private List<QuizQuestion> activeQuiz = new ArrayList<>();
    private int[] selectedAnswers = new int[0];
    private boolean quizGenerating = false;
    private boolean quizSubmitted = false;
    private String quizAttemptTier = "";
    private String quizSource = "";
    private String quizNotice = "";
    private int quizCorrect = 0;
    private int quizAwardedXp = 0;
    private long quizDeadlineElapsedRealtime = 0L;
    private CountDownTimer quizCountDownTimer;
    private TextView quizTimerText;
    private boolean quizTimedOut = false;
    private boolean quizServerAuthoritative = false;
    private boolean quizSubmitting = false;

    private final List<AgentTurn> agentConversation = new ArrayList<>();
    private String agentDraft = "";
    private View conversationScrollAnchor;

    private boolean routeLoading = false;
    private boolean routeAttempted = false;
    private boolean routeRerouting = false;
    private String routeError = "";
    private ApiModels.RoutePlanResponse currentRoute;
    private boolean missionLoading = false;
    private String missionError = "";
    private ApiModels.FamilyMissionResponse currentMission;
    private ApiModels.MissionQrPayload currentQrPayload;
    private int missionParticipantCount = 2;
    private boolean guardianDialogVisible = false;

    private static final class AgentTurn {
        final String question;
        String answer = "";
        String error = "";
        boolean loading = true;

        AgentTurn(String question) {
            this.question = question;
        }
    }

    private static final class AiSearchTurn {
        final String resourceType;
        final String query;
        ApiModels.AiSearchResponse response;
        String error = "";
        boolean loading = true;

        AiSearchTurn(String resourceType, String query) {
            this.resourceType = resourceType;
            this.query = query;
        }
    }

    /**
     * 커뮤니티 화면 전용 당겨서 새로고침 스크롤뷰입니다.
     * 별도의 SwipeRefreshLayout 의존성 없이 화면 최상단에서 아래로 충분히 당긴 뒤
     * 손을 놓으면 커뮤니티 목록을 다시 불러옵니다.
     */
    private class CommunityRefreshScrollView extends ScrollView {
        private float pullStartY;
        private boolean pullCandidate;
        private boolean refreshArmed;

        CommunityRefreshScrollView() {
            super(MainActivity.this);
            setFillViewport(true);
            setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            boolean triggerRefresh = false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pullStartY = event.getY();
                    pullCandidate = getScrollY() == 0 && !communityLoading;
                    refreshArmed = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    // 목록 중간에서 위로 올라온 경우, 최상단에 닿은 지점부터 당김 거리를 다시 잽니다.
                    if (!pullCandidate && getScrollY() == 0 && !communityLoading) {
                        pullCandidate = true;
                        pullStartY = event.getY();
                    }
                    if (pullCandidate) {
                        float pulledDistance = event.getY() - pullStartY;
                        refreshArmed = getScrollY() == 0 && pulledDistance >= dp(84);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    triggerRefresh = pullCandidate
                            && refreshArmed
                            && getScrollY() == 0
                            && currentTab == 5
                            && !communityLoading;
                    pullCandidate = false;
                    refreshArmed = false;
                    break;

                case MotionEvent.ACTION_CANCEL:
                    pullCandidate = false;
                    refreshArmed = false;
                    break;
            }

            boolean handled = super.dispatchTouchEvent(event);
            if (triggerRefresh) {
                // 터치 이벤트 처리가 끝난 뒤 화면을 다시 그리도록 예약합니다.
                post(MainActivity.this::requestCommunityRefresh);
            }
            return handled;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DataRepository.initialize(this);
        store = new UserStore(this);
        llmClient = new MarineLlmClient(store);
        viewModel = new ViewModelProvider(this).get(BluePathViewModel.class);
        cloudRepository = new BluePathRepository(this);
        profileImagePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            executor.execute(() -> {
                try {
                    cloudRepository.uploadProfileImage(uri);
                    runOnUiThread(() -> {
                        toast("프로필 사진을 업로드했습니다.");
                        showApp(6);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> toast("프로필 사진 업로드 실패: " + safeMessage(e)));
                }
            });
        });
        registerMissionQrScanner();
        NotificationHelper.scheduleVoyageAutoReroute(this);
        communityPostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        communityPosts.clear();
                        communityError = "";
                        requestCommunityRefresh();
                    }
                });
        viewModel.operation().observe(this, state -> {
            if (state == null || "처리 중…".equals(state.message)) return;
            toast(state.message);
            if (!state.success) return;
            if ("login".equals(state.type) || "register".equals(state.type)) {
                refreshAccountBindings();
                clearVoyageSession();
                if (store.hasProfile()) {
                    showApp(0);
                    if (viewModel.isCloudConfigured()) viewModel.refreshCatalog();
                } else {
                    showOnboarding();
                }
                return;
            }
            if ("password_reset".equals(state.type) || "logout".equals(state.type)) {
                if ("logout".equals(state.type)) refreshAccountBindings();
                clearVoyageSession();
                showLoginScreen();
                return;
            }
            if ("catalog".equals(state.type) && store.hasCloudSession() && store.hasProfile()) {
                showApp(currentTab);
                return;
            }
            if (store.hasCloudSession() && store.hasProfile()) showApp(currentTab);
        });
        showWelcomeScreen();
    }

    private void refreshAccountBindings() {
        store = new UserStore(this);
        llmClient = new MarineLlmClient(store);
        cloudRepository = new BluePathRepository(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store == null || appRoot == null) return;

        if (currentTab == 5
                && !communityLoading
                && (!communityInitialized || !communityError.isEmpty())) {
            // 백엔드가 재시작되었거나 직전 요청이 실패했더라도 앱을 삭제할 필요 없이
            // 화면으로 돌아오는 순간 커뮤니티 API를 다시 시도합니다.
            requestCommunityRefresh();
            return;
        }

        if (currentTab == 1) showApp(1);
    }

    @Override
    protected void onDestroy() {
        cancelQuizTimer();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showWelcomeScreen() {
        FrameLayout screen = oceanFrame();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(26), dp(26), dp(28));
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);

        TextView eyebrow = authLabel("MARINE · DATA · CAREER");
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);

        View upperSpace = new View(this);
        root.addView(upperSpace, new LinearLayout.LayoutParams(1, 0, 0.65f));

        TextView waveMark = createWaveMark(
                98,
                Color.WHITE,
                Gravity.CENTER
        );
        root.addView(waveMark, new LinearLayout.LayoutParams(-1, dp(112)));

        TextView brand = new TextView(this);
        brand.setText("BLUEPATH");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(42);
        brand.setLetterSpacing(0.10f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);

        TextView tagline = authBody("데이터로 나만의 항로를 설계하고\n해양 진로와 배움의 타이밍을 발견하세요");
        tagline.setTextSize(16);
        tagline.setGravity(Gravity.CENTER);
        tagline.setLineSpacing(dp(5), 1.05f);
        tagline.setPadding(0, dp(12), 0, dp(18));
        root.addView(tagline);

        LinearLayout dots = row();
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            TextView dot = new TextView(this);
            dot.setText(i == 0 ? "●" : "•");
            dot.setTextColor(i == 0 ? Color.WHITE : Color.parseColor("#7FC6CA"));
            dot.setTextSize(i == 0 ? 13 : 18);
            dot.setGravity(Gravity.CENTER);
            dots.addView(dot, new LinearLayout.LayoutParams(dp(20), dp(28)));
        }
        root.addView(dots);

        View lowerSpace = new View(this);
        root.addView(lowerSpace, new LinearLayout.LayoutParams(1, 0, 1f));

        Button start = authPrimaryButton(store.hasCloudSession() ? "내 계정으로 계속하기  →" : "시작하기  →");
        start.setOnClickListener(v -> {
            if (store.hasCloudSession() && store.hasProfile()) showApp(0); else showLoginScreen();
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(58));
        startParams.setMargins(0, 0, 0, dp(12));
        root.addView(start, startParams);

        Button preview = authOutlineButton("새 계정 만들기");
        preview.setOnClickListener(v -> showRegisterScreen());
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView foot = authBody("로그인 또는 회원가입 후 맞춤 항로가 저장됩니다.");
        foot.setTextSize(11);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(14), 0, 0);
        root.addView(foot);
    }

    private LinearLayout welcomeMetric(String value, String caption) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(10), dp(5), dp(10));
        TextView number = new TextView(this);
        number.setText(value);
        number.setTextColor(Color.WHITE);
        number.setTextSize(21);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        number.setGravity(Gravity.CENTER);
        TextView text = new TextView(this);
        text.setText(caption);
        text.setTextColor(Color.parseColor("#BFFBFA"));
        text.setTextSize(11);
        text.setGravity(Gravity.CENTER);
        box.addView(number);
        box.addView(text);
        return box;
    }

    private void showLoginScreen() {
        root = oceanScrollableRoot(dp(22), dp(18), dp(22), dp(30));

        Button backTop = authTextButton("‹  시작 화면");
        backTop.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        backTop.setOnClickListener(v -> showWelcomeScreen());
        root.addView(backTop, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView waveMark = authWaveMark();
        root.addView(waveMark, new LinearLayout.LayoutParams(-1, dp(82)));

        TextView heading = authTitle("Welcome to BluePath");
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        TextView description = authBody("로그인하고 학습, 퀴즈, 일정, 진로, 해양 AI를 하나의 항로에서 이어가세요.");
        description.setGravity(Gravity.CENTER);
        description.setPadding(dp(12), 0, dp(12), dp(20));
        root.addView(description);

        if (store.hasCloudSession()) {
            LinearLayout sessionCard = authCard();
            sessionCard.addView(authLabel("SAVED SESSION"));
            sessionCard.addView(authBig(store.getAccountDisplayName()));
            sessionCard.addView(authBody(store.getAccountEmail() + " 계정으로 안전하게 계속할 수 있습니다."));
            Button continueButton = authPrimaryButton("이 계정으로 계속하기");
            continueButton.setOnClickListener(v -> {
                if (store.hasProfile()) showApp(0); else showOnboarding();
            });
            LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(-1, dp(52));
            continueParams.setMargins(0, dp(10), 0, 0);
            sessionCard.addView(continueButton, continueParams);
            root.addView(sessionCard);
        }

        LinearLayout form = authCard();
        form.addView(authLabel("SIGN IN"));
        form.addView(authBig("나의 해양 항로 열기"));
        EditText email = authInputField("email@example.com", store.getAccountEmail());
        EditText password = authInputField("8자 이상 비밀번호", "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(authLabel("이메일"));
        form.addView(email, new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(authLabel("비밀번호"));
        form.addView(password, new LinearLayout.LayoutParams(-1, dp(52)));

        Button login = authPrimaryButton("로그인");
        login.setOnClickListener(v -> {
            String emailValue = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            if (emailValue.isEmpty() || passwordValue.length() < 8) {
                toast("이메일과 8자 이상의 비밀번호를 입력해 주세요.");
                return;
            }
            viewModel.login(emailValue, passwordValue);
        });
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(-1, dp(54));
        loginParams.setMargins(0, dp(18), 0, dp(8));
        form.addView(login, loginParams);

        Button forgot = authTextButton("비밀번호를 잊어버렸나요?");
        forgot.setGravity(Gravity.CENTER);
        forgot.setOnClickListener(v -> showForgotPasswordScreen());
        form.addView(forgot, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(form);

        LinearLayout signupCard = authCard();
        TextView signupText = authBody("처음 오셨나요? 연령, 관심 분야, 목표에 맞춘 나만의 해양 항로를 만들어 보세요.");
        signupText.setGravity(Gravity.CENTER);
        signupCard.addView(signupText);
        Button signup = authOutlineButton("회원가입");
        signup.setOnClickListener(v -> showRegisterScreen());
        LinearLayout.LayoutParams signupParams = new LinearLayout.LayoutParams(-1, dp(50));
        signupParams.setMargins(0, dp(8), 0, 0);
        signupCard.addView(signup, signupParams);
        root.addView(signupCard);

        if (!viewModel.isCloudConfigured()) {
            TextView warning = authBody("개발 빌드에 BLUEPATH_API_BASE_URL이 설정되지 않았습니다. 서버 주소를 설정해야 회원가입과 로그인이 동작합니다.");
            warning.setTextColor(Color.parseColor("#FFD6D1"));
            warning.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(warning);
        }
    }

    private void showRegisterScreen() {
        root = oceanScrollableRoot(dp(22), dp(18), dp(22), dp(30));

        Button backTop = authTextButton("‹  로그인");
        backTop.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        backTop.setOnClickListener(v -> showLoginScreen());
        root.addView(backTop, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(authWaveMark(), new LinearLayout.LayoutParams(-1, dp(72)));

        TextView heading = authTitle("Create your BluePath");
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        TextView description = authBody("계정을 만든 뒤 관심 분야와 목표를 설정하면 맞춤형 해양 진로 항로가 생성됩니다.");
        description.setGravity(Gravity.CENTER);
        description.setPadding(dp(10), 0, dp(10), dp(20));
        root.addView(description);

        LinearLayout form = authCard();
        EditText email = authInputField("email@example.com", "");
        EditText nickname = authInputField("2~20자 닉네임", "");
        EditText password = authInputField("8자 이상 비밀번호", "");
        EditText confirm = authInputField("비밀번호 다시 입력", "");
        EditText guardian = authInputField("보호자 이메일 (선택)", "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(authLabel("이메일")); form.addView(email, new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(authLabel("커뮤니티 닉네임"));
        LinearLayout nicknameRow = row();
        nicknameRow.addView(nickname, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button checkNickname = authOutlineButton("중복 확인");
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(104), dp(52));
        checkParams.setMargins(dp(8), 0, 0, 0);
        nicknameRow.addView(checkNickname, checkParams);
        form.addView(nicknameRow);
        TextView nicknameStatus = authBody("한글·영문·숫자와 _ . - 만 사용할 수 있습니다.");
        nicknameStatus.setTextSize(11);
        form.addView(nicknameStatus);
        final String[] verifiedNickname = {""};
        checkNickname.setOnClickListener(v -> {
            String value = nickname.getText().toString().trim();
            if (!value.matches("[0-9A-Za-z가-힣_.-]{2,20}")) {
                nicknameStatus.setText("닉네임 형식을 확인해 주세요.");
                nicknameStatus.setTextColor(DANGER);
                return;
            }
            checkNickname.setEnabled(false);
            nicknameStatus.setText("중복 여부를 확인하고 있습니다…");
            executor.execute(() -> {
                try {
                    ApiModels.NicknameAvailability result = cloudRepository.nicknameAvailable(value);
                    runOnUiThread(() -> {
                        checkNickname.setEnabled(true);
                        nicknameStatus.setText(result.message);
                        nicknameStatus.setTextColor(result.available ? SUCCESS : DANGER);
                        verifiedNickname[0] = result.available ? result.nickname : "";
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        checkNickname.setEnabled(true);
                        nicknameStatus.setText("중복 확인 실패: " + safeMessage(e));
                        nicknameStatus.setTextColor(DANGER);
                    });
                }
            });
        });
        nickname.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !verifiedNickname[0].equals(nickname.getText().toString().trim())) verifiedNickname[0] = "";
        });
        form.addView(authLabel("비밀번호")); form.addView(password, new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(authLabel("비밀번호 확인")); form.addView(confirm, new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(authLabel("보호자 이메일")); form.addView(guardian, new LinearLayout.LayoutParams(-1, dp(52)));
        Button create = authPrimaryButton("계정 만들기");
        create.setOnClickListener(v -> {
            String emailValue = email.getText().toString().trim();
            String nicknameValue = nickname.getText().toString().trim();
            String passwordValue = password.getText().toString();
            if (emailValue.isEmpty() || passwordValue.length() < 8) {
                toast("이메일과 8자 이상의 비밀번호를 입력해 주세요.");
                return;
            }
            if (!nicknameValue.equals(verifiedNickname[0])) {
                toast("닉네임 중복 확인을 완료해 주세요.");
                return;
            }
            if (!passwordValue.equals(confirm.getText().toString())) {
                toast("비밀번호 확인이 일치하지 않습니다.");
                return;
            }
            viewModel.register(emailValue, passwordValue, guardian.getText().toString().trim(), nicknameValue);
        });
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, dp(54));
        createParams.setMargins(0, dp(18), 0, 0);
        form.addView(create, createParams);
        root.addView(form);

        Button back = authOutlineButton("이미 계정이 있어요");
        back.setOnClickListener(v -> showLoginScreen());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private void showForgotPasswordScreen() {
        root = oceanScrollableRoot(dp(22), dp(18), dp(22), dp(30));

        Button backTop = authTextButton("‹  로그인");
        backTop.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        backTop.setOnClickListener(v -> showLoginScreen());
        root.addView(backTop, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(authWaveMark(), new LinearLayout.LayoutParams(-1, dp(80)));

        TextView heading = authTitle("Reset your password");
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        TextView description = authBody("가입한 이메일을 입력하면 안전한 일회용 재설정 링크를 보내드립니다.");
        description.setGravity(Gravity.CENTER);
        description.setPadding(dp(10), 0, dp(10), dp(22));
        root.addView(description);

        LinearLayout form = authCard();
        EditText email = authInputField("email@example.com", store.getAccountEmail());
        form.addView(authLabel("가입 이메일"));
        form.addView(email, new LinearLayout.LayoutParams(-1, dp(52)));
        Button request = authPrimaryButton("재설정 안내 보내기");
        request.setOnClickListener(v -> {
            String value = email.getText().toString().trim();
            if (value.isEmpty() || !value.contains("@")) {
                toast("올바른 이메일을 입력해 주세요.");
                return;
            }
            viewModel.requestPasswordReset(value);
        });
        LinearLayout.LayoutParams requestParams = new LinearLayout.LayoutParams(-1, dp(54));
        requestParams.setMargins(0, dp(18), 0, 0);
        form.addView(request, requestParams);
        root.addView(form);

        Button back = authOutlineButton("로그인으로 돌아가기");
        back.setOnClickListener(v -> showLoginScreen());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private void showOnboarding() {
        if (!store.hasCloudSession()) {
            showLoginScreen();
            return;
        }
        root = oceanScrollableRoot(dp(22), dp(18), dp(22), dp(30));
        root.addView(authWaveMark(), new LinearLayout.LayoutParams(-1, dp(78)));

        TextView heading = authTitle("나의 스마트 해도 만들기");
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        TextView description = authBody("연령, 관심 분야, 학습 목적과 현재 수준을 선택하면 첫 번째 맞춤 항로를 설계합니다.");
        description.setGravity(Gravity.CENTER);
        description.setPadding(dp(8), 0, dp(8), dp(20));
        root.addView(description);

        Spinner age = spinner(new String[]{"초등학생", "중학생", "고등학생", "대학생", "성인", "직장인", "학부모/가족"});
        Spinner interest = spinner(new String[]{"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"});
        Spinner goal = spinner(new String[]{"흥미", "체험", "진로탐색", "자격증", "직무역량 강화", "가족 교육"});
        Spinner level = spinner(new String[]{"입문", "기초", "중급", "심화", "실무"});

        LinearLayout form = authCard();
        form.addView(authLabel("연령대")); form.addView(age);
        form.addView(authLabel("관심 분야")); form.addView(interest);
        form.addView(authLabel("학습 목적")); form.addView(goal);
        form.addView(authLabel("현재 수준")); form.addView(level);

        Button start = authPrimaryButton("나의 해양 인재 DNA 시작하기");
        start.setOnClickListener(v -> {
            String a = age.getSelectedItem().toString();
            String i = interest.getSelectedItem().toString();
            String g = goal.getSelectedItem().toString();
            String l = level.getSelectedItem().toString();
            store.saveProfile(new UserProfile(a, i, g, l, RecommendationEngine.persona(a, g, i), 0));
            if (store.requiresGuardianConsent()) {
                showGuardianConsentDialog(true);
            } else {
                if (viewModel.isCloudConfigured()) viewModel.syncNow();
                showApp(0);
            }
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(54));
        startParams.setMargins(0, dp(18), 0, 0);
        form.addView(start, startParams);
        root.addView(form);
    }

    private void showApp(int tab) {
        if (!store.hasCloudSession()) {
            showLoginScreen();
            return;
        }
        if (!store.hasProfile()) {
            showOnboarding();
            return;
        }
        if (store.requiresGuardianConsent() && !store.hasGuardianConsent()) {
            if (!guardianDialogVisible) showGuardianConsentDialog(true);
            return;
        }
        cancelQuizTimer();
        quizTimerText = null;
        currentTab = tab;
        conversationScrollAnchor = null;
        applyAppWindow();
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundResource(R.drawable.bg_app_surface);
        setContentView(appRoot);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundResource(R.drawable.bg_app_surface);
        appRoot.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(12), dp(14), dp(12), dp(12));
        header.setBackgroundResource(R.drawable.bg_ocean_header);

        LinearLayout headerRow = row();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        Button menu = outlineButton("☰");
        menu.setTextSize(20);
        menu.setOnClickListener(v -> openSidebar());
        headerRow.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(42)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);

        LinearLayout brandTitle = row();
        brandTitle.setGravity(
                Gravity.START | Gravity.CENTER_VERTICAL
        );

        TextView headerWave = createWaveMark(
                30,
                Color.WHITE,
                Gravity.CENTER
        );
        brandTitle.addView(
                headerWave,
                new LinearLayout.LayoutParams(dp(36), dp(34))
        );

        TextView h = new TextView(this);
        h.setText("BluePath");
        h.setTextColor(Color.WHITE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(dp(4), 0, 0, 0);
        brandTitle.addView(
                h,
                new LinearLayout.LayoutParams(-2, -2)
        );
        brand.addView(brandTitle);
        TextView sub = new TextView(this);
        sub.setText(tabTitle(tab) + " · 데이터 기반 해양 항로");
        sub.setTextColor(Color.parseColor("#C9FFFF"));
        sub.setTextSize(11);
        sub.setPadding(0, dp(2), 0, 0);
        brand.addView(sub);
        headerRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        Button manual = outlineButton("승급 기준");
        manual.setTextSize(11);
        manual.setOnClickListener(v -> showPromotionManual());
        headerRow.addView(manual, new LinearLayout.LayoutParams(dp(88), dp(38)));
        header.addView(headerRow);
        main.addView(header);

        if (tab == 2 && !quizSubmitted && !activeQuiz.isEmpty()
                && quizDeadlineElapsedRealtime > 0L) {
            addPinnedQuizTimer(main);
        }

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        // 커뮤니티에서는 우측 하단 글쓰기 버튼과 게시글이 겹치지 않도록 여백을 확보합니다.
        content.setPadding(dp(14), dp(12), dp(14), tab == 5 ? dp(104) : dp(22));

        ScrollView scroll = tab == 5
                ? new CommunityRefreshScrollView()
                : new ScrollView(this);
        contentScroll = scroll;
        scroll.setFillViewport(true);
        scroll.addView(content);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 커뮤니티 탭에서만 우측 하단 플로팅 글쓰기 버튼을 표시합니다.
        if (tab == 5) addCommunityWriteFab();

        sidebarScrim = new View(this);
        sidebarScrim.setBackgroundColor(Color.parseColor("#7706223F"));
        sidebarScrim.setVisibility(View.GONE);
        sidebarScrim.setOnClickListener(v -> closeSidebar());
        appRoot.addView(sidebarScrim, new FrameLayout.LayoutParams(-1, -1));

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(16), dp(22), dp(16), dp(20));
        sidebar.setBackgroundResource(R.drawable.bg_sidebar);
        sidebar.setElevation(dp(14));
        sidebar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sidebarParams = new FrameLayout.LayoutParams(dp(286), -1, Gravity.START);
        appRoot.addView(sidebar, sidebarParams);

        LinearLayout sideHead = row();
        sideHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout sideBrand = new LinearLayout(this);
        sideBrand.setOrientation(LinearLayout.VERTICAL);
        sideBrand.addView(big("BLUEPATH"));
        sideBrand.addView(label("나의 해양 스마트 해도"));
        sideHead.addView(sideBrand, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = outlineButton("×");
        close.setTextSize(22);
        close.setOnClickListener(v -> closeSidebar());
        sideHead.addView(close, new LinearLayout.LayoutParams(dp(44), dp(40)));
        sidebar.addView(sideHead);

        TextView navGuide = label("탭별 기능 안내");
        navGuide.setPadding(dp(4), dp(14), dp(4), dp(6));
        sidebar.addView(navGuide);

        ScrollView navScroll = new ScrollView(this);
        navScroll.setFillViewport(true);
        navScroll.setVerticalScrollBarEnabled(false);
        navScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout navList = new LinearLayout(this);
        navList.setOrientation(LinearLayout.VERTICAL);
        navList.setPadding(0, 0, 0, dp(8));

        addNavButton(
                navList,
                "✦  AI 진로 상담",
                4,
                "직무·전공·자격·프로젝트를 자유롭게 질문하고, 내 프로필과 앱 학습 자료 및 가능한 웹 근거를 반영한 맞춤 답변을 확인합니다. NCS 기반 추천 직무와 단계별 준비 항로도 함께 살펴볼 수 있습니다."
        );
        addNavButton(
                navList,
                "⌂  홈",
                0,
                "티어·XP·연도별 활동 기록을 한눈에 보고 AI 추천 학습 자료와 교육 일정을 확인합니다. 목표 직무별 맞춤 항로 생성, 미래 효과 시뮬레이션, 재항해와 QR 가족 협동 미션도 이용할 수 있습니다."
        );
        addNavButton(
                navList,
                "▶  학습 자료",
                1,
                "문장형 AI 검색으로 필요한 해양 자료를 찾고 영상·논문을 구분해 탐색합니다. 난도, 권장 티어, 연결 진로와 추천 근거를 확인하고 찜, 시청, 학습 완료 인증과 XP 기록을 관리합니다."
        );
        addNavButton(
                navList,
                "✓  퀴즈",
                2,
                "현재 티어에 맞는 제한 시간 퀴즈를 풀고 문항별 정답, 해설, 점수, XP와 승급 결과를 확인합니다. 주제별 강점과 부족한 역량은 다음 학습 및 진로 추천에 반영됩니다."
        );
        addNavButton(
                navList,
                "◷  일정",
                3,
                "지역·대상·시기·주제를 문장으로 검색하거나 태그와 모집 상태로 교육·행사를 필터링합니다. 월별 달력, 날짜별 일정, 온라인 활동, 찜과 기기 캘린더 추가 기능을 제공합니다."
        );
        addNavButton(
                navList,
                "≋  해양 커뮤니티",
                5,
                "자유·질문 게시판에서 글을 작성하고 최신 목록을 새로고침합니다. 작성자 팔로우, 게시글·댓글 공감, 댓글과 답글을 통해 다른 사용자와 학습 경험과 질문을 나눌 수 있습니다."
        );
        addNavButton(
                navList,
                "●  MY",
                6,
                "프로필, 티어, XP와 활동 통계를 관리하고 Ocean Skill Map에서 분야별 숙련도와 근거를 확인합니다. 포트폴리오 PDF, 완료·찜 기록, 알림, 동기화, 보호자 동의와 계정 설정도 관리합니다."
        );

        navScroll.addView(navList, new FrameLayout.LayoutParams(-1, -2));
        sidebar.addView(navScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderTab(tab);
    }

    private void addPinnedQuizTimer(LinearLayout main) {
        LinearLayout timerBar = row();
        timerBar.setGravity(Gravity.CENTER_VERTICAL);
        timerBar.setPadding(dp(18), dp(10), dp(18), dp(10));
        timerBar.setElevation(dp(6));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#E8FBFC"));
        background.setStroke(dp(1), Color.parseColor("#8DE4E1"));
        timerBar.setBackground(background);

        TextView label = new TextView(this);
        label.setText("퀴즈 제한 시간");
        label.setTextColor(NAVY);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        timerBar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        quizTimerText = new TextView(this);
        quizTimerText.setTextColor(OCEAN);
        quizTimerText.setTextSize(26);
        quizTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        quizTimerText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        timerBar.addView(quizTimerText, new LinearLayout.LayoutParams(-2, -1));

        long remaining = Math.max(0L, quizDeadlineElapsedRealtime - SystemClock.elapsedRealtime());
        updateQuizTimerText(remaining);
        main.addView(timerBar, new LinearLayout.LayoutParams(-1, dp(68)));
    }

    private String tabTitle(int tab) {
        switch (tab) {
            case 1: return "학습 자료";
            case 2: return "퀴즈";
            case 3: return "일정";
            case 4: return "AI 진로 상담";
            case 5: return "해양 커뮤니티";
            case 6: return "MY";
            default: return "홈";
        }
    }

    private void openSidebar() {
        if (sidebar == null || sidebarScrim == null) return;
        sidebarScrim.setAlpha(0f);
        sidebarScrim.setVisibility(View.VISIBLE);
        sidebarScrim.animate().alpha(1f).setDuration(180).start();
        sidebar.setTranslationX(-dp(286));
        sidebar.setVisibility(View.VISIBLE);
        sidebar.animate().translationX(0f).setDuration(220).start();
    }

    private void closeSidebar() {
        if (sidebar == null || sidebarScrim == null || sidebar.getVisibility() != View.VISIBLE) return;
        sidebarScrim.animate().alpha(0f).setDuration(160).withEndAction(() -> sidebarScrim.setVisibility(View.GONE)).start();
        sidebar.animate().translationX(-dp(286)).setDuration(190).withEndAction(() -> sidebar.setVisibility(View.GONE)).start();
    }

    private void addNavButton(LinearLayout parent, String label, int tab, String description) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(4), dp(2), dp(4), dp(10));
        item.setBackgroundResource(tab == currentTab
                ? R.drawable.bg_nav_selected
                : android.R.color.transparent);
        item.setOnClickListener(v -> showApp(tab));

        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), 0, dp(8), 0);
        b.setTextColor(tab == currentTab ? NAVY : TEXT);
        b.setTypeface(Typeface.DEFAULT, tab == currentTab ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> showApp(tab));
        item.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView detail = body(description);
        detail.setTextSize(10.5f);
        detail.setTextColor(tab == currentTab ? TEXT : MUTED);
        detail.setLineSpacing(dp(1), 1.05f);
        detail.setPadding(dp(12), 0, dp(10), 0);
        detail.setOnClickListener(v -> showApp(tab));
        item.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(2));
        parent.addView(item, params);
    }

    private void renderTab(int tab) {
        if (content == null) return;
        content.removeAllViews();
        switch (tab) {
            case 0: renderHome(); break;
            case 1: renderLearning(); break;
            case 2: renderQuiz(); break;
            case 3: renderSchedule(); break;
            case 4: renderCareer(); break;
            case 5: renderCommunity(); break;
            case 6: renderMyPage(); break;
        }
    }

    private void addTabIntro(String icon, String eyebrow, String titleText, String description) {
        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(dp(18), dp(16), dp(18), dp(16));
        intro.setBackgroundResource(R.drawable.bg_tab_intro);
        intro.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        intro.setLayoutParams(params);

        LinearLayout top = row();
        top.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        boolean hasIcon = icon != null && !icon.trim().isEmpty();
        if (hasIcon) {
            TextView iconView = new TextView(this);
            iconView.setText(icon);
            iconView.setTextSize(28);
            iconView.setGravity(Gravity.CENTER);
            top.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.START);
        copy.setPadding(hasIcon ? dp(10) : 0, 0, 0, 0);
        TextView eye = new TextView(this);
        eye.setText(eyebrow);
        eye.setTextColor(Color.parseColor("#9EF5F0"));
        eye.setTextSize(11);
        eye.setLetterSpacing(0.08f);
        eye.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(eye);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(3), 0, 0);
        copy.addView(title);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        intro.addView(top);

        intro.setLayoutTransition(new LayoutTransition());
        TextView toggle = new TextView(this);
        toggle.setText("사용 방법 안내 ▾");
        toggle.setTextColor(Color.parseColor("#9EF5F0"));
        toggle.setTextSize(12);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setPadding(0, dp(10), 0, dp(2));
        intro.addView(toggle);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(Color.parseColor("#D9FFFF"));
        desc.setTextSize(13);
        desc.setLineSpacing(dp(2), 1.05f);
        desc.setPadding(0, dp(6), 0, 0);
        desc.setVisibility(View.GONE);
        intro.addView(desc);

        toggle.setOnClickListener(v -> {
            boolean open = desc.getVisibility() == View.VISIBLE;
            desc.setVisibility(open ? View.GONE : View.VISIBLE);
            toggle.setText(open ? "사용 방법 안내 ▾" : "사용 방법 안내 ▴");
        });
        content.addView(intro);
    }

    private void renderHome() {
        addTabIntro(
                "",
                "TODAY'S ROUTE",
                "홈 · 오늘의 항로",
                "나의 티어·XP와 연도별 활동 기록을 한눈에 확인하고, 관심 분야와 목표에 맞춘 AI 학습 자료와 교육 일정을 추천받아보세요.\n\n"
                        + "목표 직무별 맞춤 항로를 생성해 활동 전후의 역량과 진로 준비도 변화를 미리 살펴보고, 필요하면 일정과 난이도에 맞게 항로를 다시 조정할 수 있습니다. "
                        + "박물관에서는 QR 가족 협동 미션에 참여해 현장 활동과 획득 역량까지 하나의 해양 커리어 여정으로 기록할 수 있습니다."
        );
        maybeRefreshDashboard();
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        int xp = p.xp;
        int base = UserStore.tierBaseXp(tier);
        int next = UserStore.nextTierXp(tier);
        int progress = "다이아".equals(tier) ? 100 : Math.min(100, Math.max(0, (xp - base) * 100 / Math.max(1, next - base)));

        LinearLayout hero = card();
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(profileAvatar(store.getNickname(), store.getProfileImageUrl(), dp(68)), new LinearLayout.LayoutParams(dp(68), dp(68)));
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setPadding(dp(12), 0, dp(8), 0);
        heroText.addView(label("MY OCEAN PROFILE"));
        heroText.addView(big(store.getNickname()));
        heroText.addView(body("팔로워 " + store.getFollowerCount() + " · 팔로잉 " + store.getFollowingCount()));
        heroText.addView(body(p.interest + " · " + p.goal + " · " + p.level));
        top.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        TierShieldView shield = tierShield(tier);
        top.addView(shield, new LinearLayout.LayoutParams(dp(92), dp(104)));
        hero.addView(top);
        hero.addView(big(plainTierText(tier) + " · XP " + xp));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(progress);
        hero.addView(bar, new LinearLayout.LayoutParams(-1, dp(18)));
        hero.addView(body("현재 티어 진행도 " + progress + "%" + ("다이아".equals(tier) ? " · 최고 티어" : " · 다음 기준 XP " + next)));
        content.addView(hero);



        content.addView(sectionTitle("나의 활동"));
        LinearLayout heatCard = card();
        int currentActivityYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
        int joinedActivityYear = store.getAccountJoinedYear();
        if (selectedActivityYear < joinedActivityYear || selectedActivityYear > currentActivityYear) {
            selectedActivityYear = currentActivityYear;
        }

        TextView heatmapTitle = big(selectedActivityYear + "년 활동");
        heatCard.addView(heatmapTitle);
        heatCard.addView(body("하루 한 번의 학습과 참여가 나만의 해양 커리어를 만듭니다. 오늘도 작은 활동 하나를 기록해 보세요."));
        ActivityHeatmapView heatmap = new ActivityHeatmapView(this);
        heatmap.setYear(selectedActivityYear);
        heatmap.setActivity(store.getActivityCounts());
        HorizontalScrollView heatmapScroll = new HorizontalScrollView(this);
        heatmapScroll.setHorizontalScrollBarEnabled(true);
        heatmapScroll.setFillViewport(false);
        heatmapScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        heatmapScroll.addView(heatmap, new FrameLayout.LayoutParams(-2, -2));
        heatCard.addView(heatmapScroll, new LinearLayout.LayoutParams(-1, dp(188)));
        heatCard.addView(label("연도별 보기"));

        HorizontalScrollView yearScroll = new HorizontalScrollView(this);
        yearScroll.setHorizontalScrollBarEnabled(false);
        yearScroll.setFillViewport(false);
        LinearLayout yearRow = new LinearLayout(this);
        yearRow.setOrientation(LinearLayout.HORIZONTAL);
        yearRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        for (int year = joinedActivityYear; year <= currentActivityYear; year++) {
            final int targetYear = year;
            Button yearButton = outlineButton(String.valueOf(year));
            yearButton.setTag(year);
            styleActivityYearButton(yearButton, year == selectedActivityYear);
            yearButton.setOnClickListener(v -> {
                selectedActivityYear = targetYear;
                heatmapTitle.setText(targetYear + "년 활동");
                heatmap.setYear(targetYear);
                for (int i = 0; i < yearRow.getChildCount(); i++) {
                    View child = yearRow.getChildAt(i);
                    if (child instanceof Button && child.getTag() instanceof Integer) {
                        styleActivityYearButton((Button) child, ((Integer) child.getTag()) == targetYear);
                    }
                }
                heatmapScroll.post(() -> {
                    if (targetYear == currentActivityYear) heatmapScroll.fullScroll(View.FOCUS_RIGHT);
                    else heatmapScroll.scrollTo(0, 0);
                });
            });
            LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(dp(82), dp(40));
            yearParams.setMargins(0, dp(4), dp(8), dp(2));
            yearRow.addView(yearButton, yearParams);
        }
        yearScroll.addView(yearRow, new FrameLayout.LayoutParams(-2, -2));
        heatCard.addView(yearScroll, new LinearLayout.LayoutParams(-1, dp(48)));
        heatmapScroll.post(() -> {
            if (selectedActivityYear == currentActivityYear) heatmapScroll.fullScroll(View.FOCUS_RIGHT);
        });
        content.addView(heatCard);

        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "학습 완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "퀴즈 응시"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getBookmarks().size()), "찜"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        addVoyageTwinSection(p);
        addFamilyMissionSection(p);

        content.addView(sectionTitle("AI 추천 학습 자료"));
        List<ContentItem> items = RecommendationEngine.recommendedContents(p, tier, store);
        for (int i = 0; i < Math.min(3, items.size()); i++) addContentCard(items.get(i), true);


        content.addView(sectionTitle("추천 교육 일정"));
        List<ProgramItem> programs = RecommendationEngine.recommendedPrograms(p, store);
        for (int i = 0; i < Math.min(2, programs.size()); i++) addProgramCard(programs.get(i));

        List<String> insights = DataRepository.surveyInsights();
        if (!insights.isEmpty()) {
            content.addView(sectionTitle("관람객 데이터 기반 추천 근거"));
            LinearLayout insightCard = card();
            insightCard.addView(big("실제 관람객 " + DataRepository.surveySampleSize() + "명 응답 분석"));
            LinearLayout insightPanel = addExpandable(insightCard, "분석 인사이트 " + insights.size() + "개");
            for (String insight : insights) insightPanel.addView(body("• " + insight));
            content.addView(insightCard);
        }
    }

    private void addVoyageTwinSection(UserProfile profile) {
        content.addView(sectionTitle("PERSONAL OCEAN CAREER ROUTE · 나의 해양인재 항로"));
        LinearLayout voyage = card();
        voyage.addView(label("AI SMART NAUTICAL CHART"));
        voyage.addView(big("목표 직무와 현재 실력, 활동 기록에 맞춰 AI가 학습·체험·퀴즈 순서를 만들고 새롭게 갱신해볼 수 있습니다."));
        Spinner career = spinner(new String[]{
                "해양환경 교육 기획자", "해양생태 해설사", "항해사", "항만 물류 운영자",
                "자율운항선박 엔지니어", "해양문화 콘텐츠 기획자"
        });
        Spinner routeType = spinner(new String[]{
                "균형 항로", "가장 빠른 항로", "체험 중심 항로", "가족과 함께하는 항로",
                "취업 준비 항로", "주말 전용 항로", "무료 프로그램 우선 항로"
        });
        setSpinnerSelection(career, store.getTargetCareer());
        setSpinnerSelection(routeType, routeTypeLabel(store.getRouteType()));
        voyage.addView(label("목표 항구"));
        voyage.addView(career);
        voyage.addView(label("항해 방식"));
        voyage.addView(routeType);
        Button generate = primaryButton(routeLoading ? "AI가 항로를 계산하는 중…" : "AI 항로 생성·갱신");
        generate.setEnabled(!routeLoading && !routeRerouting);
        generate.setOnClickListener(v -> requestRoute(
                career.getSelectedItem().toString(), routeTypeCode(routeType.getSelectedItem().toString())));
        voyage.addView(generate, new LinearLayout.LayoutParams(-1, dp(48)));

        if (routeLoading || routeRerouting) {
            ProgressBar progress = new ProgressBar(this);
            voyage.addView(progress, new LinearLayout.LayoutParams(-1, dp(42)));
            voyage.addView(body(routeRerouting
                    ? "마감·시간·난이도 제약을 반영해 대체 항로를 탐색하고 있습니다."
                    : "현재 숙련도와 이력, 교육 일정, NCS 역량을 매핑하고 있습니다."));
        }
        if (!routeError.isEmpty()) voyage.addView(note("항로 불러오기: " + routeError, DANGER));

        if (currentRoute != null) {
            voyage.addView(big(currentRoute.targetCareer + " 항로"));
            voyage.addView(body(safe(currentRoute.summary)));
            LinearLayout readiness = row();
            readiness.addView(statCard(currentRoute.readinessBefore + "%", "현재 준비도"), new LinearLayout.LayoutParams(0, -2, 1));
            readiness.addView(statCard(currentRoute.readinessAfter + "%", "완료 예상"), new LinearLayout.LayoutParams(0, -2, 1));
            readiness.addView(statCard(currentRoute.estimatedMinutes + "분", "예상 소요"), new LinearLayout.LayoutParams(0, -2, 1));
            voyage.addView(readiness);
            voyage.addView(note("AI 코치 · " + safe(currentRoute.coachMessage), OCEAN));
            voyage.addView(label("현재 위치 · " + currentRoute.currentSkillTopic + " 숙련도 "
                    + currentRoute.currentMastery + " · " + plainTierText(currentRoute.tier)));

            if (currentRoute.nodes != null) {
                for (int i = 0; i < currentRoute.nodes.size(); i++) {
                    if (i > 0) {
                        TextView connector = body("↓  다음 항로");
                        connector.setGravity(Gravity.CENTER);
                        connector.setTextColor(OCEAN);
                        voyage.addView(connector);
                    }
                    addVoyageNode(voyage, currentRoute.nodes.get(i));
                }
            }

            if (store.hasPendingReroute()) {
                voyage.addView(note("자동 재항해 준비 완료 · " + safeOr(store.getPendingRerouteSummary(),
                        "최근 활동을 반영한 짧은 대체 항로가 준비되었습니다."), SUCCESS));
                Button acceptPending = primaryButton(routeRerouting ? "대체 항로 적용 중…" : "준비된 대체 항로 적용");
                acceptPending.setEnabled(!routeLoading && !routeRerouting);
                acceptPending.setOnClickListener(v -> acceptPendingReroute());
                voyage.addView(acceptPending, new LinearLayout.LayoutParams(-1, dp(48)));
            } else if (store.daysSinceRouteActivity() >= 3) {
                voyage.addView(note("자동 재항해 확인 중 · 최근 " + store.daysSinceRouteActivity()
                        + "일 동안 활동이 없어 백그라운드에서 대체 항로를 준비합니다.", DANGER));
            }
            Button reroute = outlineButton(routeRerouting ? "재항해 중…" : "마감·시간 부족으로 수동 재항해");
            reroute.setEnabled(!routeLoading && !routeRerouting);
            reroute.setOnClickListener(v -> showRerouteDialog());
            voyage.addView(reroute);

            if (currentRoute.sources != null && !currentRoute.sources.isEmpty()) {
                LinearLayout sourcePanel = addExpandable(voyage,
                        "항로 설명에 사용한 근거 " + currentRoute.sources.size() + "개");
                for (ApiModels.SourceDto source : currentRoute.sources) {
                    sourcePanel.addView(body("• " + safe(source.title) + (safe(source.organization).isEmpty()
                            ? "" : " · " + source.organization)));
                }
            }
        } else if (!routeLoading) {
            voyage.addView(body("항로를 생성하면 온라인 학습 → 박물관 체험 → 퀴즈 → 프로젝트 → NCS 직무가 순서대로 표시됩니다."));
        }
        content.addView(voyage);

        if (currentRoute == null && !routeLoading && !routeAttempted && viewModel.isCloudConfigured()) {
            routeAttempted = true;
            content.post(() -> requestRoute(store.getTargetCareer(), store.getRouteType()));
        }
    }

    private void addVoyageNode(LinearLayout parent, ApiModels.RouteNodeDto node) {
        LinearLayout nodeCard = card();
        nodeCard.addView(label("STEP " + node.order + " · " + routeNodeTypeLabel(node.nodeType)
                + " · " + safeOr(node.availabilityLabel, "일정 확인")));
        nodeCard.addView(big(safe(node.title)));
        nodeCard.addView(body(safe(node.description)));
        nodeCard.addView(note(node.topic + " +" + node.expectedSkillGain + " · 직무 준비도 +"
                + node.readinessGain + " · " + node.minutes + "분", SUCCESS));
        if (node.ncsCompetencies != null && !node.ncsCompetencies.isEmpty()) {
            nodeCard.addView(body("NCS 연결 · " + joinList(node.ncsCompetencies, " · ")));
        }
        nodeCard.addView(body("왜 이 순서인가 · " + safe(node.whyThisOrder)));
        addReasonList(nodeCard, node.recommendationReasons);
        if (node.evidenceBasis != null && !node.evidenceBasis.isEmpty()) {
            LinearLayout evidencePanel = addExpandable(nodeCard,
                    "데이터 근거 " + node.evidenceBasis.size() + "개");
            for (String evidence : node.evidenceBasis) {
                evidencePanel.addView(body("• " + evidence));
            }
        }
        LinearLayout actions = row();
        Button action = primaryButton(safeOr(node.actionLabel, "시작하기"));
        action.setOnClickListener(v -> handleRouteNodeAction(node));
        actions.addView(action, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button simulate = outlineButton("미래 효과");
        simulate.setOnClickListener(v -> requestRouteSimulation(node));
        LinearLayout.LayoutParams simParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        simParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(simulate, simParams);
        nodeCard.addView(actions);
        parent.addView(nodeCard);
    }

    private void requestRoute(String targetCareer, String routeType) {
        if (!viewModel.isCloudConfigured()) {
            routeError = "서버 연결 설정이 필요합니다.";
            if (currentTab == 0) renderTab(0);
            return;
        }
        routeLoading = true;
        routeRerouting = false;
        routeError = "";
        routeAttempted = true;
        store.saveVoyagePreferences(targetCareer, routeType);
        if (currentTab == 0) renderTab(0);
        executor.execute(() -> {
            try {
                ApiModels.RoutePlanResponse response = cloudRepository.planRoute(targetCareer, routeType);
                runOnUiThread(() -> {
                    currentRoute = response;
                    routeLoading = false;
                    routeError = "";
                    store.touchRouteActivity();
                    if (currentTab == 0) renderTab(0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    routeLoading = false;
                    routeError = safeMessage(e);
                    if (currentTab == 0) renderTab(0);
                });
            }
        });
    }

    private void requestRouteSimulation(ApiModels.RouteNodeDto node) {
        if (currentRoute == null) return;
        toast("미래 항로를 시뮬레이션합니다…");
        executor.execute(() -> {
            try {
                ApiModels.RouteSimulationResponse result = cloudRepository.simulateRoute(currentRoute.routeId, node);
                runOnUiThread(() -> showRouteSimulationDialog(result));
            } catch (Exception e) {
                runOnUiThread(() -> toast("시뮬레이션 실패: " + safeMessage(e)));
            }
        });
    }

    private void showRouteSimulationDialog(ApiModels.RouteSimulationResponse result) {
        StringBuilder message = new StringBuilder();
        message.append("현재\n")
                .append(result.skillTopic).append(" 숙련도 ").append(result.masteryBefore).append("점\n")
                .append("목표 직무 준비도 ").append(result.readinessBefore).append("%\n")
                .append("취약 항목 ").append(result.weakItemsBefore).append("개\n\n")
                .append("완료 예상 결과\n")
                .append(result.skillTopic).append(" ").append(result.masteryBefore).append(" → ")
                .append(result.masteryAfter).append("점\n")
                .append("준비도 ").append(result.readinessBefore).append(" → ")
                .append(result.readinessAfter).append("%\n")
                .append("취약 항목 ").append(result.weakItemsBefore).append(" → ")
                .append(result.weakItemsAfter).append("개\n\n")
                .append(safe(result.explanation)).append("\n\n")
                .append("다음 추천 · ").append(safe(result.nextRecommendation))
                .append("\n예측 신뢰도 · ").append(result.confidence).append("%");
        new AlertDialog.Builder(this)
                .setTitle("미래 항로 시뮬레이터 · " + safe(result.activityTitle))
                .setMessage(message.toString())
                .setNegativeButton("닫기", null)
                .setPositiveButton("이 활동 시작", (dialog, which) -> {
                    if (currentRoute == null || currentRoute.nodes == null) return;
                    for (ApiModels.RouteNodeDto node : currentRoute.nodes) {
                        if (safe(node.title).equals(safe(result.activityTitle))) {
                            handleRouteNodeAction(node);
                            break;
                        }
                    }
                }).show();
    }

    private void acceptPendingReroute() {
        String pendingId = store.getPendingRerouteId();
        if (pendingId == null || pendingId.trim().isEmpty()) return;
        routeRerouting = true;
        routeError = "";
        if (currentTab == 0) renderTab(0);
        executor.execute(() -> {
            try {
                ApiModels.RoutePlanResponse response = cloudRepository.activateRoute(pendingId);
                runOnUiThread(() -> {
                    currentRoute = response;
                    store.clearPendingReroute();
                    store.touchRouteActivity();
                    routeRerouting = false;
                    if (currentTab != 0) showApp(0); else renderTab(0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    routeRerouting = false;
                    routeError = safeMessage(e);
                    if (currentTab == 0) renderTab(0);
                });
            }
        });
    }

    private void showRerouteDialog() {
        if (currentRoute == null) return;
        String[] labels = {"신청 마감", "시간 부족", "난이도가 높음", "주말에만 가능", "무료 활동 우선"};
        String[] reasons = {"closed", "time_shortage", "too_difficult", "weekend_only", "free_only"};
        new AlertDialog.Builder(this)
                .setTitle("왜 재항해가 필요한가요?")
                .setItems(labels, (dialog, which) -> {
                    String blockedNode = null;
                    if (currentRoute.nodes != null) {
                        for (ApiModels.RouteNodeDto node : currentRoute.nodes) {
                            if ("closed".equals(node.scheduleStatus)) {
                                blockedNode = node.id;
                                break;
                            }
                        }
                    }
                    requestReroute(blockedNode, reasons[which]);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void requestReroute(String blockedNodeId, String reason) {
        if (currentRoute == null) return;
        routeRerouting = true;
        routeError = "";
        if (currentTab == 0) renderTab(0);
        executor.execute(() -> {
            try {
                ApiModels.RoutePlanResponse response = cloudRepository.reroute(currentRoute.routeId, blockedNodeId, reason);
                runOnUiThread(() -> {
                    currentRoute = response;
                    routeRerouting = false;
                    store.touchRouteActivity();
                    toast("제약을 반영해 새로운 항로를 만들었습니다.");
                    if (currentTab == 0) renderTab(0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    routeRerouting = false;
                    routeError = safeMessage(e);
                    if (currentTab == 0) renderTab(0);
                });
            }
        });
    }

    private void handleRouteNodeAction(ApiModels.RouteNodeDto node) {
        store.touchRouteActivity();
        viewModel.recordLearning("route_" + node.nodeType, node.targetId, node.title, "started");
        executor.execute(() -> {
            try {
                if (currentRoute != null) cloudRepository.recordRouteOutcome(currentRoute.routeId, node.id, "started");
            } catch (Exception ignored) {
            }
        });
        if ("video".equals(node.nodeType)) {
            store.markContentStarted(node.targetId);
            if (!safe(node.actionUrl).isEmpty()) openUrl(node.actionUrl); else showApp(1);
        } else if ("event".equals(node.nodeType)) {
            toast("현장 전시 QR을 스캔해 미션을 시작해 주세요.");
            launchMissionQrScanner();
        } else if ("program".equals(node.nodeType) || "schedule".equals(node.nodeType)) {
            showApp(3);
        } else if ("quiz".equals(node.nodeType)) {
            showApp(2);
        } else {
            showApp(4);
        }
    }

    private void addFamilyMissionSection(UserProfile profile) {
        content.addView(sectionTitle("박물관 현장 연동 · 가족 협동 미션"));
        LinearLayout mission = card();
        mission.addView(label("QR EXHIBIT MISSION"));
        mission.addView(big("현장의 QR을 스캔해 미션 참여를 인증하고, 완료한 활동과 얻은 역량을 안전하게 기록해볼 수 있습니다."));        mission.addView(body("현장 QR의 전시 코드·세션·만료시각·일회용 nonce·서명을 서버가 검증한 뒤 역할별 미션을 생성합니다. 최초 인증 때만 역량과 배지가 지급됩니다."));
        if (missionLoading) {
            mission.addView(new ProgressBar(this), new LinearLayout.LayoutParams(-1, dp(42)));
            mission.addView(body("전시 맥락과 가족 구성에 맞는 역할을 설계하고 있습니다."));
        }
        if (!missionError.isEmpty()) mission.addView(note("미션 불러오기: " + missionError, DANGER));

        if (currentMission == null) {
            Button generate = primaryButton(missionLoading ? "QR 확인 중…" : "현장 QR 스캔");
            generate.setEnabled(!missionLoading);
            generate.setOnClickListener(v -> launchMissionQrScanner());
            mission.addView(generate, new LinearLayout.LayoutParams(-1, dp(48)));
            mission.addView(body("카메라로 박물관이 발급한 서명 QR을 스캔해야 미션이 생성됩니다. 텍스트 입력이나 하드코딩 전시 코드는 인증에 사용할 수 없습니다."));
        } else {
            mission.addView(big(currentMission.title));
            mission.addView(body(currentMission.story));
            if (currentMission.roles != null) {
                for (ApiModels.MissionRole role : currentMission.roles) {
                    mission.addView(note(role.name + " · " + role.audience, OCEAN));
                    mission.addView(body(role.task));
                }
            }
            mission.addView(label("공동 미션"));
            mission.addView(body(currentMission.jointTask));
            if (currentMission.expectedSkillGains != null && !currentMission.expectedSkillGains.isEmpty()) {
                List<String> gains = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : currentMission.expectedSkillGains.entrySet()) {
                    gains.add(entry.getKey() + " +" + entry.getValue());
                }
                mission.addView(note("완료 예상 · " + joinList(gains, " · ") + " · " + currentMission.badge, SUCCESS));
            }
            mission.addView(body("안전 안내 · " + currentMission.safetyNote));
            Button verify = primaryButton("QR 미션 완료 인증");
            verify.setEnabled(currentQrPayload != null && !missionLoading);
            verify.setOnClickListener(v -> showMissionVerificationDialog());
            mission.addView(verify, new LinearLayout.LayoutParams(-1, dp(48)));
            Button regenerate = outlineButton("새 현장 QR 스캔");
            regenerate.setOnClickListener(v -> {
                currentMission = null;
                currentQrPayload = null;
                launchMissionQrScanner();
            });
            mission.addView(regenerate);
        }
        if (!store.getMissionBadges().isEmpty()) {
            mission.addView(label("획득한 현장 배지"));
            mission.addView(body(joinList(new ArrayList<>(store.getMissionBadges()), " · ")));
        }
        content.addView(mission);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerMissionQrScanner() {
        try {
            Class<?> contractClass = Class.forName("com.journeyapps.barcodescanner.ScanContract");
            Object contract = contractClass.getDeclaredConstructor().newInstance();
            androidx.activity.result.contract.ActivityResultContract<Object, Object> typedContract =
                    (androidx.activity.result.contract.ActivityResultContract<Object, Object>) contract;
            qrScanner = registerForActivityResult(typedContract, this::handleMissionQrResult);
        } catch (Exception exception) {
            qrScanner = null;
        }
    }

    private void launchMissionQrScanner() {
        if (qrScanner == null) {
            toast("QR 스캐너를 초기화하지 못했습니다. Gradle 동기화 후 다시 실행해 주세요.");
            return;
        }
        try {
            Class<?> optionsClass = Class.forName("com.journeyapps.barcodescanner.ScanOptions");
            Object options = optionsClass.getDeclaredConstructor().newInstance();
            optionsClass.getMethod("setPrompt", String.class)
                    .invoke(options, "박물관 전시 QR을 사각형 안에 맞춰 주세요.");
            optionsClass.getMethod("setBeepEnabled", boolean.class).invoke(options, false);
            optionsClass.getMethod("setOrientationLocked", boolean.class).invoke(options, true);
            try {
                optionsClass.getMethod("setDesiredBarcodeFormats", String[].class)
                        .invoke(options, (Object) new String[]{"QR_CODE"});
            } catch (NoSuchMethodException exception) {
                optionsClass.getMethod("setDesiredBarcodeFormats", java.util.Collection.class)
                        .invoke(options, java.util.Collections.singletonList("QR_CODE"));
            }
            qrScanner.launch(options);
        } catch (Exception exception) {
            toast("QR 스캐너 실행 실패: " + safeMessage(exception));
        }
    }

    private void handleMissionQrResult(Object result) {
        if (result == null) return;
        try {
            Object contentsValue = result.getClass().getMethod("getContents").invoke(result);
            String contents = contentsValue == null ? "" : contentsValue.toString();
            if (contents.trim().isEmpty()) return;
            ApiModels.MissionQrPayload payload = parseMissionQrPayload(contents);
            currentQrPayload = payload;
            currentMission = null;
            showMissionParticipantDialog(payload);
        } catch (Exception exception) {
            currentQrPayload = null;
            currentMission = null;
            missionError = "유효한 BluePath 현장 QR이 아닙니다.";
            if (currentTab == 0) renderTab(0);
        }
    }

    private ApiModels.MissionQrPayload parseMissionQrPayload(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("QR payload is empty");
        }
        ApiModels.MissionQrPayload payload;
        try {
            payload = new com.google.gson.Gson().fromJson(raw, ApiModels.MissionQrPayload.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("QR payload is not valid JSON", exception);
        }
        if (payload == null
                || trimmedLength(payload.exhibitCode) < 2
                || trimmedLength(payload.sessionId) < 8
                || trimmedLength(payload.issuedAt) < 10
                || trimmedLength(payload.expiresAt) < 10
                || trimmedLength(payload.nonce) < 16
                || trimmedLength(payload.signature) < 32) {
            throw new IllegalArgumentException("QR payload is missing required signed fields");
        }
        if (payload.exhibitTitle == null || payload.exhibitTitle.trim().isEmpty()) {
            payload.exhibitTitle = payload.exhibitCode;
        }
        return payload;
    }

    private int trimmedLength(String value) {
        return value == null ? 0 : value.trim().length();
    }

    private void showMissionParticipantDialog(ApiModels.MissionQrPayload payload) {
        String[] labels = {"2명", "3명", "4명", "5명", "6명"};
        int selected = Math.max(0, Math.min(labels.length - 1, missionParticipantCount - 2));
        new AlertDialog.Builder(this)
                .setTitle("가족 미션 참여 인원")
                .setSingleChoiceItems(labels, selected, null)
                .setMessage("실제로 함께 수행할 인원을 선택하면 역할과 공동 과제가 인원수에 맞게 생성됩니다.")
                .setNegativeButton("취소", (dialog, which) -> {
                    currentQrPayload = null;
                    currentMission = null;
                })
                .setPositiveButton("미션 생성", (dialog, which) -> {
                    int checked = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    missionParticipantCount = checked < 0 ? 2 : checked + 2;
                    requestFamilyMission(payload, missionParticipantCount);
                })
                .show();
    }

    private void requestFamilyMission(ApiModels.MissionQrPayload qrPayload, int participants) {
        if (!viewModel.isCloudConfigured()) {
            missionError = "서버 연결 설정이 필요합니다.";
            if (currentTab == 0) renderTab(0);
            return;
        }
        missionLoading = true;
        missionError = "";
        if (currentTab == 0) renderTab(0);
        executor.execute(() -> {
            try {
                ApiModels.FamilyMissionResponse response = cloudRepository.generateMission(qrPayload, participants);
                runOnUiThread(() -> {
                    currentMission = response;
                    missionLoading = false;
                    if (currentTab != 0) showApp(0); else renderTab(0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    missionLoading = false;
                    missionError = safeMessage(e);
                    if (currentTab == 0) renderTab(0);
                });
            }
        });
    }

    private void showMissionVerificationDialog() {
        if (currentMission == null) return;
        EditText note = inputField("가족이 발견한 단서나 완성한 결과물을 짧게 기록해 주세요.", "");
        note.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle("현장 미션 완료 인증")
                .setMessage("참여 인원 " + missionParticipantCount + "명으로 생성된 미션입니다. 스캔한 QR의 일회용 nonce와 서버 서명을 다시 검증하며, 활동 결과는 공백 제외 10자 이상 기록해 주세요.")
                .setView(note)
                .setNegativeButton("취소", null)
                .setPositiveButton("인증하기", (dialog, which) -> verifyCurrentMission(note.getText().toString(), missionParticipantCount))
                .show();
    }

    private void verifyCurrentMission(String completionNote, int participants) {
        if (currentMission == null || currentQrPayload == null) return;
        if (completionNote == null || completionNote.trim().length() < 10) {
            toast("활동 결과를 공백 제외 10자 이상 입력해 주세요.");
            return;
        }
        missionLoading = true;
        if (currentTab == 0) renderTab(0);
        executor.execute(() -> {
            try {
                ApiModels.MissionVerifyResponse response = cloudRepository.verifyMission(
                        currentMission.missionId, completionNote, participants, currentQrPayload);
                runOnUiThread(() -> {
                    missionLoading = false;
                    if (response.newlyVerified) {
                        store.addMissionBadge(response.badge);
                        store.touchRouteActivity();
                        viewModel.recordLearning("museum_mission", currentMission.missionId, currentMission.title, "completed_verified");
                        recordCurrentMissionRouteCompletion();
                    }
                    new AlertDialog.Builder(this)
                            .setTitle((response.newlyVerified ? "Skill Passport 인증 완료 · " : "이미 인증된 미션 · ") + response.badge)
                            .setMessage(response.message + "\n\n획득 역량 · "
                                    + competencyText(response.acquiredCompetencies)
                                    + "\n\n다음 추천 · " + response.nextRecommendation)
                            .setPositiveButton("확인", (dialog, which) -> renderTab(0))
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    missionLoading = false;
                    missionError = safeMessage(e);
                    if (currentTab == 0) renderTab(0);
                });
            }
        });
    }

    private void recordRouteCompletionByTarget(String targetId) {
        if (currentRoute == null || currentRoute.nodes == null || targetId == null) return;
        for (ApiModels.RouteNodeDto node : currentRoute.nodes) {
            if (!targetId.equals(node.targetId)) continue;
            node.completed = true;
            executor.execute(() -> {
                try {
                    cloudRepository.recordRouteOutcome(currentRoute.routeId, node.id, "completed");
                } catch (Exception ignored) {
                }
            });
            return;
        }
    }

    private void recordCurrentMissionRouteCompletion() {
        if (currentRoute == null || currentRoute.nodes == null || currentMission == null) return;
        for (ApiModels.RouteNodeDto node : currentRoute.nodes) {
            if (!"event".equals(node.nodeType)) continue;
            if (!safe(currentMission.title).contains(safe(node.title))) continue;
            node.completed = true;
            executor.execute(() -> {
                try {
                    cloudRepository.recordRouteOutcome(currentRoute.routeId, node.id, "completed");
                } catch (Exception ignored) {
                }
            });
            return;
        }
    }

    private void clearVoyageSession() {
        currentRoute = null;
        currentMission = null;
        currentQrPayload = null;
        routeLoading = false;
        routeRerouting = false;
        routeAttempted = false;
        routeError = "";
        missionLoading = false;
        missionError = "";
    }

    private String competencyText(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) return "현장 협업 증거";
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) result.add(entry.getKey() + " +" + entry.getValue());
        return joinList(result, " · ");
    }

    private String routeTypeLabel(String code) {
        if ("fastest".equals(code)) return "가장 빠른 항로";
        if ("experience".equals(code)) return "체험 중심 항로";
        if ("family".equals(code)) return "가족과 함께하는 항로";
        if ("career".equals(code)) return "취업 준비 항로";
        if ("weekend".equals(code)) return "주말 전용 항로";
        if ("free".equals(code)) return "무료 프로그램 우선 항로";
        return "균형 항로";
    }

    private String routeTypeCode(String label) {
        if (label.contains("가장 빠른")) return "fastest";
        if (label.contains("체험 중심")) return "experience";
        if (label.contains("가족")) return "family";
        if (label.contains("취업")) return "career";
        if (label.contains("주말")) return "weekend";
        if (label.contains("무료")) return "free";
        return "balanced";
    }

    private String routeNodeTypeLabel(String type) {
        if ("video".equals(type)) return "온라인 학습";
        if ("program".equals(type) || "schedule".equals(type)) return "교육 과정";
        if ("event".equals(type)) return "박물관 현장 미션";
        if ("quiz".equals(type)) return "맞춤 진단";
        if ("project".equals(type)) return "직무 프로젝트";
        if ("career".equals(type)) return "목표 항구";
        return "항로 활동";
    }

    private void renderLearning() {
        addTabIntro(
                "",
                "LEARNING LIBRARY",
                "학습 자료 · 맞춤 콘텐츠",
                "관심 분야, 현재 티어와 학습 목표에 맞는 해양 자료를 문장으로 검색하고, 영상과 논문 탭을 구분해 필요한 콘텐츠를 탐색해 보세요. "
                        + "AI 검색은 앱에 등록된 자료를 우선 활용하며, 가능한 경우 실시간 웹 근거까지 함께 검토해 결과와 요약을 보여줍니다.\n\n"
                        + "영상 탭에서는 입문·진로 탐색·직무 심화 난도별 라이브러리를 확인할 수 있습니다. 각 카드에서 권장 티어, 적합도, 출처, 소요 시간, 분야, 연결 진로와 추천 이유를 살펴보고, "
                        + "영상을 시작하거나 이어서 시청하고 찜 목록에 저장할 수 있습니다. 시청 후에는 핵심 내용을 제출해 학습 완료를 인증하고 XP와 역량 기록에 반영할 수 있습니다. 논문 탭에서는 저자·연도·학술지·DOI·초록을 확인하고 원문을 읽은 뒤 요약을 학습 증거로 저장할 수 있습니다."
        );
        addAiSearchBox(learningSubTab, "예: 해양환경 입문자가 20분 안에 볼 만한 영상이나 논문 찾아줘", learningSearchConversation);
        renderAiSearchConversation(learningSubTab, learningSearchConversation);

        LinearLayout tabs = row();
        Button videoTab = learningSubTab.equals("video") ? primaryButton("영상") : outlineButton("영상");
        Button paperTab = learningSubTab.equals("paper") ? primaryButton("논문") : outlineButton("논문");
        videoTab.setOnClickListener(v -> { learningSubTab = "video"; showApp(1); });
        paperTab.setOnClickListener(v -> { learningSubTab = "paper"; showApp(1); });
        LinearLayout.LayoutParams tabLeft = new LinearLayout.LayoutParams(0, dp(46), 1);
        tabLeft.setMargins(0, 0, dp(5), 0);
        tabs.addView(videoTab, tabLeft);
        LinearLayout.LayoutParams tabRight = new LinearLayout.LayoutParams(0, dp(46), 1);
        tabRight.setMargins(dp(5), 0, 0, 0);
        tabs.addView(paperTab, tabRight);
        content.addView(tabs);

        if ("paper".equals(learningSubTab)) {
            content.addView(sectionTitle("해양 논문 · 연구 자료"));
            content.addView(body("제목, 저자, 발행연도, 학술지, DOI와 초록 요약을 확인하고 원문 링크를 열거나 찜할 수 있습니다."));
            List<PaperItem> papers = DataRepository.papers();
            if (papers.isEmpty()) content.addView(note("등록된 논문이 없습니다. MY에서 최신 학습 자료를 불러와 주세요.", MUTED));
            else for (PaperItem paper : papers) addPaperCard(paper);
            return;
        }

        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("난도별 해양 영상 라이브러리"));
        content.addView(body("관심 분야와 통합 티어에 맞춰 정렬되며, 앱 내 검증 플레이어가 실제 재생 시간과 진행률을 기록합니다."));
        List<ContentItem> all = RecommendationEngine.recommendedContents(p, tier, store);
        addDifficultySection("하", "입문", "브론즈", all);
        addDifficultySection("중", "진로 탐색", "실버", all);
        addDifficultySection("상", "직무 심화", "골드", all);
    }

    private void addDifficultySection(String difficulty, String subtitle, String recommendedTier, List<ContentItem> all) {
        int count = 0;
        for (ContentItem item : all) if (item.difficulty.equals(difficulty)) count++;

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionTitle(difficulty + " 난도");
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout tierMeta = row();
        tierMeta.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TierShieldView shield = tierShield(recommendedTier);
        tierMeta.addView(shield, new LinearLayout.LayoutParams(dp(36), dp(42)));
        TextView meta = label(subtitle + " · " + plainTierText(recommendedTier) + " · " + count + "개");
        meta.setGravity(Gravity.END);
        meta.setPadding(dp(6), 0, 0, 0);
        tierMeta.addView(meta);
        heading.addView(tierMeta);

        content.addView(heading);
        for (ContentItem item : all) if (item.difficulty.equals(difficulty)) addContentCard(item, false);
    }

    private void renderQuiz() {
        addTabIntro(
                "",
                "SKILL CHECK",
                "퀴즈 · 역량 진단",
                "현재 통합 티어와 다음 승급 기준을 확인한 뒤, 내 관심 분야와 학습 기록에 맞춘 4지선다 퀴즈에 도전해 보세요. "
                        + "서버 연결 시에는 해양 AI가 앱 자료와 공공·기관 근거를 바탕으로 문제를 만들고, 연결이 어려울 때는 검증된 해양 로컬 문제은행으로 자동 전환됩니다.\n\n"
                        + "퀴즈 세션은 티어별 문항 수와 합격선이 적용되며 30초 제한 시간이 지나면 미응답 문항까지 자동 제출됩니다. 제출 후에는 점수, 정답 여부, 내가 고른 답, 정답과 해설, 획득 XP와 승급 결과를 문항별로 확인할 수 있습니다. "
                        + "각 주제의 정답·오답 기록은 MY의 역량 여권과 다음 학습·진로 추천에 반영되며, 같은 티어의 새 퀴즈에 다시 도전하거나 승급 기준 전체 매뉴얼을 확인할 수 있습니다."
        );
        String currentTier = store.getTier();
        content.addView(sectionTitle("AI 승급 퀴즈"));
        LinearLayout currentTierCard = card();
        currentTierCard.addView(tierSummaryRow(
                currentTier,
                "현재 통합 티어",
                plainTierCopy(PromotionRules.quizRule(currentTier)),
                dp(70),
                dp(82)
        ));
        content.addView(currentTierCard);

        if (PromotionRules.questionCount(currentTier) == 0 && activeQuiz.isEmpty()) {
            LinearLayout card = card();
            card.addView(tierSummaryRow(
                    "다이아",
                    "모든 승급 항로를 완료했습니다",
                    "최고 티어를 달성했습니다. 해양 학습 기록, 프로젝트와 진로 로드맵을 계속 확장해 보세요.",
                    dp(78),
                    dp(90)
            ));
            Button manual = outlineButton("전체 승급 매뉴얼 보기");
            manual.setOnClickListener(v -> showPromotionManual());
            card.addView(manual);
            content.addView(card);
            return;
        }

        if (quizGenerating) {
            LinearLayout loading = card();
            loading.addView(big("해양 도메인 문제를 생성하고 있습니다"));
            loading.addView(body(llmClient.isConfigured()
                    ? "보안 서버의 해양 AI가 영상 주제와 현재 프로필을 바탕으로 4지선다 문제를 구성합니다."
                    : "오프라인에서도 사용할 수 있는 검증된 해양 문제은행을 준비합니다."));
            ProgressBar progress = new ProgressBar(this);
            loading.addView(progress);
            content.addView(loading);
            return;
        }

        if (activeQuiz.isEmpty()) {
            LinearLayout startCard = card();
            int total = PromotionRules.questionCount(currentTier);
            int pass = PromotionRules.passCount(currentTier);
            startCard.addView(tierSummaryRow(
                    currentTier,
                    plainTierCopy(PromotionRules.displayTransition(currentTier)),
                    total + "문제 · 합격선 " + pass + "문제 · 전 문항 4지선다",
                    dp(68),
                    dp(80)
            ));
            startCard.addView(body(llmClient.isConfigured()
                    ? "BluePath 해양 AI가 공공·기관 자료를 검색해 근거 기반 문제를 생성합니다."
                    : "연결이 어려운 상황에서도 해양 특화 로컬 문제은행으로 학습할 수 있습니다."));
            Button generate = primaryButton(llmClient.isConfigured() ? "AI 퀴즈 생성" : "해양 퀴즈 시작");
            generate.setOnClickListener(v -> generateQuizForCurrentTier());
            startCard.addView(generate, new LinearLayout.LayoutParams(-1, dp(48)));
            content.addView(startCard);
            return;
        }

        LinearLayout session = card();
        session.addView(tierSummaryRow(
                quizAttemptTier,
                plainTierText(quizAttemptTier) + " 승급 세션",
                activeQuiz.size() + "문제 · 합격선 " + PromotionRules.passCount(quizAttemptTier)
                        + "문제 · 제한 시간 30초 · 출제: " + quizSource,
                dp(68),
                dp(80)
        ));
        if (quizTimedOut) session.addView(note("제한 시간이 종료되어 미응답 문항은 오답으로 자동 제출되었습니다.", DANGER));
        if (!quizNotice.isEmpty()) session.addView(note(quizNotice, MUTED));
        content.addView(session);

        if (quizSubmitted) addQuizResultCard();

        for (int i = 0; i < activeQuiz.size(); i++) addQuizQuestionCard(activeQuiz.get(i), i);

        if (!quizSubmitted) {
            Button submit = primaryButton(quizSubmitting ? "서버 검증 중" : "모든 답안 최종 제출 및 채점");
            submit.setEnabled(!quizSubmitting);
            submit.setOnClickListener(v -> submitQuiz());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
            lp.setMargins(0, dp(6), 0, dp(12));
            content.addView(submit, lp);
            startOrResumeQuizTimer();
        } else {
            Button retry = primaryButton("현재 티어 새 퀴즈 생성");
            retry.setOnClickListener(v -> {
                clearQuizSession();
                showApp(2);
            });
            content.addView(retry, new LinearLayout.LayoutParams(-1, dp(50)));
        }
    }

    private void generateQuizForCurrentTier() {
        final String tier = store.getTier();
        quizGenerating = true;
        quizSubmitted = false;
        quizAttemptTier = tier;
        quizNotice = "";
        showApp(2);

        executor.execute(() -> {
            List<QuizQuestion> generated;
            String source;
            String notice = "";
            if (llmClient.isConfigured()) {
                try {
                    generated = llmClient.generateQuiz(tier, store.getProfile(), DataRepository.contents());
                    source = "BluePath server verified quiz";
                } catch (Exception e) {
                    generated = RecommendationEngine.quizForTier(tier, store.getProfile().interest);
                    source = "검증된 해양 로컬 문제은행";
                    notice = "LLM 호출에 실패해 로컬 문제은행으로 전환했습니다: " + safeMessage(e);
                }
            } else {
                generated = RecommendationEngine.quizForTier(tier, store.getProfile().interest);
                source = "검증된 해양 로컬 문제은행";
                notice = "현재는 검증된 오프라인 문제은행을 사용합니다. AI 학습 기능을 이용할 수 있을 때는 근거 기반 문제 생성이 자동으로 적용됩니다.";
            }

            final List<QuizQuestion> result = generated;
            final String finalSource = source;
            final boolean finalServerAuthoritative = "BluePath server verified quiz".equals(source);
            final String finalNotice = finalServerAuthoritative ? notice : (notice + (notice.isEmpty() ? "" : "\n")
                    + "오프라인 문제은행은 연습 모드입니다. 정답 해설은 제공되지만 XP와 승급에는 반영되지 않습니다.");
            runOnUiThread(() -> {
                quizGenerating = false;
                activeQuiz = result == null ? new ArrayList<>() : new ArrayList<>(result);
                selectedAnswers = new int[activeQuiz.size()];
                Arrays.fill(selectedAnswers, -1);
                quizSource = finalSource;
                quizServerAuthoritative = finalServerAuthoritative;
                quizNotice = finalNotice;
                quizTimedOut = false;
                if (activeQuiz.size() != PromotionRules.questionCount(tier)) {
                    activeQuiz.clear();
                    selectedAnswers = new int[0];
                    quizDeadlineElapsedRealtime = 0L;
                    quizNotice = "필요한 문제 수를 충족하지 못해 세션을 시작하지 않았습니다. 다시 생성해 주세요.";
                } else {
                    quizDeadlineElapsedRealtime = SystemClock.elapsedRealtime() + QUIZ_TIME_LIMIT_MS;
                }
                showApp(2);
            });
        });
    }

    private void addQuizQuestionCard(QuizQuestion q, int questionIndex) {
        LinearLayout card = card();
        card.addView(label((questionIndex + 1) + " / " + activeQuiz.size() + " · " + q.topic));
        card.addView(big(q.question));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < q.options.length; i++) {
            final int optionIndex = i;
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText((i + 1) + ". " + q.options[i]);
            option.setTextColor(TEXT);
            option.setTextSize(14);
            option.setPadding(dp(4), dp(8), dp(4), dp(8));
            option.setOnClickListener(v -> selectedAnswers[questionIndex] = optionIndex);
            if (selectedAnswers.length > questionIndex && selectedAnswers[questionIndex] == i) option.setChecked(true);
            option.setEnabled(!quizSubmitted);
            group.addView(option, new RadioGroup.LayoutParams(-1, -2));
        }
        card.addView(group);

        if (quizSubmitted) {
            boolean correct = selectedAnswers[questionIndex] == q.answerIndex;
            card.addView(note(correct ? "정답" : "오답", correct ? SUCCESS : DANGER));
            int selectedAnswer = selectedAnswers[questionIndex];
            card.addView(body(selectedAnswer < 0
                    ? "내 답: 미응답"
                    : "내 답: " + q.options[selectedAnswer]));
            card.addView(body("정답: " + (q.answerIndex + 1) + ". " + q.options[q.answerIndex]));
            card.addView(body("해설: " + q.explanation));
        }
        content.addView(card);
    }

    private void submitQuiz() {
        submitQuiz(false);
    }

    private void submitQuiz(boolean timedOut) {
        if (quizSubmitted) return;
        if (activeQuiz.isEmpty() || selectedAnswers.length != activeQuiz.size()) {
            toast("퀴즈를 다시 생성해 주세요.");
            return;
        }
        if (!timedOut) {
            for (int i = 0; i < selectedAnswers.length; i++) {
                if (selectedAnswers[i] < 0) {
                    toast((i + 1) + "번 문제의 답을 선택해 주세요. 모든 문항을 답한 뒤 채점합니다.");
                    return;
                }
            }
        }

        cancelQuizTimer();
        quizTimedOut = timedOut;
        quizDeadlineElapsedRealtime = 0L;
        if (quizServerAuthoritative) {
            quizSubmitting = true;
            quizNotice = "서버에서 답안과 승급 조건을 검증하고 있습니다.";
            showApp(2);
            executor.execute(() -> {
                try {
                    ApiModels.QuizSubmissionResponse result = llmClient.submitQuiz(selectedAnswers);
                    List<QuizQuestion> revealed = quizQuestionsFromServer(result.questions, quizAttemptTier);
                    runOnUiThread(() -> {
                        quizSubmitting = false;
                        if (revealed.size() == activeQuiz.size()) activeQuiz = revealed;
                        quizCorrect = result.correctCount;
                        quizAwardedXp = result.xpAwarded;
                        store.applyServerQuizResult(result, quizAttemptTier, quizSource);
                        viewModel.recordLearning("quiz", quizAttemptTier, plainTierText(quizAttemptTier),
                                result.correctCount + "/" + result.total + (result.passed ? " passed" : " retry"));
                        quizSubmitted = true;
                        quizNotice = "서버 검증 완료 · 임의 스냅샷 값은 승급에 사용되지 않습니다.";
                        showApp(2);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        quizSubmitting = false;
                        quizNotice = "서버 채점에 실패해 결과를 확정하지 않았습니다: " + safeMessage(error);
                        showApp(2);
                    });
                }
            });
            return;
        }

        int correct = 0;
        for (int i = 0; i < activeQuiz.size(); i++) {
            if (selectedAnswers[i] == activeQuiz.get(i).answerIndex) correct++;
        }
        quizCorrect = correct;
        quizAwardedXp = 0;
        store.recordQuizAttempt(quizAttemptTier, correct, activeQuiz.size(),
                correct >= PromotionRules.passCount(quizAttemptTier), "offline practice");
        viewModel.recordLearning("quiz_practice", quizAttemptTier, plainTierText(quizAttemptTier),
                correct + "/" + activeQuiz.size() + " practice");
        quizSubmitted = true;
        quizNotice = "연습 결과입니다. 서버 검증이 없어 XP·숙련도·승급은 변경하지 않았습니다.";
        showApp(2);
    }

    private List<QuizQuestion> quizQuestionsFromServer(List<ApiModels.QuizQuestionDto> values, String tier) {
        List<QuizQuestion> result = new ArrayList<>();
        if (values == null) return result;
        for (ApiModels.QuizQuestionDto item : values) {
            if (item == null || item.options == null || item.options.size() != 4) continue;
            result.add(new QuizQuestion(item.id, tier, item.topic == null ? "해양교육" : item.topic,
                    item.question == null ? "" : item.question, item.options.toArray(new String[0]),
                    item.answerIndex, item.explanation == null ? "" : item.explanation));
        }
        return result;
    }

    private void addQuizResultCard() {
        int total = activeQuiz.size();
        boolean passed = quizServerAuthoritative && quizCorrect >= PromotionRules.passCount(quizAttemptTier);
        int score = total == 0 ? 0 : Math.round(quizCorrect * 100f / total);
        LinearLayout result = card();
        result.addView(big(passed ? "🎉 승급 기준 통과" : "🌊 다시 항해할 준비"));
        String resultTier = passed ? store.getTier() : quizAttemptTier;
        result.addView(tierSummaryRow(
                resultTier,
                passed ? "현재 티어" : "도전 중인 티어",
                plainTierText(resultTier),
                dp(64),
                dp(74)
        ));
        result.addView(huge(score + "점"));
        result.addView(body(quizCorrect + " / " + total + " 정답 · 합격선 " + PromotionRules.passCount(quizAttemptTier) + "문제"));
        String passMessage;
        if (passed && "플래티넘".equals(quizAttemptTier)) {
            passMessage = "다이아 고급 퀴즈를 통과했습니다. MY에서 자격 증빙과 해양 프로젝트를 제출해 인증 항로를 완성하세요.";
        } else if (passed) {
            passMessage = plainTierText(quizAttemptTier) + "에서 "
                    + plainTierText(PromotionRules.nextTier(quizAttemptTier))
                    + "로 승급했습니다. 현재 통합 티어: " + plainTierText(store.getTier());
        } else {
            passMessage = "합격까지 " + Math.max(0, PromotionRules.passCount(quizAttemptTier) - quizCorrect) + "문제가 더 필요합니다.";
        }
        result.addView(note(passMessage, passed ? SUCCESS : DANGER));
        result.addView(note(quizAwardedXp > 0
                        ? "이번 시도 XP +" + quizAwardedXp + " · 최초 합격 또는 최고점 향상 보상"
                        : "이번 시도 XP 0 · 동일 점수 반복이나 반복 실패에는 보상이 지급되지 않습니다.",
                quizAwardedXp > 0 ? OCEAN : MUTED));
        result.addView(body("아래에서 문항별 정답과 해설을 확인하세요. 각 주제의 결과는 MY 역량 여권과 다음 추천에 반영됩니다."));
        content.addView(result);
    }

    private void clearQuizSession() {
        activeQuiz.clear();
        selectedAnswers = new int[0];
        quizSubmitted = false;
        quizGenerating = false;
        quizSubmitting = false;
        quizServerAuthoritative = false;
        quizAttemptTier = "";
        quizSource = "";
        quizNotice = "";
        quizCorrect = 0;
        quizAwardedXp = 0;
        quizTimedOut = false;
        quizDeadlineElapsedRealtime = 0L;
        llmClient.clearQuizSession();
        cancelQuizTimer();
    }

    private void startOrResumeQuizTimer() {
        if (quizSubmitted || quizSubmitting || activeQuiz.isEmpty() || quizDeadlineElapsedRealtime <= 0L) return;
        long remaining = quizDeadlineElapsedRealtime - SystemClock.elapsedRealtime();
        if (remaining <= 0L) {
            submitQuiz(true);
            return;
        }
        updateQuizTimerText(remaining);
        quizCountDownTimer = new CountDownTimer(remaining, 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateQuizTimerText(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                updateQuizTimerText(0L);
                submitQuiz(true);
            }
        }.start();
    }

    private void updateQuizTimerText(long remainingMillis) {
        if (quizTimerText == null) return;
        long seconds = Math.max(0L, (remainingMillis + 999L) / 1_000L);
        quizTimerText.setText(String.format(Locale.KOREA, "남은 시간 00:%02d", seconds));
        quizTimerText.setTextColor(seconds <= 10L ? DANGER : OCEAN);
    }

    private void cancelQuizTimer() {
        if (quizCountDownTimer != null) {
            quizCountDownTimer.cancel();
            quizCountDownTimer = null;
        }
    }

    private void renderSchedule() {
        addTabIntro(
                "",
                "LIVE & ARCHIVE",
                "일정 · 교육 탐색",
                "지역, 대상, 시기와 관심 주제를 문장으로 입력해 해양 교육·행사를 AI로 찾거나, 분야 태그와 진행 상태를 선택해 전체 일정을 직접 둘러보세요. "
                        + "AI 검색 결과는 앱 자료와 가능한 실시간 웹 근거를 바탕으로 별도 영역에 표시되므로, 아래의 기본 일정 목록과 필터는 그대로 유지됩니다.\n\n"
                        + "오프라인 일정은 월별 달력에서 점으로 표시되며 이전·다음 달을 이동하고 날짜를 눌러 해당 일정을 모아볼 수 있습니다. 온라인·상시 활동은 별도 목록에서 더보기와 접기를 지원합니다. "
                        + "각 교육 과정과 이벤트 카드에서는 모집 상태, 대상, 운영 방식, 기간, 설명, 추천 점수와 추천 이유를 확인하고 찜 목록에 저장할 수 있으며, 신청 가능한 교육 과정은 기기 캘린더에 바로 추가할 수 있습니다. 종료된 일정도 유사 활동 탐색과 관심 분석을 위한 아카이브로 확인할 수 있습니다."
        );

        content.addView(sectionTitle("AI로 활동 찾기"));
        addAiSearchBox("schedule", "예: 부산에서 고등학생이 여름방학에 참여할 해양 안전 교육이 있을까?", scheduleSearchConversation);
        renderAiSearchConversation("schedule", scheduleSearchConversation);

        content.addView(sectionTitle("일정 둘러보기"));
        long catalogUpdatedAt = DataRepository.remoteCatalogUpdatedAt();
        if (catalogUpdatedAt > 0L) {
            content.addView(note("서버 카탈로그 저장 시각 · " + new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date(catalogUpdatedAt))
                    + " · 앱 재시작 후에도 이 카탈로그를 사용합니다.", MUTED));
        }
        content.addView(scheduleChipRow(
                new String[]{"전체", "해양환경", "해양생물", "항해", "선박", "안전", "가족", "진로", "독도·해양문화"}, true));
        content.addView(scheduleChipRow(new String[]{"전체", "진행 중", "진행 전", "진행 완료"}, false));

        UserProfile p = store.getProfile();
        List<ProgramItem> offlinePrograms = new ArrayList<>();
        List<ProgramItem> onlinePrograms = new ArrayList<>();
        for (ProgramItem item : RecommendationEngine.recommendedPrograms(p, store)) {
            if (!RecommendationEngine.matchesProgramFilter(item, "", scheduleSelectedTags, scheduleStatusFilter)) continue;
            if (RecommendationEngine.isOnlineProgram(item)) onlinePrograms.add(item);
            if (RecommendationEngine.isOfflineProgram(item)) offlinePrograms.add(item);
        }
        List<EventItem> offlineEvents = new ArrayList<>();
        for (EventItem item : RecommendationEngine.recommendedEvents(p)) {
            if (RecommendationEngine.matchesEventFilter(item, "", scheduleSelectedTags, scheduleStatusFilter)) {
                offlineEvents.add(item);
            }
        }

        boolean hasCurrentSchedule = false;
        for (ProgramItem item : DataRepository.programs()) {
            if (!RecommendationEngine.isArchived(item.startDate, item.endDate)) { hasCurrentSchedule = true; break; }
        }
        if (!hasCurrentSchedule) {
            for (EventItem item : DataRepository.events()) {
                if (!RecommendationEngine.isArchived(item.startDate, item.endDate)) { hasCurrentSchedule = true; break; }
            }
        }
        if (!hasCurrentSchedule) {
            LinearLayout stale = card();
            stale.addView(big("최신 모집 일정 확인 필요"));
            stale.addView(note("기본 일정 카탈로그가 모두 종료된 자료입니다. 서버의 최신 카탈로그를 불러온 뒤 공식 신청 링크와 모집 상태를 다시 확인해 주세요.", DANGER));
            Button refresh = primaryButton("최신 일정 카탈로그 불러오기");
            refresh.setOnClickListener(v -> viewModel.refreshCatalog());
            stale.addView(refresh);
            content.addView(stale);
        }

        Set<String> markedDays = new HashSet<>();
        Calendar monthCalendar = Calendar.getInstance(Locale.KOREA);
        monthCalendar.clear();
        monthCalendar.set(scheduleCalendarYear, scheduleCalendarMonth, 1);
        int daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= daysInMonth; day++) {
            String iso = String.format(Locale.KOREA, "%04d-%02d-%02d", scheduleCalendarYear, scheduleCalendarMonth + 1, day);
            for (ProgramItem item : offlinePrograms) {
                if (RecommendationEngine.coversIsoDate(item.startDate, item.endDate, iso)) {
                    markedDays.add(iso);
                    break;
                }
            }
            if (markedDays.contains(iso)) continue;
            for (EventItem item : offlineEvents) {
                if (RecommendationEngine.coversIsoDate(item.startDate, item.endDate, iso)) {
                    markedDays.add(iso);
                    break;
                }
            }
        }

        LinearLayout summaryCard = card();
        summaryCard.addView(big("오프라인 일정 " + (offlinePrograms.size() + offlineEvents.size()) + "건 · 이번 달 " + markedDays.size()
                + "일 · 온라인/상시 활동 " + onlinePrograms.size() + "개"));
        if (!scheduleSelectedTags.isEmpty() || !"전체".equals(scheduleStatusFilter)) {
            StringBuilder applied = new StringBuilder("적용된 조건:");
            for (String tag : scheduleSelectedTags) applied.append(" #").append(tag);
            if (!"전체".equals(scheduleStatusFilter)) applied.append(" · ").append(scheduleStatusFilter);
            summaryCard.addView(note(applied.toString(), OCEAN));
        }
        content.addView(summaryCard);

        content.addView(sectionTitle("오프라인 일정"));
        LinearLayout calendarCard = card();
        LinearLayout monthRow = row();
        Button previousMonth = outlineButton("〈");
        previousMonth.setOnClickListener(v -> {
            if (scheduleCalendarMonth == 0) {
                scheduleCalendarMonth = 11;
                scheduleCalendarYear--;
            } else {
                scheduleCalendarMonth--;
            }
            showApp(3);
        });
        Button nextMonth = outlineButton("〉");
        nextMonth.setOnClickListener(v -> {
            if (scheduleCalendarMonth == 11) {
                scheduleCalendarMonth = 0;
                scheduleCalendarYear++;
            } else {
                scheduleCalendarMonth++;
            }
            showApp(3);
        });
        TextView monthTitle = big(scheduleCalendarYear + "년 " + (scheduleCalendarMonth + 1) + "월");
        monthTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(44), dp(40));
        monthRow.addView(previousMonth, arrowParams);
        monthRow.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 1));
        monthRow.addView(nextMonth, arrowParams);
        calendarCard.addView(monthRow);

        MonthCalendarView calendarView = new MonthCalendarView(this);
        calendarView.setMonth(scheduleCalendarYear, scheduleCalendarMonth);
        calendarView.setMarkedDays(markedDays);
        calendarView.setSelectedDay(scheduleSelectedDate);
        calendarView.setOnDaySelectedListener(isoDate -> {
            scheduleSelectedDate = isoDate;
            showApp(3);
        });
        calendarCard.addView(calendarView, new LinearLayout.LayoutParams(-1, -2));
        content.addView(calendarCard);

        if (!scheduleSelectedDate.isEmpty()) {
            content.addView(sectionTitle(scheduleDateLabel(scheduleSelectedDate) + " 일정"));
            int shownForDate = 0;
            for (ProgramItem item : offlinePrograms) {
                if (RecommendationEngine.coversIsoDate(item.startDate, item.endDate, scheduleSelectedDate)) {
                    addProgramCard(item);
                    shownForDate++;
                }
            }
            for (EventItem item : offlineEvents) {
                if (RecommendationEngine.coversIsoDate(item.startDate, item.endDate, scheduleSelectedDate)) {
                    addEventCard(item);
                    shownForDate++;
                }
            }
            if (shownForDate == 0) content.addView(note("선택한 날짜에는 조건에 맞는 오프라인 일정이 없습니다.", MUTED));
        } else {
            content.addView(note("달력에서 점이 표시된 날짜를 선택하면 해당 날짜의 오프라인 일정을 보여드립니다.", MUTED));
        }

        content.addView(sectionTitle("온라인/상시 활동 " + onlinePrograms.size() + "개"));
        if (onlinePrograms.isEmpty()) {
            content.addView(note("조건에 맞는 온라인/상시 활동이 없습니다.", MUTED));
        } else {
            int visibleOnline = scheduleOnlineExpanded ? onlinePrograms.size() : Math.min(4, onlinePrograms.size());
            for (int i = 0; i < visibleOnline; i++) addProgramCard(onlinePrograms.get(i));
            if (onlinePrograms.size() > 4) {
                Button toggle = outlineButton(scheduleOnlineExpanded ? "접기" : "더보기 (" + (onlinePrograms.size() - 4) + "개 더)");
                toggle.setOnClickListener(v -> {
                    int savedScrollY = contentScroll == null ? 0 : contentScroll.getScrollY();
                    scheduleOnlineExpanded = !scheduleOnlineExpanded;
                    showApp(3);
                    ScrollView restoredScroll = contentScroll;
                    if (restoredScroll != null) {
                        restoredScroll.post(() -> restoredScroll.scrollTo(0, savedScrollY));
                    }
                });
                content.addView(toggle, new LinearLayout.LayoutParams(-1, dp(44)));
            }
        }
    }

    private View scheduleChipRow(String[] chips, boolean isTagRow) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String chip : chips) {
            boolean selected = isTagRow
                    ? ("전체".equals(chip) ? scheduleSelectedTags.isEmpty() : scheduleSelectedTags.contains(chip))
                    : scheduleStatusFilter.equals(chip);
            Button chipButton = outlineButton(chip);
            styleActivityYearButton(chipButton, selected);
            chipButton.setOnClickListener(v -> {
                if (isTagRow) {
                    if ("전체".equals(chip)) scheduleSelectedTags.clear();
                    else if (!scheduleSelectedTags.remove(chip)) scheduleSelectedTags.add(chip);
                } else {
                    scheduleStatusFilter = chip;
                }
                showApp(3);
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(38));
            chipParams.setMargins(0, 0, dp(6), 0);
            chipRow.addView(chipButton, chipParams);
        }
        scroll.addView(chipRow);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -2);
        scrollParams.setMargins(0, 0, 0, dp(8));
        scroll.setLayoutParams(scrollParams);
        return scroll;
    }

    private String scheduleDateLabel(String isoDate) {
        String[] parts = isoDate.split("-");
        if (parts.length != 3) return isoDate;
        try {
            return Integer.parseInt(parts[1]) + "월 " + Integer.parseInt(parts[2]) + "일";
        } catch (NumberFormatException ignored) {
            return isoDate;
        }
    }

    private void renderCareer() {
        addTabIntro(
                "",
                "UNIVERSAL CAREER AI",
                "AI 진로 상담",
                "궁금한 해양 직무, 전공, 자격, 프로젝트와 학습 순서를 자유롭게 질문하면 BluePath AI가 내 관심 분야, 현재 티어와 앱의 학습 자료를 함께 검토해 맞춤 답변을 제공합니다. "
                        + "서버가 연결된 경우 기관 자료와 설정된 실시간 웹 검색 결과를 근거로 답변하고, 연결 전에는 오프라인 해양 상담 엔진으로 기본 진로 경로를 안내합니다.\n\n"
                        + "이전 질문과 답변은 대화 기록으로 계속 남으며, 바로 이어서 후속 질문을 할 수 있습니다. 답변 아래에서는 NCS 기반 추천 직무와 단계별 준비 항로를 확인할 수 있습니다."
        );
        UserProfile p = store.getProfile();
        String tier = store.getTier();

        content.addView(sectionTitle("BluePath AI 진로 상담봇"));
        content.addView(body(llmClient.isConfigured()
                ? "보안 서버가 앱 데이터와 기관 자료를 검색하고, 설정된 경우 실시간 웹 검색·본문 추출 결과까지 함께 검토해 출처가 있는 답변을 제공합니다."
                : "서버 연결 전에는 오프라인 해양 상담 엔진으로 기본 경로를 안내합니다."));

        if (!agentConversation.isEmpty()) {
            LinearLayout historyHeader = row();
            historyHeader.setGravity(Gravity.CENTER_VERTICAL);
            historyHeader.addView(big("상담 대화"), new LinearLayout.LayoutParams(0, -2, 1));
            Button clear = outlineButton("대화 지우기");
            clear.setOnClickListener(v -> {
                agentConversation.clear();
                agentDraft = "";
                showApp(4);
            });
            historyHeader.addView(clear, new LinearLayout.LayoutParams(dp(112), dp(40)));
            content.addView(historyHeader);
            for (AgentTurn turn : agentConversation) {
                addConversationBubble("나", turn.question, true);
                if (turn.loading) {
                    LinearLayout pending = card();
                    pending.addView(label("BluePath AI"));
                    pending.addView(big("답변을 준비하고 있습니다"));
                    pending.addView(new ProgressBar(this));
                    content.addView(pending);
                    conversationScrollAnchor = pending;
                } else if (!turn.error.isEmpty()) {
                    View errorCard = addConversationBubble("BluePath AI", turn.error, false);
                    conversationScrollAnchor = errorCard;
                } else {
                    View answerCard = addConversationBubble("BluePath AI", turn.answer, false);
                    conversationScrollAnchor = answerCard;
                }
            }
        } else {
            content.addView(note("첫 질문을 입력하면 이 화면에 질문과 답변이 차례로 쌓입니다.", MUTED));
        }

        EditText input = new EditText(this);
        input.setHint(agentConversation.isEmpty()
                ? "예: 자율운항선박 분야로 진출하려면 어떤 전공·자격·프로젝트가 필요해?"
                : "이전 답변에 이어 후속 질문을 입력하세요.");
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setText(agentDraft);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setBackgroundResource(R.drawable.bg_input);
        configureKoreanTextInput(input, true);
        bindDraft(input, value -> agentDraft = value);
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        Button ask = primaryButton(agentConversation.isEmpty() ? "AI 진로 상담 받기" : "후속 질문 보내기");
        ask.setEnabled(!isAgentLoading());
        ask.setOnClickListener(v -> requestAgentAnswer(input.getText().toString()));
        LinearLayout.LayoutParams askParams = new LinearLayout.LayoutParams(-1, dp(50));
        askParams.setMargins(0, dp(10), 0, dp(10));
        content.addView(ask, askParams);

        String[] chips = {"내 티어에서 시작할 진로", "항해사 역량 로드맵", "스마트 항만 직무", "해양환경 연구자 준비", "최신 자격·교육 확인 방법"};
        for (String chip : chips) {
            Button b = outlineButton(chip);
            b.setEnabled(!isAgentLoading());
            b.setOnClickListener(v -> requestAgentAnswer(chip));
            content.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));
        }

        content.addView(sectionTitle("NCS 기반 커리어 항로"));
        content.addView(body("AI 답변 아래에서 관심 분야, 현재 통합 티어, 학습 목표를 직무·필요 역량·근무지·다음 학습으로 연결합니다."));
        for (CareerItem c : RecommendationEngine.recommendedCareers(p, tier, store)) addCareerCard(c);
    }

    private boolean isAgentLoading() {
        return !agentConversation.isEmpty() && agentConversation.get(agentConversation.size() - 1).loading;
    }

    private void requestAgentAnswer(String question) {
        final String trimmed = question == null ? "" : question.trim();
        if (trimmed.isEmpty()) {
            toast("질문을 입력해 주세요.");
            return;
        }
        if (isAgentLoading()) {
            toast("이전 답변을 기다려 주세요.");
            return;
        }
        final List<ApiModels.ChatMessage> history = agentHistoryMessages();
        final AgentTurn turn = new AgentTurn(trimmed);
        agentConversation.add(turn);
        if (agentConversation.size() > 20) agentConversation.remove(0);
        agentDraft = "";
        showApp(4);
        scrollToConversationAnchor();
        executor.execute(() -> {
            String answer;
            try {
                if (llmClient.isConfigured()) {
                    UserProfile profile = store.getProfile();
                    answer = llmClient.answerAgent(trimmed, profile, store.getTier(),
                            RecommendationEngine.recommendedContents(profile, store.getTier(), store),
                            PromotionRules.fullManualPlain(), history);
                } else {
                    answer = RecommendationEngine.answerAgent(trimmed, store.getProfile(), store.getTier());
                }
                turn.answer = answer;
            } catch (Exception e) {
                turn.error = "LLM 호출에 실패해 로컬 상담으로 전환했습니다.\n\n"
                        + RecommendationEngine.answerAgent(trimmed, store.getProfile(), store.getTier())
                        + "\n\n오류: " + safeMessage(e);
            }
            turn.loading = false;
            runOnUiThread(() -> {
                if (currentTab == 4) {
                    showApp(4);
                    scrollToConversationAnchor();
                }
            });
        });
    }

    private List<ApiModels.ChatMessage> agentHistoryMessages() {
        List<ApiModels.ChatMessage> history = new ArrayList<>();
        int start = Math.max(0, agentConversation.size() - 8);
        for (int i = start; i < agentConversation.size(); i++) {
            AgentTurn turn = agentConversation.get(i);
            if (turn.loading) continue;
            history.add(new ApiModels.ChatMessage("user", turn.question));
            String response = !turn.answer.isEmpty() ? turn.answer : turn.error;
            if (!response.isEmpty()) history.add(new ApiModels.ChatMessage("assistant", response));
        }
        return history;
    }

    /**
     * 커뮤니티 우측 하단의 플로팅 글쓰기 버튼입니다.
     * 원형 + 아이콘과 "글쓰기" 라벨을 하나의 말풍선형 버튼으로 구성합니다.
     */
    private void addCommunityWriteFab() {
        LinearLayout writeFab = new LinearLayout(this);
        writeFab.setOrientation(LinearLayout.HORIZONTAL);
        writeFab.setGravity(Gravity.CENTER_VERTICAL);
        writeFab.setPadding(dp(8), 0, dp(18), 0);
        writeFab.setClickable(true);
        writeFab.setFocusable(true);
        writeFab.setContentDescription("커뮤니티 글쓰기");
        writeFab.setElevation(dp(10));

        GradientDrawable fabBackground = new GradientDrawable();
        fabBackground.setColor(OCEAN);
        fabBackground.setCornerRadius(dp(30));
        fabBackground.setStroke(dp(1), Color.parseColor("#66FFFFFF"));
        writeFab.setBackground(fabBackground);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(OCEAN);
        plus.setTextSize(24);
        plus.setTypeface(Typeface.DEFAULT_BOLD);
        plus.setGravity(Gravity.CENTER);
        plus.setIncludeFontPadding(false);

        GradientDrawable plusBackground = new GradientDrawable();
        plusBackground.setShape(GradientDrawable.OVAL);
        plusBackground.setColor(Color.WHITE);
        plus.setBackground(plusBackground);
        writeFab.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView label = new TextView(this);
        label.setText("글쓰기");
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(10), 0, 0, 0);
        writeFab.addView(label, new LinearLayout.LayoutParams(-2, -1));

        writeFab.setOnClickListener(v -> openCommunityPostScreen());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(132), dp(58), Gravity.END | Gravity.BOTTOM);
        params.setMargins(dp(16), dp(16), dp(18), dp(24));
        appRoot.addView(writeFab, params);
    }

    private void renderCommunity() {
        addTabIntro(
                "",
                "OCEAN COMMUNITY",
                "해양 커뮤니티",
                "자유 게시판과 질문 게시판에서 해양 학습·활동 경험을 나누고 서로 답해 보세요. 검색과 더 보기를 지원하며, 작성자는 게시글과 댓글을 수정·삭제할 수 있습니다. "
                        + "불편하거나 유해한 콘텐츠는 신고하고 작성자를 차단할 수 있으며, 차단한 사용자의 게시글과 댓글은 목록에서 제외됩니다."
        );

        LinearLayout tabs = row();
        Button free = "free".equals(communityCategory) ? primaryButton("자유 게시판") : outlineButton("자유 게시판");
        Button question = "question".equals(communityCategory) ? primaryButton("질문 게시판") : outlineButton("질문 게시판");
        free.setOnClickListener(v -> { communityCategory = "free"; requestCommunityRefresh(); });
        question.setOnClickListener(v -> { communityCategory = "question"; requestCommunityRefresh(); });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(46), 1);
        left.setMargins(0, 0, dp(5), 0);
        tabs.addView(free, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(46), 1);
        right.setMargins(dp(5), 0, 0, 0);
        tabs.addView(question, right);
        content.addView(tabs);

        LinearLayout searchCard = card();
        LinearLayout searchRow = row();
        EditText search = inputField("제목·본문·작성자 검색", communityQuery);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button searchButton = primaryButton("검색");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(78), dp(48));
        searchParams.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchParams);
        searchButton.setOnClickListener(v -> {
            communityQuery = search.getText().toString().trim();
            requestCommunityRefresh();
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            communityQuery = search.getText().toString().trim();
            requestCommunityRefresh();
            return true;
        });
        searchCard.addView(searchRow);
        if (!communityQuery.isEmpty()) {
            Button clear = outlineButton("검색어 지우기");
            clear.setOnClickListener(v -> { communityQuery = ""; requestCommunityRefresh(); });
            searchCard.addView(clear, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        content.addView(searchCard);

        if (!communityError.isEmpty()) {
            content.addView(note(communityError, DANGER));
            Button retry = primaryButton("다시 시도");
            retry.setOnClickListener(v -> requestCommunityRefresh());
            LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-1, dp(48));
            retryParams.setMargins(0, 0, 0, dp(12));
            content.addView(retry, retryParams);
        }
        if (communityPosts.isEmpty() && communityLoading) {
            LinearLayout loading = card();
            loading.addView(big("커뮤니티를 불러오고 있습니다"));
            loading.addView(new ProgressBar(this));
            content.addView(loading);
            return;
        }
        if (communityPosts.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(big(communityQuery.isEmpty() ? "아직 게시글이 없습니다" : "검색 결과가 없습니다"));
            empty.addView(body(communityQuery.isEmpty() ? "첫 번째 해양 이야기를 남겨 보세요." : "다른 검색어를 입력해 보세요."));
            content.addView(empty);
            if (communityError.isEmpty() && !communityLoading && !communityInitialized) requestCommunityRefresh();
            return;
        }
        for (ApiModels.CommunityPostDto post : communityPosts) addCommunityPostCard(post);
        if (communityLoading) {
            ProgressBar progress = new ProgressBar(this);
            content.addView(progress);
        } else if (communityHasMore) {
            Button more = outlineButton("게시글 더 보기");
            more.setOnClickListener(v -> requestCommunityPage(true));
            content.addView(more, new LinearLayout.LayoutParams(-1, dp(48)));
        }
    }

    private void requestCommunityRefresh() {
        communityInitialized = false;
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        requestCommunityPage(false);
    }

    private void requestCommunityPage(boolean append) {
        if (communityLoading) return;
        communityLoading = true;
        communityError = "";
        if (currentTab == 5) showApp(5);
        final int requestedOffset = append ? communityOffset : 0;
        executor.execute(() -> {
            try {
                List<ApiModels.CommunityPostDto> result = cloudRepository.communityPosts(
                        communityCategory, communityQuery, COMMUNITY_PAGE_SIZE, requestedOffset);
                runOnUiThread(() -> {
                    List<ApiModels.CommunityPostDto> page = result == null ? new ArrayList<>() : result;
                    if (!append) communityPosts = new ArrayList<>();
                    communityPosts.addAll(page);
                    communityOffset = requestedOffset + page.size();
                    communityHasMore = page.size() == COMMUNITY_PAGE_SIZE;
                    communityLoading = false;
                    communityInitialized = true;
                    if (currentTab == 5) showApp(5);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    communityLoading = false;
                    communityInitialized = true;
                    communityError = "커뮤니티 연결 실패: " + safeMessage(e);
                    if (currentTab == 5) showApp(5);
                });
            }
        });
    }

    private void openCommunityPostScreen() {
        Intent intent = new Intent(this, CommunityPostActivity.class);
        intent.putExtra(CommunityPostActivity.EXTRA_CATEGORY, communityCategory);
        communityPostLauncher.launch(intent);
    }

    private void addCommunityPostCard(ApiModels.CommunityPostDto post) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(14), dp(12), dp(12));
        item.setBackgroundColor(Color.WHITE);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> openCommunityPostDetail(post.id));

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = body(post.title);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(NAVY);
        title.setMaxLines(2);
        title.setPadding(0, 0, dp(12), 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView author = label(post.author == null ? "알 수 없음" : post.author.nickname);
        author.setTextColor(TEXT);
        author.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        author.setMaxLines(1);
        titleRow.addView(author, new LinearLayout.LayoutParams(dp(92), -2));
        item.addView(titleRow);

        int commentCount = post.comments == null ? 0 : post.comments.size();
        int reactionCount = 0;
        if (post.reactions != null) {
            for (ApiModels.ReactionSummary reaction : post.reactions) reactionCount += reaction.count;
        }
        String photo = safe(post.imageUrl).isEmpty() ? "" : " · 사진";
        String metaText = ("question".equals(post.category) ? "질문" : "자유")
                + " · " + readableDate(post.createdAt)
                + " · 댓글 " + commentCount
                + " · 공감 " + reactionCount
                + photo;
        TextView meta = label(metaText);
        meta.setPadding(0, dp(7), 0, 0);
        item.addView(meta);

        content.addView(item, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#D7E3E8"));
        content.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void openCommunityPostDetail(String postId) {
        Intent intent = new Intent(this, CommunityPostDetailActivity.class);
        intent.putExtra(CommunityPostDetailActivity.EXTRA_POST_ID, postId);
        communityPostLauncher.launch(intent);
    }

    private void addCommentChildren(LinearLayout container, ApiModels.CommunityPostDto post, String parentId, int depth) {
        if (post.comments == null) return;
        for (ApiModels.CommunityCommentDto comment : post.comments) {
            boolean matches = parentId == null ? comment.parentId == null : parentId.equals(comment.parentId);
            if (!matches) continue;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(10 + Math.min(depth, 3) * 12), dp(8), dp(8), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(depth == 0 ? Color.parseColor("#F1F8FA") : Color.parseColor("#E9F4F8"));
            bg.setCornerRadius(dp(12));
            box.setBackground(bg);
            LinearLayout head = row();
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(profileAvatar(comment.author.nickname, comment.author.profileImageUrl, dp(34)), new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView name = body(comment.author.nickname);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setPadding(dp(8), 0, 0, 0);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TierShieldView commentShield = tierShield(comment.author.tier);
            LinearLayout.LayoutParams commentShieldParams = new LinearLayout.LayoutParams(dp(30), dp(36));
            commentShieldParams.setMargins(dp(4), 0, dp(4), 0);
            head.addView(commentShield, commentShieldParams);
            if (!store.getNickname().equals(comment.author.nickname)) {
                Button follow = outlineButton(comment.author.isFollowing ? "팔로잉" : "팔로우");
                follow.setTextSize(10);
                follow.setOnClickListener(v -> toggleFollow(comment.author.userId));
                head.addView(follow, new LinearLayout.LayoutParams(dp(78), dp(34)));
            }
            box.addView(head);
            box.addView(body(comment.body));
            box.addView(label(readableDate(comment.createdAt)));
            box.addView(reactionBar("comment", comment.id, comment.reactions));
            LinearLayout commentActions = row();
            Button reply = outlineButton("답글");
            reply.setOnClickListener(v -> showCommunityCommentDialog(post.id, comment.id, comment.author.nickname + "에게 답글"));
            commentActions.addView(reply, weightedButtonParams(true));
            if (comment.canEdit) {
                Button edit = outlineButton("수정");
                edit.setOnClickListener(v -> showEditCommentDialog(comment));
                Button delete = outlineButton("삭제");
                delete.setOnClickListener(v -> confirmDeleteComment(comment.id));
                commentActions.addView(edit, weightedButtonParams(false));
                commentActions.addView(delete, weightedButtonParams(false));
            } else {
                Button report = outlineButton("신고");
                report.setOnClickListener(v -> showReportDialog("comment", comment.id));
                Button block = outlineButton("차단");
                block.setOnClickListener(v -> confirmBlockUser(comment.author.userId, comment.author.nickname));
                commentActions.addView(report, weightedButtonParams(false));
                commentActions.addView(block, weightedButtonParams(false));
            }
            box.addView(commentActions);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(6), 0, dp(2));
            container.addView(box, params);
            addCommentChildren(container, post, comment.id, depth + 1);
        }
    }

    private View reactionBar(String targetType, String targetId, List<ApiModels.ReactionSummary> reactions) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🌊"};
        Map<String, ApiModels.ReactionSummary> map = new HashMap<>();
        if (reactions != null) for (ApiModels.ReactionSummary item : reactions) map.put(item.emoji, item);
        for (String emoji : emojis) {
            ApiModels.ReactionSummary summary = map.get(emoji);
            String label = emoji + (summary == null || summary.count == 0 ? "" : " " + summary.count);
            Button button = summary != null && summary.reactedByMe ? primaryButton(label) : outlineButton(label);
            button.setTextSize(12);
            button.setOnClickListener(v -> toggleReaction(targetType, targetId, emoji));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(38));
            params.setMargins(0, dp(6), dp(5), dp(6));
            row.addView(button, params);
        }
        scroll.addView(row);
        return scroll;
    }

    private void showCommunityCommentDialog(String postId, String parentId, String title) {
        EditText input = inputField("내용을 입력하세요", "");
        input.setSingleLine(false);
        input.setMinLines(3);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("등록", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            dialog.dismiss();
            executor.execute(() -> {
                try {
                    cloudRepository.createCommunityComment(postId, value, parentId);
                    runOnUiThread(this::requestCommunityRefresh);
                } catch (Exception e) {
                    runOnUiThread(() -> toast("댓글 작성 실패: " + safeMessage(e)));
                }
            });
        }));
        dialog.show();
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
        params.setMargins(first ? 0 : dp(4), dp(4), first ? dp(4) : 0, dp(4));
        return params;
    }

    private void showEditPostDialog(ApiModels.CommunityPostDto post) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText title = inputField("제목", post.title);
        EditText body = inputField("내용", post.body);
        body.setSingleLine(false);
        body.setMinLines(5);
        fields.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(0, dp(8), 0, 0);
        fields.addView(body, bodyParams);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("게시글 수정")
                .setView(fields)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newTitle = title.getText().toString().trim();
            String newBody = body.getText().toString().trim();
            if (newTitle.length() < 2 || newBody.length() < 2) {
                toast("제목과 내용을 두 글자 이상 입력해 주세요.");
                return;
            }
            dialog.dismiss();
            executor.execute(() -> {
                try {
                    cloudRepository.updateCommunityPost(post.id, newTitle, newBody);
                    runOnUiThread(this::requestCommunityRefresh);
                } catch (Exception e) {
                    runOnUiThread(() -> toast("게시글 수정 실패: " + safeMessage(e)));
                }
            });
        }));
        dialog.show();
    }

    private void confirmDeletePost(String postId) {
        new AlertDialog.Builder(this)
                .setTitle("게시글 삭제")
                .setMessage("게시글과 모든 댓글을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> executor.execute(() -> {
                    try {
                        cloudRepository.deleteCommunityPost(postId);
                        runOnUiThread(this::requestCommunityRefresh);
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("게시글 삭제 실패: " + safeMessage(e)));
                    }
                }))
                .show();
    }

    private void showEditCommentDialog(ApiModels.CommunityCommentDto comment) {
        EditText input = inputField("댓글 내용", comment.body);
        input.setSingleLine(false);
        input.setMinLines(3);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("댓글 수정")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            dialog.dismiss();
            executor.execute(() -> {
                try {
                    cloudRepository.updateCommunityComment(comment.id, value);
                    runOnUiThread(this::requestCommunityRefresh);
                } catch (Exception e) {
                    runOnUiThread(() -> toast("댓글 수정 실패: " + safeMessage(e)));
                }
            });
        }));
        dialog.show();
    }

    private void confirmDeleteComment(String commentId) {
        new AlertDialog.Builder(this)
                .setTitle("댓글 삭제")
                .setMessage("댓글과 연결된 답글을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> executor.execute(() -> {
                    try {
                        cloudRepository.deleteCommunityComment(commentId);
                        runOnUiThread(this::requestCommunityRefresh);
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("댓글 삭제 실패: " + safeMessage(e)));
                    }
                }))
                .show();
    }

    private void showReportDialog(String targetType, String targetId) {
        EditText input = inputField("신고 사유를 입력하세요", "");
        input.setSingleLine(false);
        input.setMinLines(3);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("콘텐츠 신고")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("신고", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = input.getText().toString().trim();
            if (reason.length() < 2) {
                toast("신고 사유를 두 글자 이상 입력해 주세요.");
                return;
            }
            dialog.dismiss();
            executor.execute(() -> {
                try {
                    cloudRepository.reportCommunity(targetType, targetId, reason);
                    runOnUiThread(() -> toast("신고가 접수되었습니다."));
                } catch (Exception e) {
                    runOnUiThread(() -> toast("신고 실패: " + safeMessage(e)));
                }
            });
        }));
        dialog.show();
    }

    private void confirmBlockUser(String userId, String nickname) {
        new AlertDialog.Builder(this)
                .setTitle("사용자 차단")
                .setMessage(nickname + "님의 게시글과 댓글을 숨길까요? 다시 누르면 차단을 해제할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("차단", (dialog, which) -> executor.execute(() -> {
                    try {
                        boolean blocked = cloudRepository.toggleBlock(userId);
                        runOnUiThread(() -> {
                            toast(blocked ? "사용자를 차단했습니다." : "차단을 해제했습니다.");
                            requestCommunityRefresh();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("차단 처리 실패: " + safeMessage(e)));
                    }
                }))
                .show();
    }

    private void toggleReaction(String targetType, String targetId, String emoji) {
        executor.execute(() -> {
            try {
                cloudRepository.toggleReaction(targetType, targetId, emoji);
                runOnUiThread(this::requestCommunityRefresh);
            } catch (Exception e) {
                runOnUiThread(() -> toast("공감 반응 실패: " + safeMessage(e)));
            }
        });
    }

    private void toggleFollow(String userId) {
        executor.execute(() -> {
            try {
                cloudRepository.toggleFollow(userId);
                runOnUiThread(this::requestCommunityRefresh);
            } catch (Exception e) {
                runOnUiThread(() -> toast("팔로우 처리 실패: " + safeMessage(e)));
            }
        });
    }

    private void renderMyPage() {
        addTabIntro(
                "",
                "MY OCEAN PAGE",
                "MY · 나의 해양 여권",
                "내 프로필 사진, 관심 분야, 목표, 통합 티어, XP, 팔로워·팔로잉 수와 학습·찜·퀴즈 통계를 한곳에서 확인하세요. "
                        + "Ocean Skill Map에서는 퀴즈, 학습 완료와 현장 미션으로 쌓인 분야별 숙련도와 증거를 살펴보고, 노드를 눌러 점수 근거, 하위 역량, NCS 연계, 연결 진로와 다음 추천 활동을 확인할 수 있습니다.\n\n"
                        + "검증된 학습·미션 기록은 목표 진로 준비도와 증거 코드가 포함된 해양 역량 포트폴리오로 미리 보거나 PDF로 생성해 공유할 수 있습니다. 승급·학습 리포트에서는 티어별 최고 퀴즈 점수와 최근 결과를 확인하고, 완료한 영상과 찜한 항목도 다시 살펴볼 수 있습니다.\n\n"
                        + "연령대, 관심 분야, 학습 목적과 현재 수준을 수정하고 프로필 사진을 업로드할 수 있으며, 미성년 계정의 보호자 동의와 클라우드 동기화, 최신 학습 자료 불러오기, 로그아웃을 관리할 수 있습니다. "
                        + "매일 학습 알림의 시간 설정·해제와 시험·자격 일정의 캘린더 추가를 지원하며, 플래티넘 이상에서는 다이아 고급 퀴즈·자격 증빙·해양 프로젝트 제출 및 검토 상태를 관리할 수 있습니다. 필요할 때는 기기의 프로필과 학습 기록 전체를 초기화할 수 있습니다."
        );
        UserProfile p = store.getProfile();
        String tier = store.getTier();

        LinearLayout profileCard = card();
        LinearLayout profileTop = row();
        profileTop.setGravity(Gravity.CENTER_VERTICAL);
        profileTop.addView(profileAvatar(store.getNickname(), store.getProfileImageUrl(), dp(72)), new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        profileText.setPadding(dp(12), 0, dp(8), 0);
        profileText.addView(big(store.getNickname()));
        profileText.addView(body(p.ageGroup + " · " + p.interest + " · " + p.goal));
        profileText.addView(label(store.getAccountEmail()));
        profileText.addView(body("팔로워 " + store.getFollowerCount() + " · 팔로잉 " + store.getFollowingCount()));
        profileTop.addView(profileText, new LinearLayout.LayoutParams(0, -2, 1));
        TierShieldView myShield = tierShield(tier);
        profileTop.addView(myShield, new LinearLayout.LayoutParams(dp(88), dp(100)));
        profileCard.addView(profileTop);
        profileCard.addView(big("통합 티어 " + plainTierText(tier) + " · XP " + p.xp));
        Button uploadPhoto = outlineButton("프로필 사진 업로드");
        uploadPhoto.setOnClickListener(v -> profileImagePicker.launch(new String[]{"image/jpeg", "image/png", "image/webp"}));
        profileCard.addView(uploadPhoto, new LinearLayout.LayoutParams(-1, dp(46)));
        content.addView(profileCard);

        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "검증 완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getBookmarks().size()), "찜"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "응시"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        content.addView(sectionTitle("Ocean Skill Passport"));
        Map<String, Integer> masteryMap = currentSkillMastery();
        Map<String, Integer> evidenceMap = currentSkillEvidence();
        LinearLayout skillCard = card();
        skillCard.addView(big("인터랙티브 Ocean Skill Map"));
        skillCard.addView(body("퀴즈 문항, 학습 완료와 현장 미션 증거를 분야별 노드로 연결합니다. 노드를 누르면 점수 근거, NCS 역량, 연결 진로와 다음 활동을 확인할 수 있습니다."));
        String targetCareer = store.getTargetCareer();
        int readiness = SkillProfileCatalog.careerReadiness(targetCareer, masteryMap);
        skillCard.addView(note("목표 진로 " + targetCareer + " · 현재 준비도 " + readiness + "점", readiness >= 70 ? SUCCESS : OCEAN));

        OceanSkillMapView skillMap = new OceanSkillMapView(this);
        skillMap.setSkillData(masteryMap, evidenceMap);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(390));
        mapParams.setMargins(0, dp(8), 0, dp(8));
        skillCard.addView(skillMap, mapParams);

        LinearLayout skillDetail = new LinearLayout(this);
        skillDetail.setOrientation(LinearLayout.VERTICAL);
        String initialTopic = SkillProfileCatalog.weakestTopics(masteryMap, 1).get(0);
        renderSkillMapDetail(skillDetail, initialTopic, masteryMap, evidenceMap);
        skillMap.setOnSkillSelectedListener(topic -> renderSkillMapDetail(skillDetail, topic, masteryMap, evidenceMap));
        skillCard.addView(skillDetail);

        skillCard.addView(label("전체 숙련도 목록"));
        for (String topic : SkillProfileCatalog.TOPICS) addSkillProgress(skillCard, topic);
        content.addView(skillCard);

        content.addView(sectionTitle("검증형 해양 역량 포트폴리오"));
        addEvidencePortfolioCard();

        content.addView(sectionTitle("승급·학습 리포트"));
        LinearLayout report = card();
        report.addView(big("XP " + p.xp + " · " + plainTierCopy(PromotionRules.quizRule(tier))));
        report.addView(body("최근 퀴즈: " + plainTierCopy(store.getLastQuizSummary())));
        addTierScoreRow(report, "브론즈", "최고 " + store.getBestQuizScore("브론즈") + "/10");
        addTierScoreRow(report, "실버", "최고 " + store.getBestQuizScore("실버") + "/12");
        addTierScoreRow(report, "골드", "최고 " + store.getBestQuizScore("골드") + "/15");
        addTierScoreRow(report, "플래티넘", "고급 " + store.getBestQuizScore("플래티넘") + "/20");
        Button manual = outlineButton("승급 기준 전체 보기");
        manual.setOnClickListener(v -> showPromotionManual());
        report.addView(manual);
        content.addView(report);

        content.addView(sectionTitle("학습 기록"));
        addIdListCard("완료한 영상", store.getCompletedContentIds());
        addIdListCard("찜한 항목", store.getBookmarks());

        content.addView(sectionTitle("프로필 편집"));
        LinearLayout editCard = card();
        Spinner age = spinner(new String[]{"초등학생", "중학생", "고등학생", "대학생", "성인", "직장인", "학부모/가족"});
        Spinner interest = spinner(new String[]{"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"});
        Spinner goal = spinner(new String[]{"흥미", "체험", "진로탐색", "자격증", "직무역량 강화", "가족 교육"});
        Spinner level = spinner(new String[]{"입문", "기초", "중급", "심화", "실무"});
        setSpinnerSelection(age, p.ageGroup);
        setSpinnerSelection(interest, p.interest);
        setSpinnerSelection(goal, p.goal);
        setSpinnerSelection(level, p.level);
        editCard.addView(label("연령대")); editCard.addView(age);
        editCard.addView(label("관심 분야")); editCard.addView(interest);
        editCard.addView(label("학습 목적")); editCard.addView(goal);
        editCard.addView(label("현재 수준")); editCard.addView(level);
        Button saveProfile = primaryButton("프로필 저장");
        saveProfile.setOnClickListener(v -> {
            String a = age.getSelectedItem().toString();
            String i = interest.getSelectedItem().toString();
            String g = goal.getSelectedItem().toString();
            String l = level.getSelectedItem().toString();
            store.saveProfile(new UserProfile(a, i, g, l, RecommendationEngine.persona(a, g, i), p.xp));
            toast("프로필을 저장했습니다.");
            if (store.requiresGuardianConsent() && !store.hasGuardianConsent()) {
                showGuardianConsentDialog(false);
            } else {
                if (viewModel.isCloudConfigured()) viewModel.syncNow();
                showApp(6);
            }
        });
        editCard.addView(saveProfile, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(editCard);

        content.addView(sectionTitle("계정과 클라우드 백업"));
        LinearLayout cloudCard = card();
        cloudCard.addView(big("☁️ " + store.getAccountDisplayName()));
        cloudCard.addView(body(store.getAccountEmail()));
        cloudCard.addView(body("마지막 동기화: " + store.getLastSyncAt()));
        Button sync = primaryButton("학습 기록 지금 동기화");
        sync.setOnClickListener(v -> viewModel.syncNow());
        cloudCard.addView(sync, new LinearLayout.LayoutParams(-1, dp(48)));
        Button catalog = outlineButton("최신 학습 자료 불러오기");
        catalog.setOnClickListener(v -> viewModel.refreshCatalog());
        cloudCard.addView(catalog);
        Button logout = outlineButton("로그아웃");
        logout.setOnClickListener(v -> viewModel.logout());
        cloudCard.addView(logout);
        content.addView(cloudCard);

        content.addView(sectionTitle("학습 알림과 캘린더"));
        LinearLayout reminderCard = card();
        reminderCard.addView(big(store.isReminderEnabled()
                ? "🔔 매일 " + String.format(Locale.KOREA, "%02d:%02d", store.getReminderHour(), store.getReminderMinute())
                : "🔕 학습 알림 꺼짐"));
        reminderCard.addView(body("추천 영상, 승급 퀴즈, 찜한 교육 일정을 놓치지 않도록 원하는 시간에 알려드립니다."));
        Button reminder = primaryButton(store.isReminderEnabled() ? "알림 시간 변경" : "매일 학습 알림 켜기");
        reminder.setOnClickListener(v -> showReminderTimePicker());
        reminderCard.addView(reminder, new LinearLayout.LayoutParams(-1, dp(48)));
        Button examReminder = outlineButton("시험·자격 일정 추가");
        examReminder.setOnClickListener(v -> showExamReminderDialog());
        reminderCard.addView(examReminder);
        if (store.isReminderEnabled()) {
            Button disableReminder = outlineButton("학습 알림 끄기");
            disableReminder.setOnClickListener(v -> {
                NotificationHelper.cancelDaily(this);
                store.setReminderEnabled(false, store.getReminderHour(), store.getReminderMinute());
                toast("학습 알림을 껐습니다.");
                showApp(6);
            });
            reminderCard.addView(disableReminder);
        }
        content.addView(reminderCard);

        content.addView(sectionTitle("다이아 인증 항로"));
        LinearLayout diamondCard = card();
        diamondCard.addView(tierSummaryRow(
                "다이아",
                "다이아 인증 항로",
                "고급 퀴즈 · 자격 증빙 · 해양 프로젝트",
                dp(72),
                dp(84)
        ));
        diamondCard.addView(body("세 항목을 모두 완료하고 검토 승인을 받으면 다이아 인증 항로가 완성됩니다."));
        diamondCard.addView(note("고급 퀴즈 16/20: " + statusLabel(store.isDiamondAdvancedQuizPassed() ? "approved" : "not_submitted"),
                store.isDiamondAdvancedQuizPassed() ? SUCCESS : MUTED));
        diamondCard.addView(note("자격 증빙: " + statusLabel(store.getCertificationStatus()), statusColor(store.getCertificationStatus())));
        diamondCard.addView(note("해양 프로젝트: " + statusLabel(store.getProjectStatus()), statusColor(store.getProjectStatus())));
        if (PromotionRules.rank(tier) >= PromotionRules.rank("플래티넘")) {
            Button certification = outlineButton("자격 증빙 제출");
            certification.setOnClickListener(v -> showDiamondEvidenceDialog("certification"));
            diamondCard.addView(certification);
            Button project = outlineButton("해양 프로젝트 제출");
            project.setOnClickListener(v -> showDiamondEvidenceDialog("project"));
            diamondCard.addView(project);
            Button refresh = primaryButton("검토 상태 새로고침");
            refresh.setOnClickListener(v -> viewModel.refreshDiamondStatus());
            diamondCard.addView(refresh, new LinearLayout.LayoutParams(-1, dp(48)));
        } else {
            diamondCard.addView(body("플래티넘에 도달하면 고급 퀴즈와 증빙 제출 메뉴가 활성화됩니다."));
        }
        content.addView(diamondCard);



        content.addView(sectionTitle("프로필 초기화"));
        LinearLayout account = card();
        account.addView(body("초기화하면 이 기기의 프로필, XP, 티어, 역량 증거, 퀴즈 결과와 찜 목록이 삭제되고 로그인 화면으로 돌아갑니다."));
        Button reset = outlineButton("프로필과 학습 기록 초기화");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("모든 기록을 초기화할까요?")
                .setMessage("프로필, XP, 승급 티어, 역량 증거, 퀴즈 기록, 찜과 로그인 정보가 삭제됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (dialog, which) -> {
                    NotificationHelper.cancelDaily(this);
                    store.reset();
                    clearQuizSession();
                    showWelcomeScreen();
                }).show());
        account.addView(reset);
        content.addView(account);
    }

    private Map<String, Integer> currentSkillMastery() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String topic : SkillProfileCatalog.TOPICS) values.put(topic, store.getSkillMastery(topic));
        return values;
    }

    private Map<String, Integer> currentSkillEvidence() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String topic : SkillProfileCatalog.TOPICS) values.put(topic, store.getSkillEvidenceCount(topic));
        return values;
    }

    private void renderSkillMapDetail(LinearLayout parent, String topic, Map<String, Integer> masteryMap,
                                      Map<String, Integer> evidenceMap) {
        parent.removeAllViews();
        SkillProfileCatalog.SkillDescriptor descriptor = SkillProfileCatalog.descriptor(topic);
        int score = masteryMap.getOrDefault(topic, 50);
        int evidence = evidenceMap.getOrDefault(topic, 0);
        int predicted = SkillProfileCatalog.predictedNextScore(score, evidence);

        parent.addView(big(topic + " · " + SkillProfileCatalog.scoreLevel(score) + " " + score + "점"));
        parent.addView(body(evidence > 0
                ? "점수 근거: 퀴즈 답변, 학습 완료 또는 미션 기록 " + evidence + "개를 반영했습니다."
                : "점수 근거: 아직 분야별 증거가 없어 진단 전 기준값 50점으로 표시됩니다."));
        parent.addView(body("하위 역량: " + descriptor.subSkills));
        parent.addView(body("NCS 연계: " + descriptor.ncsCompetencies));
        parent.addView(body("연결 진로: " + descriptor.career));
        parent.addView(note("다음 활동 후 예상 " + predicted + "점 · " + descriptor.nextAction, predicted > score ? OCEAN : MUTED));
        Button learning = outlineButton(topic + " 관련 학습 보기");
        learning.setOnClickListener(v -> {
            learningSubTab = "video";
            showApp(1);
        });
        parent.addView(learning, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addEvidencePortfolioCard() {
        PortfolioPdfExporter.PortfolioData data = buildPortfolioData();
        int totalEvidence = 0;
        for (int value : data.evidence.values()) totalEvidence += value;

        LinearLayout portfolio = card();
        portfolio.addView(big(data.targetCareer + " 준비도 " + data.careerReadiness + "점"));
        portfolio.addView(body("완료 학습 " + data.learningEvidence.size() + "건 · 현장 미션 " + data.missionEvidence.size()
                + "건 · 역량 증거 " + totalEvidence + "개를 공유 가능한 PDF로 정리합니다."));
        portfolio.addView(note(store.hasCloudSession() ? "공유 시 서버 서명 자격 증명을 새로 발급합니다." : "현재는 로컬 초안이며 외부 검증이 불가능합니다.", OCEAN));
        portfolio.addView(body("포트폴리오는 앱에서 완료 조건이 확인된 기록과 현재 검토 상태만 포함합니다. 외부 기관 승인은 승인 완료 상태일 때만 표시됩니다."));

        LinearLayout actions = row();
        Button preview = outlineButton("미리보기");
        preview.setOnClickListener(v -> showPortfolioPreview());
        Button share = primaryButton("PDF 생성 및 공유");
        share.setOnClickListener(v -> sharePortfolioPdf());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(46), 1);
        left.setMargins(0, 0, dp(6), 0);
        actions.addView(preview, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(46), 1);
        right.setMargins(dp(6), 0, 0, 0);
        actions.addView(share, right);
        portfolio.addView(actions);
        content.addView(portfolio);
    }

    private PortfolioPdfExporter.PortfolioData buildPortfolioData() {
        UserProfile profile = store.getProfile();
        Map<String, Integer> mastery = currentSkillMastery();
        Map<String, Integer> evidence = currentSkillEvidence();
        String targetCareer = store.getTargetCareer();

        List<String> completedIds = new ArrayList<>(store.getCompletedContentIds());
        completedIds.sort(String::compareTo);
        List<String> learningEvidence = new ArrayList<>();
        for (String id : completedIds) {
            String reflection = store.getContentReflection(id);
            String summary = displayNameForId(id);
            if (!reflection.isEmpty()) summary += " · 학습 소감: " + reflection;
            learningEvidence.add(summary);
        }

        List<String> missionEvidence = new ArrayList<>(store.getMissionBadges());
        missionEvidence.sort(String::compareTo);

        List<String> quizEvidence = new ArrayList<>();
        if (store.getQuizAttempts() > 0) {
            quizEvidence.add("최근 결과: " + plainTierCopy(store.getLastQuizSummary()));
            quizEvidence.add("브론즈 최고 " + store.getBestQuizScore("브론즈") + "/10");
            quizEvidence.add("실버 최고 " + store.getBestQuizScore("실버") + "/12");
            quizEvidence.add("골드 최고 " + store.getBestQuizScore("골드") + "/15");
            quizEvidence.add("플래티넘 최고 " + store.getBestQuizScore("플래티넘") + "/20");
        }

        List<String> diamondEvidence = new ArrayList<>();
        diamondEvidence.add("고급 퀴즈: " + statusLabel(store.isDiamondAdvancedQuizPassed() ? "approved" : "not_submitted"));
        diamondEvidence.add("자격 증빙: " + statusLabel(store.getCertificationStatus()));
        diamondEvidence.add("해양 프로젝트: " + statusLabel(store.getProjectStatus()));

        StringBuilder canonical = new StringBuilder();
        canonical.append(store.getNickname()).append('|').append(store.getAccountEmail()).append('|')
                .append(store.getTier()).append('|').append(profile.xp).append('|').append(targetCareer);
        for (String topic : SkillProfileCatalog.TOPICS) {
            canonical.append('|').append(topic).append(':').append(mastery.getOrDefault(topic, 50))
                    .append(':').append(evidence.getOrDefault(topic, 0));
        }
        for (String item : learningEvidence) canonical.append("|learning:").append(item);
        for (String item : missionEvidence) canonical.append("|mission:").append(item);
        for (String item : quizEvidence) canonical.append("|quiz:").append(item);
        for (String item : diamondEvidence) canonical.append("|diamond:").append(item);

        return new PortfolioPdfExporter.PortfolioData(
                store.getNickname(),
                new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date()),
                PortfolioPdfExporter.verificationCode(canonical.toString()),
                store.getTier(),
                profile.xp,
                targetCareer,
                SkillProfileCatalog.careerReadiness(targetCareer, mastery),
                profile.ageGroup,
                profile.interest,
                profile.goal,
                profile.level,
                mastery,
                evidence,
                learningEvidence,
                missionEvidence,
                quizEvidence,
                diamondEvidence,
                SkillProfileCatalog.strongestTopics(mastery, 3),
                SkillProfileCatalog.weakestTopics(mastery, 2),
                false, "", "", ""
        );
    }

    private void showPortfolioPreview() {
        PortfolioPdfExporter.PortfolioData data = buildPortfolioData();
        ScrollView scroll = new ScrollView(this);
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(20), dp(12), dp(20), dp(12));
        preview.addView(big(data.nickname + " · " + data.tier));
        preview.addView(body(data.targetCareer + " 준비도 " + data.careerReadiness + "점"));
        preview.addView(note(store.hasCloudSession() ? "PDF 공유 시 서버 서명과 검증 QR이 포함됩니다." : "로컬 초안 미리보기입니다.", OCEAN));
        preview.addView(label("역량 요약"));
        for (String topic : SkillProfileCatalog.TOPICS) {
            preview.addView(body("• " + topic + " " + data.mastery.getOrDefault(topic, 50) + "점 · 증거 "
                    + data.evidence.getOrDefault(topic, 0) + "개"));
        }
        preview.addView(label("포함 증거"));
        preview.addView(body("완료 학습 " + data.learningEvidence.size() + "건 · 현장 미션 " + data.missionEvidence.size()
                + "건 · 퀴즈 응시 " + store.getQuizAttempts() + "회"));
        preview.addView(body("자격 증빙 " + statusLabel(store.getCertificationStatus()) + " · 해양 프로젝트 "
                + statusLabel(store.getProjectStatus())));
        scroll.addView(preview);

        new AlertDialog.Builder(this)
                .setTitle("해양 역량 포트폴리오 미리보기")
                .setView(scroll)
                .setNegativeButton("닫기", null)
                .setPositiveButton("PDF 공유", (dialog, which) -> sharePortfolioPdf())
                .show();
    }

    private void sharePortfolioPdf() {
        PortfolioPdfExporter.PortfolioData localData = buildPortfolioData();
        toast("서버 자격 증명을 발급하고 PDF를 생성하고 있습니다.");
        executor.execute(() -> {
            try {
                PortfolioPdfExporter.PortfolioData data = localData;
                if (store.hasCloudSession() && cloudRepository.isCloudConfigured()) {
                    ApiModels.PortfolioCredentialResponse credential = cloudRepository.issuePortfolioCredential();
                    data = localData.withCredential(credential.credentialId, credential.verifyUrl, credential.signature, credential.issuedAt);
                }
                File file = PortfolioPdfExporter.export(this, data);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                runOnUiThread(() -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/pdf");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.setClipData(ClipData.newRawUri("bluepath portfolio", uri));
                    share.putExtra(Intent.EXTRA_SUBJECT, "BluePath 해양 역량 포트폴리오");
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "포트폴리오 PDF 공유"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("포트폴리오 생성 실패: " + safeMessage(e)));
            }
        });
    }

    private void addSkillSummaryCard() {
        String[] topics = {"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"};
        String lowestTopic = store.getProfile().interest;
        int lowest = 101;
        boolean hasEvidence = false;
        for (String topic : topics) {
            if (store.getSkillEvidenceCount(topic) <= 0) continue;
            hasEvidence = true;
            int mastery = store.getSkillMastery(topic);
            if (mastery < lowest) {
                lowest = mastery;
                lowestTopic = topic;
            }
        }
        LinearLayout card = card();
        card.addView(label("OCEAN SKILL SIGNAL"));
        card.addView(big(hasEvidence ? "우선 보완 역량 · " + lowestTopic : "첫 역량 진단이 필요합니다"));
        card.addView(body(hasEvidence
                ? lowestTopic + " 숙련도 " + lowest + "점입니다. 이 역량을 보완하는 콘텐츠와 교육 과정이 추천 상단에 배치됩니다."
                : "퀴즈를 한 번 완료하면 문항별 결과가 분야별 숙련도로 전환되고 다음 추천이 달라집니다."));
        final boolean evidenceAvailable = hasEvidence;
        Button go = outlineButton(evidenceAvailable ? "MY에서 전체 숙련도 보기" : "퀴즈로 진단 시작");
        go.setOnClickListener(v -> showApp(evidenceAvailable ? 6 : 2));
        card.addView(go);
        content.addView(card);
    }

    private void addSkillProgress(LinearLayout parent, String topic) {
        int score = store.getSkillMastery(topic);
        int evidence = store.getSkillEvidenceCount(topic);
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = body(topic);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = label(score + "점 · 증거 " + evidence + "개");
        value.setGravity(Gravity.END);
        header.addView(value);
        parent.addView(header);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(score);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(9));
        params.setMargins(0, 0, 0, dp(8));
        parent.addView(progress, params);
    }

    private LinearLayout addExpandable(LinearLayout parent, String titleText) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutTransition(new LayoutTransition());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = label(titleText);
        header.addView(titleView);
        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextColor(OCEAN);
        arrow.setTextSize(16);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        arrow.setPadding(dp(6), dp(4), dp(6), 0);
        header.addView(arrow);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);

        header.setOnClickListener(v -> {
            boolean open = panel.getVisibility() == View.VISIBLE;
            panel.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.setText(open ? "▾" : "▴");
        });

        wrapper.addView(header);
        wrapper.addView(panel);
        parent.addView(wrapper);
        return panel;
    }

    private void addReasonList(LinearLayout parent, List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) return;
        LinearLayout panel = addExpandable(parent, "추천 근거 " + reasons.size() + "개");
        for (String reason : reasons) panel.addView(body("• " + reason));
    }

    private String joinList(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }

    private void showContentCompletionDialog(ContentItem item) {
        if (store.getCompletedContentIds().contains(item.id)) {
            toast("이미 완료 인증된 학습입니다.");
            return;
        }
        if (!store.hasVerifiedVideoCompletion(item.id, item.minutes)) {
            toast("실제 재생 기준 70% 이상과 최소 학습 시간을 충족해야 합니다. 현재 "
                    + store.getVideoProgressPercent(item.id) + "% · " + store.getVideoWatchSeconds(item.id) + "초입니다.");
            return;
        }
        if (!store.hasCloudSession() || !cloudRepository.isCloudConfigured()) {
            toast("로그인 후 서버 검증을 받아야 영상 학습을 완료할 수 있습니다.");
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(body("시청 인증이 완료되었습니다. 소감을 건너뛰면 70XP, 10자 이상 작성하면 100XP를 받습니다."));
        EditText reflection = inputField("핵심 내용 또는 새롭게 알게 된 점", "");
        reflection.setSingleLine(false);
        reflection.setMinLines(3);
        form.addView(reflection);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("학습 완료하기")
                .setView(form)
                .setNegativeButton("건너뛰기 · 70XP", null)
                .setPositiveButton("작성 완료 · 100XP", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v ->
                    submitVideoCompletion(item, "", dialog));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = reflection.getText().toString().trim();
                if (value.length() < 10) {
                    reflection.setError("10자 이상 작성하거나 70XP 건너뛰기를 선택해 주세요.");
                    return;
                }
                submitVideoCompletion(item, value, dialog);
            });
        });
        dialog.show();
    }

    private void submitVideoCompletion(ContentItem item, String reflection, AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        executor.execute(() -> {
            try {
                ApiModels.VideoCompletionResponse result = cloudRepository.completeVideo(item.id, reflection);
                runOnUiThread(() -> {
                    if (result.xpAwarded > 0) {
                        if (!reflection.isEmpty()) store.saveContentReflection(item.id, reflection);
                        viewModel.recordLearning("video", item.id, item.title,
                                reflection.isEmpty() ? "completed_without_reflection" : "completed_with_reflection");
                        recordRouteCompletionByTarget(item.id);
                    }
                    dialog.dismiss();
                    toast(result.xpAwarded > 0
                            ? "학습 완료! " + result.xpAwarded + "XP를 받았습니다."
                            : "이미 완료된 학습입니다.");
                    showApp(currentTab);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("영상 학습 완료 실패: " + safeMessage(error));
                });
            }
        });
    }

    private void addIdListCard(String title, Set<String> ids) {
        LinearLayout card = card();
        card.addView(big(title + " · " + ids.size() + "개"));
        if (ids.isEmpty()) {
            card.addView(body("아직 기록이 없습니다."));
        } else {
            int shown = 0;
            for (String id : ids) {
                card.addView(body("• " + displayNameForId(id)));
                if (++shown >= 5) break;
            }
            if (ids.size() > shown) card.addView(label("외 " + (ids.size() - shown) + "개"));
        }
        content.addView(card);
    }

    private String displayNameForId(String id) {
        for (ContentItem item : DataRepository.contents()) if (item.id.equals(id)) return item.title;
        for (ProgramItem item : DataRepository.programs()) if (item.id.equals(id)) return item.title;
        for (EventItem item : DataRepository.events()) if (item.id.equals(id)) return item.title;
        for (PaperItem item : DataRepository.papers()) if (item.id.equals(id)) return item.title;
        for (CareerItem item : DataRepository.careers()) if (item.id.equals(id)) return item.title;
        return id;
    }

    private void addContentCard(ContentItem item, boolean compact) {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        int score = RecommendationEngine.scoreContent(item, p, tier, store);
        boolean completed = store.getCompletedContentIds().contains(item.id);
        boolean verified = store.hasVerifiedVideoCompletion(item.id, item.minutes);
        boolean started = store.isContentStarted(item.id);
        LinearLayout card = card();
        card.setLayoutTransition(new LayoutTransition());

        String learningState = completed
                ? "학습 완료"
                : verified ? "시청 인증 완료"
                : started ? "검증 시청 " + store.getVideoProgressPercent(item.id) + "%" : "학습 전";
        LinearLayout summaryRow = row();
        summaryRow.setGravity(Gravity.TOP);

        LinearLayout mediaColumn = new LinearLayout(this);
        mediaColumn.setOrientation(LinearLayout.VERTICAL);
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackgroundResource(R.drawable.bg_thumb);
        thumbnail.setContentDescription(item.title + " 영상 썸네일");
        String thumbnailUrl = youtubeThumbnailUrl(item.url);
        Glide.with(this)
                .load(thumbnailUrl)
                .placeholder(R.drawable.bg_thumb)
                .error(R.drawable.bg_thumb)
                .fallback(R.drawable.bg_thumb)
                .transform(new CenterCrop(), new RoundedCorners(dp(8)))
                .into(thumbnail);
        mediaColumn.addView(thumbnail, new LinearLayout.LayoutParams(dp(88), dp(64)));

        TextView detailToggle = label("자세히 보기 ▾");
        detailToggle.setTextColor(OCEAN);
        detailToggle.setTextSize(10);
        detailToggle.setGravity(Gravity.CENTER);
        detailToggle.setSingleLine(true);
        detailToggle.setBackgroundResource(R.drawable.bg_secondary_button);
        detailToggle.setPadding(dp(4), 0, dp(4), 0);
        detailToggle.setClickable(true);
        detailToggle.setFocusable(true);
        detailToggle.setContentDescription(item.title + " 상세 정보 펼치기");
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(32));
        toggleParams.setMargins(0, dp(6), 0, 0);
        mediaColumn.addView(detailToggle, toggleParams);

        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(dp(88), -2);
        mediaParams.setMargins(0, 0, dp(12), 0);
        summaryRow.addView(mediaColumn, mediaParams);

        LinearLayout summaryCopy = new LinearLayout(this);
        summaryCopy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout statusRow = row();
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tierChip = tierChip(item.requiredTier);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(26));
        chipParams.setMargins(0, 0, dp(7), 0);
        statusRow.addView(tierChip, chipParams);
        statusRow.addView(label(item.difficulty + " 난도 · " + learningState));
        summaryCopy.addView(statusRow);
        TextView title = big(item.title);
        title.setTextSize(15);
        summaryCopy.addView(title);
        summaryCopy.addView(body((item.minutes > 0 ? "영상 " + item.minutes + "분" : "영상") + " · 적합도 " + score));
        summaryRow.addView(summaryCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView bookmarkHeart = new TextView(this);
        bookmarkHeart.setTextSize(24);
        bookmarkHeart.setGravity(Gravity.CENTER);
        bookmarkHeart.setClickable(true);
        bookmarkHeart.setFocusable(true);
        updateBookmarkHeart(bookmarkHeart, store.isBookmarked(item.id), item.title);
        bookmarkHeart.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            boolean bookmarked = store.isBookmarked(item.id);
            updateBookmarkHeart(bookmarkHeart, bookmarked, item.title);
            viewModel.recordLearning("bookmark", item.id, item.title, bookmarked ? "saved" : "removed");
            toast(bookmarked ? "찜 목록에 저장했습니다." : "찜을 해제했습니다.");
        });
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        heartParams.setMargins(dp(4), 0, 0, 0);
        summaryRow.addView(bookmarkHeart, heartParams);
        card.addView(summaryRow);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        if (completed) {
            addContentReflectionBlock(details, item);
        }
        details.addView(body("출처: " + item.source));
        details.addView(body("분야: " + item.topic + " · 연결 진로: " + item.careerTag));
        addReasonList(details, RecommendationEngine.contentReasons(item, p, tier, store));
        if (completed) {
            details.addView(note("학습 완료 인증 · XP 반영됨", SUCCESS));
        } else if (verified) {
            details.addView(note("시청 인증 완료 · 학습 완료하기를 눌러 XP를 받을 수 있습니다.", SUCCESS));
        } else if (started) {
            details.addView(note("검증 시청 " + store.getVideoProgressPercent(item.id) + "% · "
                    + store.getVideoWatchSeconds(item.id) + "초 · 기준 충족 후 시청 인증이 완료됩니다.", OCEAN));
        } else {
            details.addView(note("앱 내 검증 플레이어에서 시청 기록을 쌓은 뒤 학습 완료를 인증할 수 있습니다.", MUTED));
        }

        String watchLabel = completed ? "영상 다시보기" : (started || verified) ? "영상 계속 보기" : "영상 학습 시작";
        Button watch = primaryButton(watchLabel);
        watch.setOnClickListener(v -> {
            store.markContentStarted(item.id);
            viewModel.recordLearning("video", item.id, item.title, "started");
            Intent verifiedVideoIntent = new Intent(this, VerifiedVideoActivity.class);
            verifiedVideoIntent.putExtra(VerifiedVideoActivity.EXTRA_CONTENT_ID, item.id);
            verifiedVideoIntent.putExtra(VerifiedVideoActivity.EXTRA_TITLE, item.title);
            verifiedVideoIntent.putExtra(VerifiedVideoActivity.EXTRA_URL, item.url);
            verifiedVideoIntent.putExtra(VerifiedVideoActivity.EXTRA_MINUTES, item.minutes);
            startActivity(verifiedVideoIntent);
        });
        details.addView(watch, new LinearLayout.LayoutParams(-1, dp(44)));

        if (verified && !completed) {
            Button complete = outlineButton("학습 완료하기");
            complete.setOnClickListener(v -> showContentCompletionDialog(item));
            details.addView(complete, new LinearLayout.LayoutParams(-1, dp(46)));
        }
        card.addView(details);

        detailToggle.setOnClickListener(v -> {
            boolean expanded = details.getVisibility() == View.VISIBLE;
            details.setVisibility(expanded ? View.GONE : View.VISIBLE);
            detailToggle.setText(expanded ? "자세히 보기 ▾" : "접기 ▴");
            detailToggle.setContentDescription(item.title + (expanded ? " 상세 정보 펼치기" : " 상세 정보 접기"));
        });
        content.addView(card);
    }

    private void addContentReflectionBlock(LinearLayout details, ContentItem item) {
        String savedReflection = store.getContentReflection(item.id);
        details.addView(label("내 학습 소감"));
        if (!savedReflection.isEmpty()) {
            details.addView(body(savedReflection));
            Button edit = outlineButton("소감 수정하기");
            edit.setOnClickListener(v -> showContentReflectionEditDialog(item));
            details.addView(edit, new LinearLayout.LayoutParams(-1, dp(44)));
        } else {
            details.addView(body("아직 작성한 소감이 없습니다. 학습하며 새롭게 알게 된 점을 남겨 보세요."));
            Button write = outlineButton("소감 작성하기");
            write.setOnClickListener(v -> showContentReflectionEditDialog(item));
            details.addView(write, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#D7E3E8"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(10), 0, dp(6));
        details.addView(divider, dividerParams);
    }

    private void showContentReflectionEditDialog(ContentItem item) {
        EditText reflection = inputField("핵심 내용 또는 새롭게 알게 된 점", store.getContentReflection(item.id));
        reflection.setSingleLine(false);
        reflection.setMinLines(3);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("학습 소감")
                .setMessage("학습하며 새롭게 알게 된 점을 10자 이상 기록해 주세요.")
                .setView(reflection)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = reflection.getText().toString().trim();
            if (value.length() < 10) {
                reflection.setError("10자 이상 작성해 주세요.");
                return;
            }
            store.saveContentReflection(item.id, value);
            if (viewModel.isCloudConfigured()) viewModel.syncNow();
            dialog.dismiss();
            toast("학습 소감을 저장했습니다.");
            showApp(currentTab);
        }));
        dialog.show();
    }

    private TextView tierChip(String tier) {
        String value = plainTierText(tier);
        TextView chip = new TextView(this);
        chip.setText(value);
        chip.setTextSize(11);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(tierChipTextColor(value));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(tierChipColor(value));
        background.setCornerRadius(dp(13));
        chip.setBackground(background);
        return chip;
    }

    private int tierChipColor(String tier) {
        if ("실버".equals(tier)) return Color.parseColor("#94A3B8");
        if ("골드".equals(tier)) return Color.parseColor("#EAB308");
        if ("플래티넘".equals(tier)) return Color.parseColor("#22C1C3");
        if ("다이아".equals(tier)) return Color.parseColor("#60A5FA");
        return Color.parseColor("#B7794A");
    }

    private int tierChipTextColor(String tier) {
        return "브론즈".equals(tier) || "플래티넘".equals(tier) ? Color.WHITE : NAVY;
    }

    private void updateBookmarkHeart(TextView heart, boolean bookmarked, String title) {
        heart.setText(bookmarked ? "♥" : "♡");
        heart.setTextColor(bookmarked ? Color.parseColor("#E11D48") : Color.parseColor("#94A3B8"));
        heart.setContentDescription((bookmarked ? "찜 해제: " : "찜하기: ") + title);
    }

    private String youtubeThumbnailUrl(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url.trim());
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);

            String videoId = null;
            List<String> segments = uri.getPathSegments();
            if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
                if (!segments.isEmpty()) videoId = segments.get(0);
            } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")
                    || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")) {
                if ("/watch".equals(uri.getPath())) {
                    videoId = uri.getQueryParameter("v");
                } else if (segments.size() >= 2
                        && ("embed".equals(segments.get(0))
                        || "shorts".equals(segments.get(0))
                        || "live".equals(segments.get(0)))) {
                    videoId = segments.get(1);
                }
            }
            if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{6,20}")) return null;
            return "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addPaperCard(PaperItem item) {
        boolean completed = store.getCompletedContentIds().contains(item.id);
        boolean started = store.isContentStarted(item.id);
        LinearLayout card = card();
        String paperState = "retracted".equalsIgnoreCase(item.paperStatus) ? "철회됨"
                : "corrected".equalsIgnoreCase(item.paperStatus) ? "정정본" : "현재본";
        card.addView(label(item.topic + " · " + item.year + " · " + paperState + (item.doi.isEmpty() ? "" : " · DOI " + item.doi)));
        if (!item.versionNote.isEmpty()) card.addView(note(item.versionNote, "retracted".equalsIgnoreCase(item.paperStatus) ? DANGER : MUTED));
        card.addView(big("▤ " + item.title));
        card.addView(body(item.authors + (item.source.isEmpty() ? "" : " · " + item.source)));
        if (!item.abstractText.isEmpty()) card.addView(body(item.abstractText));
        if (completed) card.addView(note("논문 학습 완료 · 요약 기록과 역량 증거가 저장되었습니다.", SUCCESS));
        else if (started) card.addView(note("원문 열람 기록됨 · 핵심 주장과 배운 점을 제출해 완료할 수 있습니다.", OCEAN));

        LinearLayout actions = row();
        Button open = primaryButton("원문 열기");
        open.setOnClickListener(v -> {
            store.markContentStarted(item.id);
            viewModel.recordLearning("paper", item.id, item.title, "opened");
            openUrl(item.url);
            showApp(1);
        });
        Button bookmark = outlineButton(store.isBookmarked(item.id) ? "찜 해제" : "찜");
        bookmark.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            viewModel.recordLearning("bookmark", item.id, item.title, store.isBookmarked(item.id) ? "saved" : "removed");
            showApp(1);
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(44), 1);
        left.setMargins(0, 0, dp(6), 0);
        actions.addView(open, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(44), 1);
        right.setMargins(dp(6), 0, 0, 0);
        actions.addView(bookmark, right);
        card.addView(actions);
        if (started && !completed && !"retracted".equalsIgnoreCase(item.paperStatus)) {
            Button complete = outlineButton("논문 학습 완료 기록");
            complete.setOnClickListener(v -> showPaperCompletionDialog(item));
            card.addView(complete, new LinearLayout.LayoutParams(-1, dp(46)));
        } else if ("retracted".equalsIgnoreCase(item.paperStatus)) {
            card.addView(note("철회 논문은 참고 열람만 가능하며 학습 자격 증명이나 XP를 만들 수 없습니다.", DANGER));
        }
        content.addView(card);
    }

    private void showPaperCompletionDialog(PaperItem item) {
        EditText reflection = inputField("핵심 주장, 근거와 새롭게 알게 된 점", store.getContentReflection(item.id));
        reflection.setSingleLine(false);
        reflection.setMinLines(4);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("논문 학습 완료")
                .setMessage("원문의 핵심 주장과 근거, 새롭게 알게 된 점을 포함해 40자 이상 기록해 주세요.")
                .setView(reflection)
                .setNegativeButton("취소", null)
                .setPositiveButton("완료 기록", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = reflection.getText().toString().trim();
            if (value.length() < 40) {
                reflection.setError("40자 이상 작성해 주세요.");
                return;
            }
            if (!store.hasCloudSession() || !cloudRepository.isCloudConfigured()) {
                reflection.setError("로그인 후 서버 검증을 받아야 논문 학습을 완료할 수 있습니다.");
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            executor.execute(() -> {
                try {
                    ApiModels.PaperCompletionResponse result = cloudRepository.completePaper(item.id, value);
                    runOnUiThread(() -> {
                        store.markCompleted(item.id);
                        store.saveContentReflection(item.id, value);
                        viewModel.recordLearning("paper", item.id, item.title, "server_verified");
                        dialog.dismiss();
                        toast("논문 학습 증거를 서버에서 검증했습니다. XP +" + result.xpAwarded);
                        showApp(1);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast("논문 학습 검증 실패: " + safeMessage(error));
                    });
                }
            });
        }));
        dialog.show();
    }

    private void showProgramParticipationDialog(ProgramItem item) {
        if (!store.hasCloudSession() || !cloudRepository.isCloudConfigured()) {
            toast("로그인과 서버 연결이 필요합니다.");
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(body(item.title));

        form.addView(label("참여 상태"));
        Spinner statusSpinner = new Spinner(this);
        String[] statusLabels = {"신청", "참석", "수료"};
        String[] statusValues = {"enrolled", "attended", "completed"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, statusLabels);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);
        form.addView(statusSpinner, new LinearLayout.LayoutParams(-1, dp(52)));

        form.addView(label("사전 평가 점수 선택 입력"));
        EditText preAssessment = inputField("0부터 100", "");
        preAssessment.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(preAssessment);

        form.addView(label("사후 평가 점수 선택 입력"));
        EditText postAssessment = inputField("0부터 100", "");
        postAssessment.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(postAssessment);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("교육 참여 기록")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String preText = preAssessment.getText().toString().trim();
            String postText = postAssessment.getText().toString().trim();
            Integer preScore = null;
            Integer postScore = null;

            try {
                if (!preText.isEmpty()) preScore = Integer.parseInt(preText);
                if (!postText.isEmpty()) postScore = Integer.parseInt(postText);
            } catch (NumberFormatException error) {
                toast("평가 점수는 0부터 100 사이의 정수로 입력해 주세요.");
                return;
            }

            if (preScore != null && (preScore < 0 || preScore > 100)) {
                preAssessment.setError("0부터 100 사이로 입력해 주세요.");
                return;
            }
            if (postScore != null && (postScore < 0 || postScore > 100)) {
                postAssessment.setError("0부터 100 사이로 입력해 주세요.");
                return;
            }

            int selectedPosition = statusSpinner.getSelectedItemPosition();
            if (selectedPosition < 0 || selectedPosition >= statusValues.length) selectedPosition = 0;
            String selectedStatus = statusValues[selectedPosition];
            Integer finalPreScore = preScore;
            Integer finalPostScore = postScore;

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            executor.execute(() -> {
                try {
                    ApiModels.ProgramParticipationResponse result = cloudRepository.saveProgramParticipation(
                            item.id, item.title, selectedStatus, finalPreScore, finalPostScore);
                    runOnUiThread(() -> {
                        viewModel.recordLearning(
                                "program_participation", item.id, item.title, "server_" + result.status);
                        dialog.dismiss();
                        String savedStatus;
                        if ("completed".equals(result.status)) savedStatus = "수료";
                        else if ("attended".equals(result.status)) savedStatus = "참석";
                        else savedStatus = "신청";
                        toast("교육 참여 상태를 " + savedStatus + "으로 저장했습니다.");
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast("교육 참여 기록 실패: " + safeMessage(error));
                    });
                }
            });
        }));
        dialog.show();
    }

    private void addProgramCard(ProgramItem item) {
        UserProfile p = store.getProfile();
        int score = RecommendationEngine.scoreProgram(item, p, store);
        String status = RecommendationEngine.scheduleStatus(item.startDate, item.endDate);
        boolean archived = RecommendationEngine.isArchived(item.startDate, item.endDate);
        LinearLayout card = card();
        card.addView(label(item.topic + " · 추천 " + score + "점 · " + status));
        card.addView(big("📚 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate + " · " + item.target + " · " + item.method));
        card.addView(body("시간대 " + item.timezone
                + (item.capacity > 0 ? " · 정원 " + item.capacity + "명" : "")
                + (item.waitlistAvailable ? " · 대기 신청 가능" : "")
                + (item.applicationDeadline.isEmpty() ? "" : " · 신청 마감 " + item.applicationDeadline)));
        card.addView(body(item.description));
        addReasonList(card, RecommendationEngine.programReasons(item, p, store));
        if (archived) card.addView(note("현재 신청 가능한 일정이 아니라 교육 이력과 수요 분석을 위한 아카이브 자료입니다.", MUTED));
        Button b = outlineButton(store.isBookmarked(item.id) ? "일정 찜 해제" : "일정 찜하기");
        b.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            viewModel.recordLearning("program", item.id, item.title, store.isBookmarked(item.id) ? "saved" : "removed");
            toast("일정 찜 목록을 업데이트했습니다.");
            showApp(currentTab);
        });
        card.addView(b);
        if (!archived && !item.applicationUrl.trim().isEmpty()) {
            Button apply = primaryButton("공식 신청 페이지 열기");
            apply.setOnClickListener(v -> openUrl(item.applicationUrl));
            card.addView(apply);
        }
        if (!archived && !"데이터 확인 필요".equals(status)) {
            Button calendar = outlineButton("내 캘린더에 추가");
            calendar.setOnClickListener(v -> addProgramToCalendar(item));
            card.addView(calendar);
        }
        if (store.hasCloudSession()) {
            Button participation = outlineButton("신청·참석·수료 기록");
            participation.setOnClickListener(v -> showProgramParticipationDialog(item));
            card.addView(participation);
        }
        content.addView(card);
    }

    private void addEventCard(EventItem item) {
        String status = RecommendationEngine.scheduleStatus(item.startDate, item.endDate);
        LinearLayout card = card();
        card.addView(label(item.category + " · " + item.target + " · " + status));
        card.addView(big("🎪 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate + " · " + item.timezone));
        card.addView(body((item.capacity > 0 ? "정원 " + item.capacity + "명 · " : "")
                + (item.waitlistAvailable ? "대기 신청 가능 · " : "")
                + (item.applicationDeadline.isEmpty() ? "신청 마감 정보 없음" : "신청 마감 " + item.applicationDeadline)));
        card.addView(body(item.description));
        card.addView(body("출처: " + item.source));
        if (RecommendationEngine.isArchived(item.startDate, item.endDate)) {
            card.addView(note("종료된 행사입니다. 유사 프로그램 기획과 개인 관심 분석을 위한 아카이브로 제공합니다.", MUTED));
        }
        if (!RecommendationEngine.isArchived(item.startDate, item.endDate) && !item.applicationUrl.trim().isEmpty()) {
            Button apply = primaryButton("공식 안내·신청 페이지 열기");
            apply.setOnClickListener(v -> openUrl(item.applicationUrl));
            card.addView(apply);
        }
        Button b = outlineButton(store.isBookmarked(item.id) ? "이벤트 찜 해제" : "이벤트 찜하기");
        b.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            viewModel.recordLearning("event", item.id, item.title, store.isBookmarked(item.id) ? "saved" : "removed");
            toast("이벤트 찜 목록을 업데이트했습니다.");
            showApp(currentTab);
        });
        card.addView(b);
        content.addView(card);
    }

    private void addCareerCard(CareerItem item) {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        int score = RecommendationEngine.scoreCareer(item, p, tier, store);
        LinearLayout card = card();
        LinearLayout tierRow = row();
        tierRow.setGravity(Gravity.CENTER_VERTICAL);
        TierShieldView recommendedTierShield = tierShield(item.recommendedTier);
        tierRow.addView(recommendedTierShield, new LinearLayout.LayoutParams(dp(40), dp(46)));
        TextView tierMeta = label(item.field + " · 권장 " + plainTierText(item.recommendedTier) + " · 적합도 " + score);
        tierMeta.setPadding(dp(7), 0, 0, 0);
        tierRow.addView(tierMeta, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(tierRow);
        card.addView(big("🧭 " + item.title));
        card.addView(body(item.description));
        addReasonList(card, RecommendationEngine.careerReasons(item, p, tier, store));
        card.addView(label("필요 역량 / NCS"));
        card.addView(body(join(item.ncsUnits, " → ")));
        card.addView(label("실제 데이터에서 연결된 기관 예시"));
        List<String> institutions = RecommendationEngine.relatedInstitutions(item, 4);
        card.addView(body(institutions.isEmpty() ? join(item.workplaces, ", ") : joinList(institutions, " · ")));
        card.addView(note("추천 로드맵: 역량 갭 진단 → 근거 영상 학습 → 승급 퀴즈 → 실제 교육 과정 → 자격·프로젝트 증빙", OCEAN));
        content.addView(card);
    }

    private void showGuardianConsentDialog(boolean onboarding) {
        if (guardianDialogVisible) return;
        guardianDialogVisible = true;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        TextView notice = body("보호자 이메일로 24시간 유효한 확인 링크를 전송합니다. 앱에서 대신 동의할 수 없으며, 보호자가 링크에서 동의 문서 버전 2026-07을 확인해야 완료됩니다.");
        EditText guardianEmail = inputField("보호자 이메일", store.getGuardianEmail());
        form.addView(notice);
        form.addView(label("보호자 이메일"));
        form.addView(guardianEmail);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("보호자 동의 확인")
                .setView(form)
                .setCancelable(!onboarding)
                .setNeutralButton("상태 새로고침", null)
                .setNegativeButton(onboarding ? "로그아웃" : "동의 철회", null)
                .setPositiveButton("확인 링크 보내기", null)
                .create();
        dialog.setOnDismissListener(ignored -> guardianDialogVisible = false);
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String email = guardianEmail.getText().toString().trim();
                if (email.isEmpty()) {
                    toast("보호자 이메일을 입력해 주세요.");
                    return;
                }
                executor.execute(() -> {
                    try {
                        ApiModels.GuardianConsentStatus result = cloudRepository.requestGuardianConsent(email);
                        runOnUiThread(() -> {
                            store.saveGuardianConsent(false, result.guardianEmail);
                            toast("보호자 확인 링크를 전송했습니다. 보호자가 링크에서 동의한 뒤 상태를 새로고침해 주세요.");
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("동의 요청 실패: " + safeMessage(error)));
                    }
                });
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> executor.execute(() -> {
                try {
                    ApiModels.GuardianConsentStatus result = cloudRepository.refreshGuardianConsent();
                    runOnUiThread(() -> {
                        boolean confirmed = "confirmed".equals(result.status);
                        toast(confirmed ? "보호자 동의가 확인되었습니다." : "아직 보호자 확인을 기다리고 있습니다.");
                        if (confirmed) {
                            dialog.dismiss();
                            showApp(onboarding ? 0 : 6);
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> toast("동의 상태 확인 실패: " + safeMessage(error)));
                }
            }));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (onboarding) {
                    store.clearCloudSession();
                    dialog.dismiss();
                    showLoginScreen();
                    return;
                }
                executor.execute(() -> {
                    try {
                        cloudRepository.revokeGuardianConsent();
                        runOnUiThread(() -> {
                            toast("보호자 동의를 철회했습니다. 미성년 프로필은 다시 확인할 때까지 이용할 수 없습니다.");
                            store.clearCloudSession();
                            dialog.dismiss();
                            showLoginScreen();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("동의 철회 실패: " + safeMessage(error)));
                    }
                });
            });
        });
        dialog.show();
    }

    private void showReminderTimePicker() {
        new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2401);
            }
            NotificationHelper.scheduleDaily(this, hourOfDay, minute);
            store.setReminderEnabled(true, hourOfDay, minute);
            toast(String.format(Locale.KOREA, "매일 %02d:%02d에 알려드릴게요.", hourOfDay, minute));
            showApp(6);
        }, store.getReminderHour(), store.getReminderMinute(), true).show();
    }

    private void showDiamondEvidenceDialog(String type) {
        if (!store.hasCloudSession()) {
            toast("증빙 검토를 받으려면 다시 로그인해 주세요.");
            showLoginScreen();
            return;
        }
        boolean certification = "certification".equals(type);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText title = inputField(certification ? "자격·교육 과정명" : "프로젝트 제목", "");
        EditText url = inputField("확인 가능한 링크", "");
        form.addView(label(certification ? "자격 또는 수료 내용" : "해양 프로젝트"));
        form.addView(title);
        form.addView(label("증빙 링크"));
        form.addView(url);
        new AlertDialog.Builder(this)
                .setTitle(certification ? "자격 증빙 제출" : "해양 프로젝트 제출")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("검토 요청", (dialog, which) -> {
                    String titleValue = title.getText().toString().trim();
                    String urlValue = url.getText().toString().trim();
                    if (titleValue.isEmpty() || urlValue.isEmpty()) {
                        toast("제목과 증빙 링크를 모두 입력해 주세요.");
                        return;
                    }
                    viewModel.submitDiamondEvidence(type, titleValue, urlValue);
                }).show();
    }

    private void showExamReminderDialog() {
        EditText title = inputField("예: 해기사 시험 접수 마감", "");
        new AlertDialog.Builder(this)
                .setTitle("시험·자격 일정")
                .setMessage("일정 이름을 입력한 뒤 날짜와 시간을 선택하세요. 기기 캘린더와 알림에 함께 등록됩니다.")
                .setView(title)
                .setNegativeButton("취소", null)
                .setPositiveButton("날짜 선택", (dialog, which) -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty()) {
                        toast("일정 이름을 입력해 주세요.");
                        return;
                    }
                    Calendar selected = Calendar.getInstance();
                    new android.app.DatePickerDialog(this, (dateView, year, month, day) -> {
                        selected.set(year, month, day);
                        new android.app.TimePickerDialog(this, (timeView, hour, minute) -> {
                            selected.set(Calendar.HOUR_OF_DAY, hour);
                            selected.set(Calendar.MINUTE, minute);
                            selected.set(Calendar.SECOND, 0);
                            if (selected.getTimeInMillis() <= System.currentTimeMillis()) {
                                toast("현재 이후의 일정을 선택해 주세요.");
                                return;
                            }
                            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2402);
                            }
                            NotificationHelper.scheduleOneTime(this, value, selected.getTimeInMillis());
                            try {
                                Intent intent = new Intent(Intent.ACTION_INSERT)
                                        .setData(CalendarContract.Events.CONTENT_URI)
                                        .putExtra(CalendarContract.Events.TITLE, value)
                                        .putExtra(CalendarContract.Events.DESCRIPTION, "BluePath 해양 학습·시험 일정")
                                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, selected.getTimeInMillis())
                                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, selected.getTimeInMillis() + 60 * 60 * 1000L);
                                startActivity(intent);
                            } catch (Exception ignored) {
                                toast("알림은 등록했지만 캘린더 앱을 열 수 없습니다.");
                            }
                        }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show();
                    }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show();
                }).show();
    }

    private void addProgramToCalendar(ProgramItem item) {
        if (RecommendationEngine.isArchived(item.startDate, item.endDate)) {
            toast("종료된 아카이브 일정은 캘린더에 추가할 수 없습니다.");
            return;
        }
        try {
            String[] startParts = item.startDate.split("-");
            String[] endParts = item.endDate.split("-");
            Calendar start = Calendar.getInstance();
            start.set(Integer.parseInt(startParts[0]), Integer.parseInt(startParts[1]) - 1,
                    Integer.parseInt(startParts[2]), 9, 0, 0);
            Calendar end = Calendar.getInstance();
            end.set(Integer.parseInt(endParts[0]), Integer.parseInt(endParts[1]) - 1,
                    Integer.parseInt(endParts[2]), 18, 0, 0);
            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, item.title)
                    .putExtra(CalendarContract.Events.DESCRIPTION, item.description)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.getTimeInMillis())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.getTimeInMillis());
            startActivity(intent);
        } catch (Exception e) {
            toast("이 일정의 날짜를 캘린더에 추가할 수 없습니다.");
        }
    }

    private void showPrivacyNotice() {
        new AlertDialog.Builder(this)
                .setTitle("BluePath 개인정보 안내")
                .setMessage("맞춤 추천을 위해 연령대, 관심 분야, 학습 목표, 수준, 영상 학습 시작·완료 증거, 퀴즈 결과와 역량 숙련도를 사용합니다. 앱 이용 전 로그인이 필요하며 학습 기록은 계정과 동기화할 수 있습니다. 인증 토큰은 Android Keystore 기반 저장소에 보호되고, 미성년 사용자는 보호자 동의를 관리할 수 있습니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    private String statusLabel(String status) {
        if ("approved".equals(status)) return "승인 완료";
        if ("pending".equals(status)) return "검토 중";
        if ("rejected".equals(status)) return "보완 필요";
        return "미제출";
    }

    private int statusColor(String status) {
        if ("approved".equals(status)) return SUCCESS;
        if ("rejected".equals(status)) return DANGER;
        return MUTED;
    }

    private void showPromotionManual() {
        new AlertDialog.Builder(this)
                .setTitle("BluePath 승급 매뉴얼")
                .setMessage(tierText(PromotionRules.fullManual()))
                .setPositiveButton("확인", null)
                .show();
    }

    private FrameLayout oceanFrame() {
        applyOceanWindow();
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.parseColor("#062D38"));

        OceanBackgroundView ocean = new OceanBackgroundView(this);
        frame.addView(ocean, new FrameLayout.LayoutParams(-1, -1));

        View tint = new View(this);
        tint.setBackgroundColor(Color.argb(30, 0, 25, 33));
        frame.addView(tint, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private LinearLayout oceanScrollableRoot(int left, int top, int right, int bottom) {
        FrameLayout screen = oceanFrame();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(left, top, right, bottom);
        scroll.addView(layout, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        return layout;
    }

    private void applyOceanWindow() {
        getWindow().setStatusBarColor(Color.parseColor("#073642"));
        getWindow().setNavigationBarColor(Color.parseColor("#03262F"));
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private void applyAppWindow() {
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private LinearLayout authCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundResource(R.drawable.bg_auth_card);
        card.setElevation(dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(params);
        return card;
    }

    private EditText authInputField(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.parseColor("#A9CED1"));
        field.setTextSize(14);
        field.setSingleLine(true);
        field.setSelectAllOnFocus(false);
        field.setBackgroundResource(R.drawable.bg_auth_input);
        field.setPadding(dp(14), 0, dp(14), 0);
        return field;
    }

    private TextView authTitle(String text) {
        TextView view = new TextView(this);
        view.setText(tierText(text));
        view.setTextColor(Color.WHITE);
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(6), 0, dp(10));
        return view;
    }

    private TextView authBig(String text) {
        TextView view = new TextView(this);
        view.setText(tierText(text == null || text.trim().isEmpty() ? "BluePath 사용자" : text));
        view.setTextColor(Color.WHITE);
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(2), 0, dp(5));
        return view;
    }

    private TextView authBody(String text) {
        TextView view = new TextView(this);
        view.setText(tierText(text));
        view.setTextColor(Color.parseColor("#D9F0F1"));
        view.setTextSize(14);
        view.setLineSpacing(dp(2), 1.08f);
        view.setPadding(0, dp(3), 0, dp(6));
        return view;
    }

    private TextView authLabel(String text) {
        TextView view = new TextView(this);
        view.setText(tierText(text));
        view.setTextColor(Color.parseColor("#A7E6E7"));
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.06f);
        view.setPadding(0, dp(8), 0, dp(5));
        return view;
    }

    private TextView authWaveMark() {
        return createWaveMark(
                72,
                Color.WHITE,
                Gravity.CENTER
        );
    }

    private TextView createWaveMark(
            float textSizeSp,
            int color,
            int gravity
    ) {
        TextView mark = new TextView(this);
        mark.setText(WAVE_MARK);
        mark.setTextColor(color);
        mark.setTextSize(textSizeSp);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(gravity);
        mark.setIncludeFontPadding(false);
        return mark;
    }

    private Button authPrimaryButton(String text) {
        Button button = new Button(this);
        button.setText(tierText(text));
        button.setAllCaps(false);
        button.setTextColor(NAVY);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(R.drawable.bg_auth_primary_button);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setElevation(dp(3));
        return button;
    }

    private Button authOutlineButton(String text) {
        Button button = new Button(this);
        button.setText(tierText(text));
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#6FF4EF"));
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(R.drawable.bg_auth_outline_button);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        return button;
    }

    private Button authTextButton(String text) {
        Button button = new Button(this);
        button.setText(tierText(text));
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#D9F5F4"));
        button.setTextSize(13);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setPadding(0, dp(2), 0, dp(8));
        return s;
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private EditText inputField(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        field.setTextSize(14);
        field.setSingleLine(true);
        field.setBackgroundResource(R.drawable.bg_input);
        configureKoreanTextInput(field, false);
        return field;
    }

    private void configureKoreanTextInput(EditText field, boolean multiLine) {
        int type = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        if (multiLine) type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
        field.setInputType(type);
        field.setTextLocale(Locale.KOREAN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            field.setImeHintLocales(new LocaleList(Locale.KOREAN, Locale.getDefault()));
        }
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | (multiLine ? EditorInfo.IME_ACTION_NONE : EditorInfo.IME_ACTION_DONE));
        field.setHorizontallyScrolling(!multiLine);
        field.setFocusableInTouchMode(true);
    }

    private void bindDraft(EditText field, Consumer<String> listener) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                listener.accept(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private View addConversationBubble(String speaker, String message, boolean user) {
        LinearLayout messageRow = new LinearLayout(this);
        messageRow.setOrientation(LinearLayout.VERTICAL);
        messageRow.setGravity(user ? Gravity.END : Gravity.START);
        messageRow.setPadding(user ? dp(44) : 0, dp(4), user ? 0 : dp(24), dp(4));

        TextView speakerView = new TextView(this);
        speakerView.setText(tierText(speaker));
        speakerView.setTextSize(11);
        speakerView.setTypeface(Typeface.DEFAULT_BOLD);
        speakerView.setGravity(Gravity.CENTER);
        speakerView.setIncludeFontPadding(false);
        speakerView.setPadding(dp(9), dp(4), dp(9), dp(4));

        GradientDrawable speakerBackground = new GradientDrawable();
        speakerBackground.setCornerRadius(dp(999));
        if (user) {
            speakerView.setTextColor(Color.parseColor("#075985"));
            speakerBackground.setColor(Color.parseColor("#CFFAFE"));
            speakerBackground.setStroke(dp(1), Color.parseColor("#67E8F9"));
        } else {
            speakerView.setTextColor(Color.parseColor("#46637A"));
            speakerBackground.setColor(Color.parseColor("#EEF5F7"));
            speakerBackground.setStroke(dp(1), Color.parseColor("#D3E3E7"));
        }
        speakerView.setBackground(speakerBackground);

        LinearLayout.LayoutParams speakerParams = new LinearLayout.LayoutParams(-2, -2);
        speakerParams.setMargins(0, 0, 0, dp(5));
        messageRow.addView(speakerView, speakerParams);

        TextView messageView = new TextView(this);
        messageView.setText(tierText(message));
        messageView.setTextSize(user ? 15 : 14);
        messageView.setTextColor(user ? Color.WHITE : TEXT);
        messageView.setLineSpacing(dp(2), 1.08f);
        messageView.setPadding(dp(16), dp(12), dp(16), dp(12));
        messageView.setTextIsSelectable(true);
        messageView.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels
                * (user ? 0.82f : 0.92f)));

        GradientDrawable messageBackground = new GradientDrawable();
        if (user) {
            messageBackground.setColor(Color.parseColor("#087F8C"));
            messageBackground.setStroke(dp(1), Color.parseColor("#0E7490"));
            messageBackground.setCornerRadii(new float[]{
                    dp(18), dp(18),
                    dp(18), dp(18),
                    dp(5), dp(5),
                    dp(18), dp(18)
            });
            messageView.setElevation(dp(2));
        } else {
            messageBackground.setColor(Color.WHITE);
            messageBackground.setStroke(dp(1), Color.parseColor("#D3E3E7"));
            messageBackground.setCornerRadii(new float[]{
                    dp(18), dp(18),
                    dp(18), dp(18),
                    dp(18), dp(18),
                    dp(5), dp(5)
            });
            messageView.setElevation(dp(1));
        }
        messageView.setBackground(messageBackground);

        messageRow.addView(messageView, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, dp(12));
        content.addView(messageRow, rowParams);
        return messageRow;
    }

    private void scrollToConversationAnchor() {
        final View anchor = conversationScrollAnchor;
        final ScrollView scroll = contentScroll;
        if (anchor == null || scroll == null) return;
        scroll.postDelayed(() -> scroll.smoothScrollTo(0, Math.max(0, anchor.getTop() - dp(72))), 120L);
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(NAVY);
        v.setTextSize(20);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(12), 0, dp(8));
        return v;
    }

    private TextView title(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(NAVY);
        v.setTextSize(29);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(14), 0, dp(12));
        return v;
    }

    private TextView huge(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(NAVY);
        v.setTextSize(34);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(4), 0, dp(6));
        return v;
    }

    private TextView big(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(TEXT);
        v.setTextSize(17);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(2), 0, dp(6));
        return v;
    }

    private TextView body(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(TEXT);
        v.setTextSize(14);
        v.setLineSpacing(dp(2), 1.05f);
        v.setPadding(0, dp(4), 0, dp(6));
        return v;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(MUTED);
        v.setTextSize(12);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(8), 0, dp(4));
        return v;
    }

    private TextView note(String text, int color) {
        TextView v = body(text);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private CharSequence tierText(String text) {
        return TierTextFormatter.format(this, text);
    }

    private TierShieldView tierShield(String tier) {
        TierShieldView shield = new TierShieldView(this);
        shield.setTier(tier);
        return shield;
    }

    private LinearLayout tierSummaryRow(
            String tier,
            String titleText,
            String detailText,
            int shieldWidth,
            int shieldHeight
    ) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TierShieldView shield = tierShield(tier);
        row.addView(shield, new LinearLayout.LayoutParams(shieldWidth, shieldHeight));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(big(titleText));
        if (detailText != null && !detailText.trim().isEmpty()) {
            copy.addView(body(detailText));
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void addTierScoreRow(LinearLayout parent, String tier, String scoreText) {
        LinearLayout scoreRow = row();
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        TierShieldView shield = tierShield(tier);
        scoreRow.addView(shield, new LinearLayout.LayoutParams(dp(38), dp(44)));
        TextView score = body(plainTierText(tier) + " · " + scoreText);
        score.setTypeface(Typeface.DEFAULT_BOLD);
        score.setPadding(dp(8), 0, 0, 0);
        scoreRow.addView(score, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(scoreRow);
    }

    private String plainTierText(String tier) {
        String value = PromotionRules.displayName(tier);
        if (value == null || value.trim().isEmpty()) value = tier;
        return plainTierCopy(value);
    }

    private String plainTierCopy(String text) {
        if (text == null || text.isEmpty()) return "";
        text = PromotionRules.stripShieldMarkers(text);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            boolean emojiDecoration = codePoint == 0xFE0F
                    || codePoint == 0x20E3
                    || (codePoint >= 0x1F000 && codePoint <= 0x1FAFF);
            if (!emojiDecoration) result.appendCodePoint(codePoint);
        }
        return result.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private LinearLayout statCard(String value, String labelText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundResource(R.drawable.bg_card);
        box.setPadding(dp(6), dp(10), dp(6), dp(10));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.setMargins(dp(3), 0, dp(3), dp(12));
        box.setLayoutParams(boxParams);
        TextView valueView = big(value);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = label(labelText);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, 0, 0, 0);
        box.addView(valueView);
        box.addView(labelView);
        return box;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15), dp(13), dp(15), dp(13));
        l.setBackgroundResource(R.drawable.bg_card);
        l.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(tierText(text));
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundResource(R.drawable.bg_primary_button);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private Button outlineButton(String text) {
        Button b = new Button(this);
        b.setText(tierText(text));
        b.setAllCaps(false);
        b.setTextColor(NAVY);
        b.setTextSize(13);
        b.setBackgroundResource(R.drawable.bg_secondary_button);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private void styleActivityYearButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : NAVY);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackgroundResource(selected ? R.drawable.bg_primary_button : R.drawable.bg_secondary_button);
    }


    private void maybeRefreshDashboard() {
        if (dashboardRefreshing || !cloudRepository.isCloudConfigured()) return;
        if (System.currentTimeMillis() - dashboardRefreshedAt < 60_000L) return;

        // Record the attempt before the request starts. If the server is unavailable or
        // an older account cannot load the new dashboard endpoint, renderHome() must not
        // immediately start the same request again and continuously replace the view tree.
        dashboardRefreshing = true;
        dashboardRefreshedAt = System.currentTimeMillis();
        executor.execute(() -> {
            boolean refreshed = false;
            try {
                cloudRepository.refreshDashboard();
                refreshed = true;
            } catch (Exception ignored) {
                // Keep the locally cached dashboard usable. A later visit can retry after
                // the cooldown without blocking scrolling or button input.
            }
            final boolean shouldRender = refreshed;
            runOnUiThread(() -> {
                dashboardRefreshing = false;
                if (shouldRender && currentTab == 0) showApp(0);
            });
        });
    }

    private void addAiSearchBox(String resourceType, String hint, List<AiSearchTurn> conversation) {
        boolean loading = isSearchLoading(conversation, resourceType);
        LinearLayout searchCard = card();
        searchCard.addView(label("LLM 기반 검색"));
        EditText input = inputField(hint, searchDraft(resourceType));
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(6);
        configureKoreanTextInput(input, true);
        bindDraft(input, value -> setSearchDraft(resourceType, value));
        searchCard.addView(input);
        Button search = primaryButton(loading ? "검색 중…" : conversationHasType(conversation, resourceType) ? "후속 질문으로 다시 찾기" : "AI로 자료 찾기");
        search.setEnabled(!loading);
        search.setOnClickListener(v -> requestAiSearch(resourceType, input.getText().toString()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(8), 0, 0);
        searchCard.addView(search, params);
        if (loading) searchCard.addView(new ProgressBar(this));
        content.addView(searchCard);
    }

    private void renderAiSearchConversation(String resourceType, List<AiSearchTurn> conversation) {
        List<AiSearchTurn> visible = new ArrayList<>();
        for (AiSearchTurn turn : conversation) if (resourceType.equals(turn.resourceType)) visible.add(turn);
        if (visible.isEmpty()) return;

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(sectionTitle("AI 검색 대화"), new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = outlineButton("기록 지우기");
        clear.setOnClickListener(v -> {
            conversation.removeIf(turn -> resourceType.equals(turn.resourceType));
            setSearchDraft(resourceType, "");
            showApp("schedule".equals(resourceType) ? 3 : 1);
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(112), dp(40)));
        content.addView(header);

        for (AiSearchTurn turn : visible) {
            addConversationBubble("나", turn.query, true);
            if (turn.loading) {
                LinearLayout pending = card();
                pending.addView(label("BluePath AI"));
                pending.addView(big("자료와 근거를 찾고 있습니다"));
                pending.addView(new ProgressBar(this));
                content.addView(pending);
                conversationScrollAnchor = pending;
                continue;
            }
            if (!turn.error.isEmpty()) {
                View error = addConversationBubble("BluePath AI", "검색 실패: " + turn.error, false);
                conversationScrollAnchor = error;
                continue;
            }
            ApiModels.AiSearchResponse response = turn.response;
            if (response == null) continue;
            LinearLayout answer = card();
            answer.addView(label("BluePath AI"));
            answer.addView(body(safeOr(response.summary, "검색 결과를 정리했습니다.")));
            answer.addView(note(response.usedLiveWeb
                    ? "앱 자료와 실시간 웹 근거를 함께 검토했습니다."
                    : "앱에 등록된 자료를 기준으로 검색했습니다.", OCEAN));
            content.addView(answer);
            conversationScrollAnchor = answer;

            int shown = 0;
            if (response.items != null) {
                for (ApiModels.ContentDto dto : response.items) {
                    if ("video".equals(resourceType) && "video".equals(dto.contentType)) {
                        addContentCard(contentFromDto(dto), false);
                        shown++;
                    } else if ("paper".equals(resourceType) && "paper".equals(dto.contentType)) {
                        addPaperCard(paperFromDto(dto));
                        shown++;
                    } else if ("schedule".equals(resourceType)) {
                        if ("event".equals(dto.contentType)) addEventCard(eventFromDto(dto));
                        else addProgramCard(programFromDto(dto));
                        shown++;
                    }
                    if (shown >= 8) break;
                }
            }
            if (shown == 0) content.addView(note("조건에 맞는 등록 자료를 찾지 못했습니다. 조건을 바꿔 후속 질문을 해보세요.", MUTED));
        }
        if ("schedule".equals(resourceType)) {
            content.addView(note("AI 검색 대화는 위 영역에만 표시되며, 아래 일정 둘러보기에는 영향을 주지 않습니다.", MUTED));
        }
    }

    private void requestAiSearch(String resourceType, String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            toast("찾고 싶은 자료를 입력해 주세요.");
            return;
        }
        List<AiSearchTurn> conversation = searchConversation(resourceType);
        if (isSearchLoading(conversation, resourceType)) {
            toast("이전 검색을 기다려 주세요.");
            return;
        }
        List<ApiModels.ChatMessage> history = searchHistoryMessages(conversation, resourceType);
        AiSearchTurn turn = new AiSearchTurn(resourceType, value);
        conversation.add(turn);
        if (conversation.size() > 30) conversation.remove(0);
        setSearchDraft(resourceType, "");
        int tab = "schedule".equals(resourceType) ? 3 : 1;
        showApp(tab);
        scrollToConversationAnchor();
        executor.execute(() -> {
            try {
                turn.response = cloudRepository.aiSearch(value, resourceType, history);
            } catch (Exception e) {
                turn.error = safeMessage(e);
            }
            turn.loading = false;
            runOnUiThread(() -> {
                if (currentTab == tab) {
                    showApp(tab);
                    scrollToConversationAnchor();
                }
            });
        });
    }

    private List<AiSearchTurn> searchConversation(String resourceType) {
        return "schedule".equals(resourceType) ? scheduleSearchConversation : learningSearchConversation;
    }

    private boolean conversationHasType(List<AiSearchTurn> conversation, String resourceType) {
        for (AiSearchTurn turn : conversation) if (resourceType.equals(turn.resourceType)) return true;
        return false;
    }

    private boolean isSearchLoading(List<AiSearchTurn> conversation, String resourceType) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            AiSearchTurn turn = conversation.get(i);
            if (resourceType.equals(turn.resourceType)) return turn.loading;
        }
        return false;
    }

    private List<ApiModels.ChatMessage> searchHistoryMessages(List<AiSearchTurn> conversation, String resourceType) {
        List<ApiModels.ChatMessage> history = new ArrayList<>();
        List<AiSearchTurn> sameType = new ArrayList<>();
        for (AiSearchTurn turn : conversation) if (resourceType.equals(turn.resourceType) && !turn.loading) sameType.add(turn);
        int start = Math.max(0, sameType.size() - 6);
        for (int i = start; i < sameType.size(); i++) {
            AiSearchTurn turn = sameType.get(i);
            history.add(new ApiModels.ChatMessage("user", turn.query));
            if (turn.response != null && turn.response.summary != null && !turn.response.summary.trim().isEmpty()) {
                history.add(new ApiModels.ChatMessage("assistant", turn.response.summary.trim()));
            } else if (!turn.error.isEmpty()) {
                history.add(new ApiModels.ChatMessage("assistant", "검색 실패: " + turn.error));
            }
        }
        return history;
    }

    private String searchDraft(String resourceType) {
        return "schedule".equals(resourceType) ? scheduleSearchDraft : learningSearchDraft;
    }

    private void setSearchDraft(String resourceType, String value) {
        if ("schedule".equals(resourceType)) scheduleSearchDraft = value == null ? "" : value;
        else learningSearchDraft = value == null ? "" : value;
    }

    private ContentItem contentFromDto(ApiModels.ContentDto dto) {
        return new ContentItem(safe(dto.id), safe(dto.title), safe(dto.source), safe(dto.url),
                safeOr(dto.difficulty, "중"), safeOr(dto.requiredTier, "브론즈"),
                safeOr(dto.topic, "해양교육"), safe(dto.careerTag), dto.minutes);
    }

    private ProgramItem programFromDto(ApiModels.ContentDto dto) {
        return new ProgramItem(safe(dto.id), safe(dto.title), safeOr(dto.target, "전체"),
                safe(dto.startAt), safe(dto.endAt), safeOr(dto.method, "오프라인"),
                safeOr(dto.topic, "해양교육"), safe(dto.description), safe(dto.source),
                safeOr(dto.applicationUrl, safe(dto.url)), safe(dto.applicationDeadline), dto.capacity,
                dto.waitlistAvailable, safeOr(dto.timezone, "Asia/Seoul"));
    }

    private EventItem eventFromDto(ApiModels.ContentDto dto) {
        return new EventItem(safe(dto.id), safe(dto.title), safe(dto.startAt), safe(dto.endAt),
                safeOr(dto.target, "전체"), safeOr(dto.category, "행사"), safe(dto.description), safe(dto.source),
                safeOr(dto.applicationUrl, safe(dto.url)), safe(dto.applicationDeadline), dto.capacity,
                dto.waitlistAvailable, safeOr(dto.timezone, "Asia/Seoul"));
    }

    private PaperItem paperFromDto(ApiModels.ContentDto dto) {
        return new PaperItem(safe(dto.id), safe(dto.title), safe(dto.authors), safe(dto.year),
                safe(dto.source), safe(dto.url), safeOr(dto.topic, "해양교육"), safe(dto.description), safe(dto.doi),
                safeOr(dto.paperStatus, "current"), safe(dto.versionNote));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeOr(String value, String fallback) {
        String result = safe(value);
        return result.isEmpty() ? fallback : result;
    }

    private View profileAvatar(String nickname, String imageUrl, int size) {
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(ApiClient.resolveMediaUrl(imageUrl)).circleCrop().into(image);
            return image;
        }
        TextView fallback = new TextView(this);
        String name = nickname == null || nickname.trim().isEmpty() ? "B" : nickname.trim();
        String[] icons = {"🌊", "🐳", "⚓", "🐬", "⛵", "🪸", "🐚", "🧭"};
        fallback.setText(icons[(name.hashCode() & 0x7fffffff) % icons.length]);
        fallback.setTextSize(24);
        fallback.setGravity(Gravity.CENTER);
        int[] colors = {Color.parseColor("#D9F4FF"), Color.parseColor("#DFFBFA"), Color.parseColor("#E5E7FF"), Color.parseColor("#E0F2FE"), Color.parseColor("#DCFCE7")};
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(colors[(name.hashCode() & 0x7fffffff) % colors.length]);
        fallback.setBackground(bg);
        return fallback;
    }

    private String readableDate(String value) {
        if (value == null || value.trim().isEmpty()) return "방금 전";
        String result = value.replace('T', ' ');
        return result.length() > 16 ? result.substring(0, 16) : result;
    }

    private void showPromotionCelebration(String newTier) {
        if (appRoot == null) return;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#AA06223F"));
        overlay.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(26), dp(28), dp(24));
        panel.setBackgroundResource(R.drawable.bg_card);
        panel.setElevation(dp(18));

        TierShieldView shield = tierShield(newTier);
        LinearLayout.LayoutParams shieldParams = new LinearLayout.LayoutParams(dp(128), dp(146));
        shieldParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(shield, shieldParams);

        TextView title = huge("새 티어 달성");
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView tierName = big(plainTierText(newTier));
        tierName.setGravity(Gravity.CENTER);
        panel.addView(tierName);
        TextView message = body("학습과 퀴즈 성과가 반영되어 새로운 티어로 승급했습니다.");
        message.setGravity(Gravity.CENTER);
        panel.addView(message);

        Button close = primaryButton("계속하기");
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(48));
        closeParams.setMargins(0, dp(10), 0, 0);
        panel.addView(close, closeParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(dp(340), getResources().getDisplayMetrics().widthPixels - dp(32)),
                -2,
                Gravity.CENTER
        );
        overlay.addView(panel, panelParams);
        appRoot.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        View.OnClickListener dismiss = v -> {
            if (overlay.getParent() == appRoot) appRoot.removeView(overlay);
        };
        close.setOnClickListener(dismiss);
        overlay.setOnClickListener(v -> {
            if (v == overlay) dismiss.onClick(v);
        });

        panel.setScaleX(0.82f);
        panel.setScaleY(0.82f);
        panel.setAlpha(0f);
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).start();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("링크를 열 수 없습니다: " + url);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, tierText(message), Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String join(String[] values, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }
}
