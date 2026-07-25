package com.bluepath.app;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import com.bluepath.app.view.QuizTimerRingView;
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
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String WAVE_MARK = "∿";
    private static final long QUIZ_TIME_PER_QUESTION_MS = 30_000L;
    private static final String QUIZ_SOURCE_SERVER = "BluePath 검증 출제";
    private static final String RICH_BODY_MARKER = CommunityPostActivity.RICH_BODY_MARKER;

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
    private final ExecutorService communityExecutor = Executors.newFixedThreadPool(2);
    private LinearLayout root;
    private LinearLayout content;
    private ScrollView contentScroll;
    private FrameLayout appRoot;
    // 퀴즈 응시 중 헤더의 일정·알림 버튼을 잠그기 위해 참조를 보관합니다.
    private TextView headerCalendarButton;
    private TextView headerBellButton;
    // 학습 헤더의 ❤ 찜 개수 표시. 찜 토글 시 즉시 갱신합니다.
    private TextView headerWishButton;
    private int currentTab = 0;
    // 일정 화면은 하단 내비게이션에 없으므로, 진입 직전 탭을 기억해 뒤로 가기에 사용합니다.
    private int scheduleReturnTab = 0;
    private String learningSubTab = "video";
    private String communityCategory = "all";
    private String communityTag = "";
    private String communitySort = "latest";
    // "all"은 전체 글, "following"은 팔로우한 사용자의 글만 보여 줍니다.
    private String communityScope = "all";
    private ApiModels.CommunityFeedMetaDto communityMeta;
    private ApiModels.CommunityPostDto communityDetailPost;
    private final Set<String> communityExpandedPosts = new HashSet<>();
    // 커뮤니티 위에 겹쳐 뜨는 화면들입니다. 프로필 → 팔로워 목록 순으로 두 단계까지 쌓입니다.
    private String communityProfileUserId = "";
    private ApiModels.CommunityUserProfileDto communityProfile;
    private List<ApiModels.CommunityPostDto> communityProfilePosts = new ArrayList<>();
    private boolean communityProfileLoading = false;
    private String communityProfileError = "";
    private String communityFollowListUserId = "";
    private String communityFollowListMode = "followers";
    private String communityDetailReturnProfileUserId = "";
    private List<ApiModels.ProfileSummary> communityFollowList = new ArrayList<>();
    private boolean communityFollowListLoading = false;
    private String communityFollowListError = "";
    // MY 탭에서 팔로워 목록을 열 수도 있어, 오버레이를 띄운 탭을 기억해 두고 그 탭에서만 렌더링합니다.
    private int communityOverlayTab = 5;
    // 화면에 떠 있는 팔로우 버튼들을 사용자별로 모아 두고, 상태가 바뀌면 전체 리렌더 없이 그것만 갱신합니다.
    private final Map<String, List<Runnable>> followViewRefreshers = new HashMap<>();
    private final Set<String> followRequestsInFlight = new HashSet<>();
    private static final String[] COMMUNITY_TAGS_FREE = {"후기", "정보공유", "진로고민", "자격증·시험", "모임·번개"};
    private static final String[] COMMUNITY_TAGS_QUESTION = {"진로고민", "자격증·시험", "학습자료", "입시", "현직에게"};
    private int selectedActivityYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
    private boolean dashboardRefreshing = false;
    private long dashboardRefreshedAt = 0L;
    private boolean communityLoading = false;
    private boolean communityInitialized = false;
    private String communityError = "";
    private String communityQuery = "";
    private int communityOffset = 0;
    private boolean communityHasMore = true;
    private int communityRequestVersion = 0;
    private static final int COMMUNITY_PAGE_SIZE = 20;
    private static final long COMMUNITY_SEARCH_DEBOUNCE_MS = 150L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable communitySearchRunnable;
    private EditText communitySearchInput;
    private Button communitySearchClearButton;
    private LinearLayout communityResultsContainer;
    private List<ApiModels.CommunityPostDto> communityPosts = new ArrayList<>();
    private boolean learningSearchLoading = false;
    private boolean scheduleSearchLoading = false;
    private ApiModels.AiSearchResponse learningSearchResponse;
    private ApiModels.AiSearchResponse scheduleSearchResponse;
    private final Set<String> scheduleSelectedTags = new LinkedHashSet<>();
    private String scheduleStatusFilter = "전체";
    private String scheduleSelectedDate = "";
    private int scheduleCalendarYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
    private int scheduleCalendarMonth = Calendar.getInstance(Locale.KOREA).get(Calendar.MONTH);
    private boolean scheduleOnlineExpanded = false;

    private List<QuizQuestion> activeQuiz = new ArrayList<>();
    private int[] selectedAnswers = new int[0];
    private boolean[] quizMarked = new boolean[0];
    private int quizCurrentIndex = 0;
    private boolean quizGenerating = false;
    private boolean quizSubmitted = false;
    private String quizAttemptTier = "";
    private String quizSource = "";
    private String quizNotice = "";
    private int quizCorrect = 0;
    private int quizAwardedXp = 0;
    private long quizDeadlineElapsedRealtime = 0L;
    private long quizSessionTotalMs = 0L;
    private CountDownTimer quizCountDownTimer;
    private QuizTimerRingView quizTimerRing;
    private boolean quizTimedOut = false;
    private boolean quizServerAuthoritative = false;
    private boolean quizSubmitting = false;
    /**
     * 마지막 문항까지 진행을 마친 뒤 채점 단계로 넘어갔음을 뜻합니다. 채점 요청이 실패해도
     * 이 값이 유지되므로 마지막 문항 화면으로 되돌아가지 않고 재시도 화면을 보여줍니다.
     */
    private boolean quizAwaitingResult = false;

    private boolean agentLoading = false;
    private final List<String[]> agentChat = new ArrayList<>();
    private boolean profileEditOpen = false;
    private boolean skillMapOpen = false;

    private boolean routeLoading = false;
    private boolean routeAttempted = false;
    private boolean routeRerouting = false;
    private boolean routeDetailsExpanded = false;
    private boolean expandRouteDetailsAfterLoad = false;
    private String routeError = "";
    private ApiModels.RoutePlanResponse currentRoute;
    private boolean missionLoading = false;
    private String missionError = "";
    private ApiModels.FamilyMissionResponse currentMission;
    private ApiModels.MissionQrPayload currentQrPayload;
    private int missionParticipantCount = 2;
    private boolean guardianDialogVisible = false;
    private boolean attendanceRequestInFlight = false;

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
                post(MainActivity.this::requestCommunityScreenRefresh);
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

        if (currentTab == 0) {
            requestDailyAttendance();
            return;
        }

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
        if (communitySearchRunnable != null) {
            mainHandler.removeCallbacks(communitySearchRunnable);
            communitySearchRunnable = null;
        }
        communityExecutor.shutdownNow();
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
        // 일정 탭의 필터·달력·찜 등은 화면을 다시 렌더링하므로, 같은 탭 안에서
        // 갱신될 때는 기존 스크롤 위치를 보존합니다. 다른 탭에서 처음 진입할 때는
        // 평소처럼 맨 위에서 시작합니다.
        boolean restoreScheduleScroll = tab == 3 && currentTab == 3 && contentScroll != null;
        int scheduleScrollY = restoreScheduleScroll ? contentScroll.getScrollY() : 0;
        cancelQuizTimer();
        quizTimerRing = null;
        if (tab == 3 && currentTab != 3) scheduleReturnTab = currentTab;
        currentTab = tab;
        applyAppWindow();
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundResource(R.drawable.bg_app_surface);
        setContentView(appRoot);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundResource(R.drawable.bg_app_surface);
        appRoot.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // 모든 탭 공통: 투명 배경 + 검은 글씨의 컴팩트 헤더.
        // 탭 인트로 카드 대신 제목 옆 ? 버튼으로 사용 방법 안내를 제공합니다.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(12), dp(14), dp(12), dp(12));

        LinearLayout headerRow = row();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        if (tab == 3) {
            // 일정은 하단 내비게이션에 없는 화면이라 헤더에서 이전 탭으로 돌아갑니다.
            Button back = outlineButton("‹");
            back.setTextSize(20);
            back.setContentDescription("이전 화면으로");
            back.setOnClickListener(v -> showApp(scheduleReturnTab));
            headerRow.addView(back, new LinearLayout.LayoutParams(dp(48), dp(42)));
        }

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(tab == 3 ? dp(12) : dp(2), 0, 0, 0);

        LinearLayout brandTitle = row();
        brandTitle.setGravity(
                Gravity.START | Gravity.CENTER_VERTICAL
        );

        TextView headerWave = createWaveMark(
                30,
                Color.BLACK,
                Gravity.CENTER
        );
        brandTitle.addView(
                headerWave,
                new LinearLayout.LayoutParams(dp(36), dp(34))
        );

        TextView h = new TextView(this);
        h.setText(tabTitle(tab));
        h.setTextColor(Color.BLACK);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(dp(4), 0, 0, 0);
        brandTitle.addView(
                h,
                new LinearLayout.LayoutParams(-2, -2)
        );
        if (!tabGuideText(tab).isEmpty()) {
            TextView info = new TextView(this);
            info.setText("?");
            info.setTextSize(9);
            info.setTypeface(Typeface.DEFAULT_BOLD);
            info.setTextColor(MUTED);
            info.setGravity(Gravity.CENTER);
            GradientDrawable infoBg = new GradientDrawable();
            infoBg.setShape(GradientDrawable.OVAL);
            infoBg.setColor(Color.TRANSPARENT);
            infoBg.setStroke(dp(1), MUTED);
            info.setBackground(infoBg);
            info.setContentDescription(tabTitle(tab) + " 사용 방법 안내");
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            infoParams.setMargins(dp(8), 0, 0, 0);
            info.setOnClickListener(v -> showTabGuideDialog(tab));
            brandTitle.addView(info, infoParams);
        }
        brand.addView(brandTitle);
        headerRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        // 학습 화면에서는 찜 개수 하트 버튼을 표시하고, 누르면 찜 목록을 보여줍니다.
        headerWishButton = null;
        if (tab == 1 || tab == 2) {
            int wishCount = store.getBookmarks().size();
            TextView wish = homeText("❤ " + wishCount, 13, Color.parseColor("#E0475B"), true);
            wish.setGravity(Gravity.CENTER);
            wish.setPadding(dp(12), 0, dp(12), 0);
            wish.setClickable(true);
            wish.setFocusable(true);
            wish.setContentDescription("찜 목록 열기 · " + wishCount + "개");
            GradientDrawable wishBg = new GradientDrawable();
            wishBg.setColor(Color.WHITE);
            wishBg.setCornerRadius(dp(18));
            wishBg.setStroke(dp(1), Color.parseColor("#F3C3CB"));
            wish.setBackground(wishBg);
            wish.setOnClickListener(v -> showBookmarkListDialog());
            LinearLayout.LayoutParams wishParams = new LinearLayout.LayoutParams(-2, dp(36));
            wishParams.setMargins(0, 0, dp(6), 0);
            headerRow.addView(wish, wishParams);
            headerWishButton = wish;
        }
        headerCalendarButton = null;
        if (tab != 3) {
            headerCalendarButton = homeIconButton("📅", "일정 열기", v -> showApp(3));
            headerRow.addView(headerCalendarButton, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        LinearLayout.LayoutParams bellParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        bellParams.setMargins(dp(6), 0, 0, 0);
        headerBellButton = homeIconButton("🔔", "알림함 열기", v -> showNotificationInbox());
        headerRow.addView(headerBellButton, bellParams);
        header.addView(headerRow);
        if (tab != 0) main.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        // 커뮤니티에서는 우측 하단 글쓰기 버튼과 게시글이 겹치지 않도록 여백을 확보합니다.
        content.setPadding(dp(14), dp(12), dp(14), tab == 5 ? dp(82) : dp(22));

        ScrollView scroll = tab == 5
                ? new CommunityRefreshScrollView()
                : new ScrollView(this);
        contentScroll = scroll;
        scroll.setFillViewport(true);
        scroll.addView(content);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // AI 진로 상담 탭은 채팅 입력바를 화면 하단에 고정합니다. (키보드가 뜨면 함께 올라옴)
        if (tab == 4) main.addView(buildAgentComposer());

        // 퀴즈 응시 중에는 몰입을 위해 하단 내비게이션을 숨깁니다.
        if (!isQuizTakingActive()) main.addView(buildBottomNav(tab));

        // 커뮤니티 탭에서만 우측 하단 플로팅 글쓰기 버튼을 표시합니다. (상세·프로필·팔로우 목록에서는 숨김)
        if (tab == 5 && communityDetailPost == null && !isCommunityOverlayOpen(5)) addCommunityWriteFab();

        renderTab(tab);
        if (restoreScheduleScroll && contentScroll != null) {
            ScrollView scheduleScroll = contentScroll;
            scheduleScroll.post(() -> scheduleScroll.scrollTo(0, scheduleScrollY));
        }
        if (tab == 0) requestDailyAttendance();
    }

    private String tabTitle(int tab) {
        switch (tab) {
            case 1: return "학습";
            case 2: return "학습";
            case 3: return "일정";
            case 4: return "AI 진로 상담";
            case 5: return "해양 커뮤니티";
            case 6: return "MY";
            default: return "홈";
        }
    }

    /**
     * 하단 고정 내비게이션 바입니다. 홈·학습·AI 진로 상담·커뮤니티·MY 다섯 항목으로 구성되며,
     * 일정(3)은 헤더의 달력 아이콘 등 별도 경로로만 진입합니다. 학습(1)과 퀴즈(2)는 '학습' 하나로 묶입니다.
     */
    private LinearLayout buildBottomNav(int tab) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(14));
        bar.setPadding(dp(4), dp(7), dp(4), dp(9));

        bar.addView(bottomNavItem("⌂", "홈", tab == 0, v -> openBottomNavRoot(0)), bottomNavCell());
        bar.addView(bottomNavItem("▶", "학습", tab == 1 || tab == 2, v -> openBottomNavRoot(1)), bottomNavCell());
        bar.addView(bottomNavAiItem(tab == 4), bottomNavCell());
        bar.addView(bottomNavItem("≋", "커뮤니티", tab == 5, v -> openBottomNavRoot(5)), bottomNavCell());
        bar.addView(bottomNavItem("👤", "MY", tab == 6, v -> openBottomNavRoot(6)), bottomNavCell());
        return bar;
    }

    /**
     * 하단 메뉴를 누를 때는 현재 화면의 깊이나 필터를 유지하지 않고 각 탭의 첫 화면으로 돌아갑니다.
     * 같은 메뉴를 다시 눌러도 화면을 새로 구성하므로 스크롤 역시 맨 위에서 시작합니다.
     */
    private void openBottomNavRoot(int tab) {
        if (tab == 0) {
            routeDetailsExpanded = false;
            expandRouteDetailsAfterLoad = false;
        } else if (tab == 1) {
            learningSubTab = "video";
            learningSearchResponse = null;
        } else if (tab == 5) {
            resetCommunityToRoot();
        } else if (tab == 6) {
            profileEditOpen = false;
            skillMapOpen = false;
            resetCommunityOverlays();
        }
        showApp(tab);
    }

    private void resetCommunityToRoot() {
        cancelPendingCommunitySearch();
        communityRequestVersion++;
        communityCategory = "all";
        communityTag = "";
        communitySort = "latest";
        communityScope = "all";
        communityQuery = "";
        communityMeta = null;
        communityLoading = false;
        communityInitialized = false;
        communityError = "";
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        communityExpandedPosts.clear();
        resetCommunityOverlays();
    }

    private void resetCommunityOverlays() {
        communityDetailPost = null;
        communityDetailReturnProfileUserId = "";
        communityProfileUserId = "";
        communityProfile = null;
        communityProfilePosts = new ArrayList<>();
        communityProfileLoading = false;
        communityProfileError = "";
        communityFollowListUserId = "";
        communityFollowList = new ArrayList<>();
        communityFollowListLoading = false;
        communityFollowListError = "";
        communityOverlayTab = 5;
    }

    private LinearLayout.LayoutParams bottomNavCell() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private LinearLayout bottomNavItem(String icon, String labelText, boolean active, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(labelText);
        item.setOnClickListener(listener);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(19);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextColor(active ? OCEAN : MUTED);
        iconView.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        item.addView(iconView, new LinearLayout.LayoutParams(-2, -2));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(10);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(active ? NAVY : MUTED);
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        item.addView(label, new LinearLayout.LayoutParams(-2, -2));
        return item;
    }

    private LinearLayout bottomNavAiItem(boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription("AI 진로 상담");
        item.setOnClickListener(v -> showApp(4));

        TextView orb = new TextView(this);
        orb.setText("✦");
        orb.setTextSize(19);
        orb.setGravity(Gravity.CENTER);
        orb.setTextColor(Color.WHITE);
        orb.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable orbBg = new GradientDrawable();
        orbBg.setShape(GradientDrawable.OVAL);
        orbBg.setColor(active ? NAVY : OCEAN);
        orbBg.setStroke(dp(2), Color.parseColor("#9EF5F0"));
        orb.setBackground(orbBg);
        orb.setElevation(dp(6));
        item.addView(orb, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView label = new TextView(this);
        label.setText("AI 진로 상담");
        label.setTextSize(9);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(active ? NAVY : MUTED);
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        label.setPadding(0, dp(2), 0, 0);
        item.addView(label, new LinearLayout.LayoutParams(-2, -2));
        return item;
    }

    private void renderTab(int tab) {
        if (content == null) return;
        content.removeAllViews();
        followViewRefreshers.clear();
        switch (tab) {
            case 0: renderHome(); break;
            case 1: renderLearning(); break;
            case 2: renderQuiz(); break;
            case 3: renderSchedule(); break;
            case 4: renderCareer(); break;
            case 5: renderCommunity(); break;
            case 6: renderMyPage(); break;
        }
        applyHeaderQuizLock();
    }

    /**
     * 퀴즈 응시 화면에서는 상단 일정·알림 버튼을 숨겨 풀이 도중 다른 화면으로
     * 이동하지 못하게 합니다. 퀴즈 종료는 응시 화면의 ✕ 버튼으로만 진행합니다.
     */
    private void applyHeaderQuizLock() {
        int visibility = isQuizTakingActive() ? View.GONE : View.VISIBLE;
        if (headerCalendarButton != null) headerCalendarButton.setVisibility(visibility);
        if (headerBellButton != null) headerBellButton.setVisibility(visibility);
    }

    private boolean isQuizTakingActive() {
        return currentTab == 2 && !activeQuiz.isEmpty() && !quizSubmitted && !quizGenerating
                && !quizSubmitting && !quizAwaitingResult;
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
        maybeRefreshDashboard();
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        addHomeHeader(p);
        addHomeXpSection(p, tier);
        addHomeUrgentSection(p);
        addHomeContinueLearningSection();
        addVoyageTwinSection(p);
        addHomeSectionHeader("AI 추천 학습 자료", "전체 보기 ›", v -> showApp(1));
        content.addView(note("내 항로와 최근 학습 기록을 반영해 정렬했어요.", OCEAN));
        List<ContentItem> items = RecommendationEngine.recommendedContents(p, tier, store);
        for (int i = 0; i < Math.min(3, items.size()); i++) addContentCard(items.get(i), true);

        addHomeSectionHeader("추천 교육 일정", "일정 전체 ›", v -> showApp(3));
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

    private void addHomeHeader(UserProfile profile) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(10), dp(2), dp(12));

        View avatar = profileAvatar(store.getNickname(), store.getProfileImageUrl(), dp(48));
        if (avatar instanceof TextView) ((TextView) avatar).setTextSize(20);
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setContentDescription("MY 열기");
        avatar.setOnClickListener(v -> showApp(6));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        int hour = Calendar.getInstance(Locale.KOREA).get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "좋은 아침이에요" : hour < 18 ? "좋은 오후예요" : "좋은 저녁이에요";
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(8), 0, dp(4), 0);
        TextView title = homeText(greeting + ", " + store.getNickname() + "님", 16, NAVY, true);
        title.setMaxLines(2);
        copy.addView(title);
        copy.addView(homeText("오늘도 " + profile.interest + " 활동을 이어가 볼까요?", 11, MUTED, false));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        header.addView(homeIconButton("📅", "일정 열기", v -> showApp(3)),
                new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        guideParams.setMargins(dp(4), 0, 0, 0);
        header.addView(homeIconButton("?", "홈 사용 방법 안내", v -> showHomeGuideDialog()), guideParams);
        LinearLayout.LayoutParams reminderParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        reminderParams.setMargins(dp(4), 0, 0, 0);
        header.addView(homeIconButton("🔔", "알림함 열기", v -> showNotificationInbox()), reminderParams);
        content.addView(header);
    }

    private void addHomeXpSection(UserProfile profile, String tier) {
        int xp = profile.xp;
        int base = UserStore.tierBaseXp(tier);
        int next = UserStore.nextTierXp(tier);
        int progress = "다이아".equals(tier)
                ? 100
                : Math.min(100, Math.max(0, (xp - base) * 100 / Math.max(1, next - base)));

        LinearLayout panel = homePanel(Color.TRANSPARENT, Color.TRANSPARENT, 24);
        panel.setBackgroundResource(R.drawable.bg_ocean_header);
        panel.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView xpText = homeText("XP " + xp + ("다이아".equals(tier) ? " · 최고 티어" : " · " + plainTierText(tier) + "까지 " + Math.max(0, next - xp)),
                14, Color.WHITE, true);
        top.addView(xpText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView standard = homePill("승급 기준", Color.parseColor("#267B91"), Color.parseColor("#9EF5F0"));
        standard.setOnClickListener(v -> showPromotionManual());
        top.addView(standard);
        TextView percent = homeText(progress + "%", 15, CYAN, true);
        percent.setGravity(Gravity.END);
        percent.setPadding(dp(14), 0, 0, 0);
        top.addView(percent);
        panel.addView(top);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(progress);
        bar.setProgressTintList(ColorStateList.valueOf(CYAN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B6983")));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(7));
        barParams.setMargins(0, dp(7), 0, 0);
        panel.addView(bar, barParams);
        content.addView(panel);
    }

    private void addHomeUrgentSection(UserProfile profile) {
        addHomeSectionHeader("지금 놓치면 안 돼요", "", null);
        ProgramItem urgentProgram = null;
        for (ProgramItem item : RecommendationEngine.recommendedPrograms(profile, store)) {
            if (!RecommendationEngine.isArchived(item.startDate, item.endDate)) {
                urgentProgram = item;
                break;
            }
        }

        LinearLayout urgentRow = row();
        urgentRow.setGravity(Gravity.TOP);
        LinearLayout programCard = createUrgentProgramCard(urgentProgram);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, dp(218), 1);
        first.setMargins(0, 0, dp(6), 0);
        urgentRow.addView(programCard, first);

        LinearLayout missionCard = createUrgentMissionCard();
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(218), 1);
        second.setMargins(dp(6), 0, 0, 0);
        urgentRow.addView(missionCard, second);
        content.addView(urgentRow);
    }

    private LinearLayout createUrgentProgramCard(ProgramItem item) {
        LinearLayout card = homePanel(Color.TRANSPARENT, Color.TRANSPARENT, 22);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0B4C6B"), Color.parseColor("#14A6A6")});
        background.setCornerRadius(dp(22));
        card.setBackground(background);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        String status = item == null ? "새 일정" : RecommendationEngine.scheduleStatus(item.startDate, item.endDate);
        card.addView(homePill(status, Color.parseColor("#FFF2C7"), Color.parseColor("#8A5B00")));
        TextView title = homeText(item == null ? "모집 중인 일정을 확인해 보세요" : item.title, 14, Color.WHITE, true);
        title.setMaxLines(3);
        title.setPadding(0, dp(10), 0, dp(4));
        card.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));
        String meta = item == null ? "관심 분야에 맞는 새 일정을 찾아볼 수 있어요." : item.method + " · " + item.startDate;
        TextView metaView = homeText(meta, 11, Color.parseColor("#D9FFFF"), false);
        metaView.setMaxLines(2);
        card.addView(metaView);
        Button action = outlineButton(item == null ? "일정 보기 ›" : "바로 확인 ›");
        action.setOnClickListener(v -> {
            if (item != null && !item.applicationUrl.trim().isEmpty()) openUrl(item.applicationUrl);
            else showApp(3);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(44));
        actionParams.setMargins(0, dp(10), 0, 0);
        card.addView(action, actionParams);
        return card;
    }

    private LinearLayout createUrgentMissionCard() {
        LinearLayout card = homePanel(Color.WHITE, Color.parseColor("#17B8BA"), 22);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.addView(homePill("협동 미션", Color.parseColor("#DDF7F7"), OCEAN));
        TextView title = homeText(currentMission == null ? "가족과 함께하는 해양 미션" : currentMission.title,
                14, NAVY, true);
        title.setMaxLines(3);
        title.setPadding(0, dp(10), 0, dp(4));
        card.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView state = homeText(currentMission == null ? "현장 QR을 스캔하면 역할별 미션이 열려요." : "미션 진행 중 · 인증을 완료해 보세요.",
                11, MUTED, false);
        state.setMaxLines(2);
        card.addView(state);
        Button action = primaryButton(currentMission == null ? "QR 미션 시작 ›" : "완료 인증 ›");
        action.setOnClickListener(v -> {
            if (currentMission == null) launchMissionQrScanner();
            else showMissionVerificationDialog();
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(44));
        actionParams.setMargins(0, dp(10), 0, 0);
        card.addView(action, actionParams);
        return card;
    }

    private void addHomeContinueLearningSection() {
        ContentItem latest = null;
        long latestStartedAt = 0L;
        for (ContentItem item : DataRepository.contents()) {
            if (!store.isContentStarted(item.id) || store.getCompletedContentIds().contains(item.id)) continue;
            long startedAt = store.getContentStartedAt(item.id);
            if (startedAt > latestStartedAt) {
                latest = item;
                latestStartedAt = startedAt;
            }
        }
        if (latest == null) return;

        addHomeSectionHeader("이어서 학습", "전체 보기 ›", v -> showApp(1));
        ContentItem item = latest;
        LinearLayout card = homePanel(Color.WHITE, Color.parseColor("#CFE3EB"), 22);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        LinearLayout summary = row();
        summary.setGravity(Gravity.CENTER_VERTICAL);

        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this)
                .load(youtubeThumbnailUrl(item.url))
                .placeholder(R.drawable.bg_thumb)
                .error(R.drawable.bg_thumb)
                .fallback(R.drawable.bg_thumb)
                .transform(new CenterCrop(), new RoundedCorners(dp(10)))
                .into(thumbnail);
        summary.addView(thumbnail, new LinearLayout.LayoutParams(dp(96), dp(74)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        TextView title = homeText(item.title, 14, NAVY, true);
        title.setMaxLines(2);
        copy.addView(title);
        int watched = store.getVideoWatchSeconds(item.id);
        int total = Math.max(store.getVideoDurationSeconds(item.id), item.minutes * 60);
        int remaining = Math.max(0, total - watched);
        copy.addView(homeText(item.source + " · 남은 시간 " + Math.max(1, (remaining + 59) / 60) + "분", 11, MUTED, false));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(store.getVideoProgressPercent(item.id));
        progress.setProgressTintList(ColorStateList.valueOf(OCEAN));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(8));
        progressParams.setMargins(0, dp(8), 0, 0);
        copy.addView(progress, progressParams);
        summary.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(summary);

        Button continueButton = primaryButton("이어보기");
        continueButton.setOnClickListener(v -> openVerifiedContent(item));
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(-1, dp(46));
        continueParams.setMargins(0, dp(12), 0, 0);
        card.addView(continueButton, continueParams);
        content.addView(card);
    }

    private void openVerifiedContent(ContentItem item) {
        store.markContentStarted(item.id);
        viewModel.recordLearning("video", item.id, item.title, "started");
        Intent verified = new Intent(this, VerifiedVideoActivity.class);
        verified.putExtra(VerifiedVideoActivity.EXTRA_CONTENT_ID, item.id);
        verified.putExtra(VerifiedVideoActivity.EXTRA_TITLE, item.title);
        verified.putExtra(VerifiedVideoActivity.EXTRA_URL, item.url);
        verified.putExtra(VerifiedVideoActivity.EXTRA_MINUTES, item.minutes);
        startActivity(verified);
    }

    private void addHomeSectionHeader(String titleText, String actionText, View.OnClickListener listener) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(14), dp(2), dp(10));
        TextView title = homeText(titleText, 19, NAVY, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (actionText != null && !actionText.trim().isEmpty()) {
            TextView action = homeText(actionText, 11.5f, OCEAN, true);
            action.setGravity(Gravity.CENTER);
            action.setPadding(dp(10), dp(8), 0, dp(8));
            action.setClickable(true);
            action.setFocusable(true);
            action.setOnClickListener(listener);
            header.addView(action);
        }
        content.addView(header);
    }

    private TextView homeIconButton(String text, String description, View.OnClickListener listener) {
        TextView button = homeText(text, 14, OCEAN, true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.parseColor("#CFE3EB"));
        button.setBackground(background);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView homeText(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(tierText(text));
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setLineSpacing(dp(2), 1.05f);
        return view;
    }

    private TextView homePill(String text, int backgroundColor, int textColor) {
        TextView pill = homeText(text, 10.5f, textColor, true);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(16));
        pill.setBackground(background);
        return pill;
    }

    private LinearLayout homePanel(int backgroundColor, int strokeColor, int radiusDp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setElevation(dp(1));
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(radiusDp));
        if (Color.alpha(strokeColor) > 0) background.setStroke(dp(1), strokeColor);
        panel.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        panel.setLayoutParams(params);
        return panel;
    }

    private void showHomeGuideDialog() {
        ScrollView scroll = new ScrollView(this);
        TextView guide = body(
                "홈에서는 오늘 바로 시작할 활동과 현재 항로를 먼저 확인할 수 있습니다.\n\n"
                        + "상단 XP 진행도는 현재 티어에서 다음 티어까지 남은 정도를 보여줍니다. 승급 기준을 누르면 전체 기준을 확인할 수 있습니다.\n\n"
                        + "AI 스마트 항해도는 온라인 학습, 박물관 체험, 퀴즈, 프로젝트와 NCS 직무를 목표에 맞는 순서로 연결합니다. 항로 생성·갱신 후 각 단계의 시작 버튼을 눌러 활동할 수 있습니다.\n\n"
                        + "추천 학습 자료와 교육 일정은 관심 분야, 목표, 현재 티어, 학습 기록과 일정 상태를 반영해 정렬됩니다. 찜한 항목과 완료 기록은 MY에서 다시 확인할 수 있습니다."
        );
        guide.setPadding(dp(20), dp(8), dp(20), dp(18));
        scroll.addView(guide);
        new AlertDialog.Builder(this)
                .setTitle("홈 사용 방법 안내")
                .setView(scroll)
                .setPositiveButton("확인", null)
                .show();
    }

    private void showNotificationInbox() {
        new AlertDialog.Builder(this)
                .setTitle("알림")
                .setMessage("새 알림이 없습니다.")
                .setNegativeButton("알림 설정", (dialog, which) -> showReminderTimePicker())
                .setPositiveButton("확인", null)
                .show();
    }

    private void requestDailyAttendance() {
        if (!cloudRepository.isCloudConfigured() || !store.hasCloudSession()) return;
        String today = seoulDateKey();
        if (attendanceRequestInFlight || today.equals(store.getLastAttendanceCheckDate())) return;
        attendanceRequestInFlight = true;
        executor.execute(() -> {
            try {
                ApiModels.AttendanceResponse response = cloudRepository.checkInAttendance();
                runOnUiThread(() -> {
                    attendanceRequestInFlight = false;
                    store.setLastAttendanceCheckDate(today);
                    if (currentTab != 0 || appRoot == null) return;
                    showApp(0);
                    appRoot.post(() -> showAttendanceDialog(response));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    attendanceRequestInFlight = false;
                    toast("출석 확인을 불러오지 못했습니다: " + safeMessage(error));
                });
            }
        });
    }

    private String seoulDateKey() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return format.format(new Date());
    }

    private void showAttendanceDialog(ApiModels.AttendanceResponse response) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(20), dp(18), dp(8));

        TextView mark = createWaveMark(42, Color.WHITE, Gravity.CENTER);
        GradientDrawable markBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#075071"), Color.parseColor("#10A6A8")});
        markBackground.setShape(GradientDrawable.OVAL);
        mark.setBackground(markBackground);
        panel.addView(mark, new LinearLayout.LayoutParams(dp(88), dp(88)));

        TextView title = homeText(response.newlyCheckedIn ? "출석 완료!" : "오늘 출석 확인 완료", 28, NAVY, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(6));
        panel.addView(title);
        String reward = response.xpAwarded > 0 ? " · XP +" + response.xpAwarded : "";
        TextView streak = homeText(Math.max(1, response.streak) + "일 연속 항해 중" + reward, 17, OCEAN, true);
        streak.setGravity(Gravity.CENTER);
        panel.addView(streak);

        Set<String> attendedDates = new HashSet<>();
        if (response.attendedDates != null) attendedDates.addAll(response.attendedDates);
        String[] dayLabels = {"월", "화", "수", "목", "금", "토", "일"};
        Calendar day = Calendar.getInstance(Locale.KOREA);
        int offset = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        day.add(Calendar.DAY_OF_MONTH, -offset);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

        LinearLayout week = row();
        week.setGravity(Gravity.CENTER);
        week.setPadding(0, dp(18), 0, dp(8));
        for (int index = 0; index < dayLabels.length; index++) {
            boolean checked = attendedDates.contains(dateFormat.format(day.getTime()));
            LinearLayout dayColumn = new LinearLayout(this);
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            dayColumn.setGravity(Gravity.CENTER);
            TextView circle = homeText(checked ? "✓" : "·", 18, checked ? OCEAN : Color.parseColor("#B6C5D2"), true);
            circle.setGravity(Gravity.CENTER);
            GradientDrawable circleBackground = new GradientDrawable();
            circleBackground.setShape(GradientDrawable.OVAL);
            circleBackground.setColor(checked ? Color.parseColor("#DDF8F5") : Color.parseColor("#F0F4F7"));
            circle.setBackground(circleBackground);
            dayColumn.addView(circle, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView dayLabel = homeText(dayLabels[index], 11, MUTED, false);
            dayLabel.setGravity(Gravity.CENTER);
            dayLabel.setPadding(0, dp(4), 0, 0);
            dayColumn.addView(dayLabel);
            week.addView(dayColumn, new LinearLayout.LayoutParams(0, -2, 1));
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        panel.addView(week, new LinearLayout.LayoutParams(-1, -2));

        TextView bonus = homeText("7일 연속 출석 시 보너스 XP +50", 13, MUTED, true);
        bonus.setGravity(Gravity.CENTER);
        bonus.setPadding(0, dp(6), 0, dp(8));
        panel.addView(bonus);

        new AlertDialog.Builder(this)
                .setView(panel)
                .setCancelable(false)
                .setPositiveButton("오늘도 항해 시작", null)
                .show();
    }

    private void addActivityHistorySection() {
        LinearLayout heatCard = card();
        int currentActivityYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
        int[] summary = activitySummary();

        LinearLayout headRow = row();
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heatmapTitle = big("나의 활동");
        headRow.addView(heatmapTitle, new LinearLayout.LayoutParams(0, -2, 1));
        if (summary[3] > 0) {
            TextView streak = new TextView(this);
            streak.setText("연속 " + summary[3] + "일 항해 중");
            streak.setTextSize(11);
            streak.setTypeface(Typeface.DEFAULT_BOLD);
            streak.setTextColor(Color.parseColor("#C2571B"));
            streak.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable streakBg = new GradientDrawable();
            streakBg.setColor(Color.parseColor("#FDEBDD"));
            streakBg.setCornerRadius(dp(11));
            streak.setBackground(streakBg);
            headRow.addView(streak);
        }
        heatCard.addView(headRow);

        ActivityHeatmapView heatmap = new ActivityHeatmapView(this);
        heatmap.setYear(currentActivityYear);
        heatmap.setActivity(store.getActivityCounts());
        HorizontalScrollView heatmapScroll = new HorizontalScrollView(this);
        heatmapScroll.setHorizontalScrollBarEnabled(false);
        heatmapScroll.setFillViewport(false);
        heatmapScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        heatmapScroll.addView(heatmap, new FrameLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams heatmapParams = new LinearLayout.LayoutParams(-1, dp(188));
        heatmapParams.setMargins(0, dp(6), 0, 0);
        heatCard.addView(heatmapScroll, heatmapParams);
        // 최근 달이 보이도록 오른쪽 끝으로 스크롤 (좌로 밀면 이전 달 확인 가능)
        heatmapScroll.post(() -> heatmapScroll.fullScroll(View.FOCUS_RIGHT));

        int currentMonth = Calendar.getInstance(Locale.KOREA).get(Calendar.MONTH) + 1;
        heatCard.addView(label(currentMonth + "월 출석 " + summary[0] + "일 · 올해 활동 " + summary[1]
                + "회 · 최장 연속 " + summary[2] + "일"));
        content.addView(heatCard);
    }

    /** {이번 달 출석일, 올해 활동 횟수, 올해 최장 연속일, 현재 연속일} */
    private int[] activitySummary() {
        Map<String, Integer> counts = store.getActivityCounts();
        Calendar now = Calendar.getInstance(Locale.KOREA);
        String yearPrefix = String.format(Locale.US, "%04d-", now.get(Calendar.YEAR));
        String monthPrefix = String.format(Locale.US, "%04d-%02d-", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1);
        int monthDays = 0;
        int yearTotal = 0;
        Set<String> activeDays = new HashSet<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            activeDays.add(entry.getKey());
            if (entry.getKey().startsWith(yearPrefix)) yearTotal += entry.getValue();
            if (entry.getKey().startsWith(monthPrefix)) monthDays++;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        int maxStreak = 0;
        for (String day : activeDays) {
            if (!day.startsWith(yearPrefix)) continue;
            try {
                Calendar cursor = Calendar.getInstance(Locale.KOREA);
                cursor.setTime(format.parse(day));
                cursor.add(Calendar.DAY_OF_MONTH, -1);
                if (activeDays.contains(format.format(cursor.getTime()))) continue; // 연속 구간의 시작만 계산
                int streak = 1;
                cursor.add(Calendar.DAY_OF_MONTH, 2);
                while (activeDays.contains(format.format(cursor.getTime()))) {
                    streak++;
                    cursor.add(Calendar.DAY_OF_MONTH, 1);
                }
                maxStreak = Math.max(maxStreak, streak);
            } catch (Exception ignored) {
            }
        }
        int currentStreak = 0;
        Calendar cursor = Calendar.getInstance(Locale.KOREA);
        if (!activeDays.contains(format.format(cursor.getTime()))) cursor.add(Calendar.DAY_OF_MONTH, -1);
        while (activeDays.contains(format.format(cursor.getTime()))) {
            currentStreak++;
            cursor.add(Calendar.DAY_OF_MONTH, -1);
        }
        return new int[]{monthDays, yearTotal, maxStreak, currentStreak};
    }

    private void addVoyageTwinSection(UserProfile profile) {
        addHomeSectionHeader("AI 스마트 항해도", "", null);
        LinearLayout voyage = homePanel(Color.WHITE, Color.parseColor("#CFE3EB"), 22);
        voyage.setPadding(dp(16), dp(15), dp(16), dp(15));
        voyage.addView(label("AI SMART NAUTICAL CHART"));
        voyage.addView(big("목표 직무·현재 실력·활동 기록에 맞춰 AI가 나만의 항로를 새로 그립니다"));
        voyage.addView(body("온라인 학습 → 박물관 체험 → 퀴즈 → 프로젝트 → NCS 직무 순서로 단계가 생성되고, 활동할 때마다 자동 갱신됩니다."));
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
        LinearLayout selectors = row();
        selectors.setGravity(Gravity.TOP);
        LinearLayout careerColumn = new LinearLayout(this);
        careerColumn.setOrientation(LinearLayout.VERTICAL);
        careerColumn.addView(label("목표 항구"));
        careerColumn.addView(career, new LinearLayout.LayoutParams(-1, dp(52)));
        selectors.addView(careerColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout routeColumn = new LinearLayout(this);
        routeColumn.setOrientation(LinearLayout.VERTICAL);
        routeColumn.setPadding(dp(8), 0, 0, 0);
        routeColumn.addView(label("항해 방식"));
        routeColumn.addView(routeType, new LinearLayout.LayoutParams(-1, dp(52)));
        selectors.addView(routeColumn, new LinearLayout.LayoutParams(0, -2, 1));
        voyage.addView(selectors);
        Button generate = primaryButton(routeLoading ? "AI가 항로를 계산하는 중…" : "AI 항로 생성·갱신");
        generate.setEnabled(!routeLoading && !routeRerouting);
        generate.setOnClickListener(v -> {
            routeDetailsExpanded = false;
            expandRouteDetailsAfterLoad = true;
            requestRoute(career.getSelectedItem().toString(), routeTypeCode(routeType.getSelectedItem().toString()));
        });
        LinearLayout.LayoutParams generateParams = new LinearLayout.LayoutParams(-1, dp(52));
        generateParams.setMargins(0, dp(10), 0, dp(8));
        voyage.addView(generate, generateParams);

        HorizontalScrollView stageScroll = new HorizontalScrollView(this);
        stageScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout stages = row();
        stages.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        String[] stageLabels = {"① 온라인 학습", "② 박물관 체험", "③ 퀴즈", "④ 프로젝트", "⑤ NCS 직무"};
        for (String stageLabel : stageLabels) {
            TextView stage = homePill(stageLabel, Color.parseColor("#D7F5EF"), Color.parseColor("#087668"));
            LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-2, -2);
            stageParams.setMargins(0, 0, dp(7), 0);
            stages.addView(stage, stageParams);
        }
        stageScroll.addView(stages, new FrameLayout.LayoutParams(-2, -2));
        voyage.addView(stageScroll, new LinearLayout.LayoutParams(-1, dp(42)));

        if (routeLoading || routeRerouting) {
            ProgressBar progress = new ProgressBar(this);
            voyage.addView(progress, new LinearLayout.LayoutParams(-1, dp(42)));
            voyage.addView(body(routeRerouting
                    ? "마감·시간·난이도 제약을 반영해 대체 항로를 탐색하고 있습니다."
                    : "현재 숙련도와 이력, 교육 일정, NCS 역량을 매핑하고 있습니다."));
        }
        if (!routeError.isEmpty()) voyage.addView(note("항로 불러오기: " + routeError, DANGER));

        if (currentRoute != null) {
            LinearLayout routeDetails = addRouteDetailsExpandable(voyage);
            routeDetails.addView(big(currentRoute.targetCareer + " 항로"));
            routeDetails.addView(body(safe(currentRoute.summary)));
            LinearLayout readiness = row();
            readiness.addView(statCard(currentRoute.readinessBefore + "%", "현재 준비도"), new LinearLayout.LayoutParams(0, -2, 1));
            readiness.addView(statCard(currentRoute.readinessAfter + "%", "완료 예상"), new LinearLayout.LayoutParams(0, -2, 1));
            readiness.addView(statCard(currentRoute.estimatedMinutes + "분", "예상 소요"), new LinearLayout.LayoutParams(0, -2, 1));
            routeDetails.addView(readiness);
            routeDetails.addView(note("AI 코치 · " + safe(currentRoute.coachMessage), OCEAN));
            routeDetails.addView(label("현재 위치 · " + currentRoute.currentSkillTopic + " 숙련도 "
                    + currentRoute.currentMastery + " · " + plainTierText(currentRoute.tier)));

            LinearLayout routeMeta = row();
            routeMeta.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            int routeNodeCount = currentRoute.nodes == null ? 0 : currentRoute.nodes.size();
            routeMeta.addView(homePill(routeNodeCount + "단계 · 예상 " + currentRoute.estimatedDays + "일",
                    Color.parseColor("#E8FBFC"), OCEAN));
            routeDetails.addView(routeMeta);

            if (currentRoute.nodes != null) {
                for (int i = 0; i < currentRoute.nodes.size(); i++) {
                    if (i > 0) {
                        TextView connector = body("↓  다음 항로");
                        connector.setGravity(Gravity.CENTER);
                        connector.setTextColor(OCEAN);
                        routeDetails.addView(connector);
                    }
                    addVoyageNode(routeDetails, currentRoute.nodes.get(i));
                }
            }

            if (store.hasPendingReroute()) {
                routeDetails.addView(note("자동 재항해 준비 완료 · " + safeOr(store.getPendingRerouteSummary(),
                        "최근 활동을 반영한 짧은 대체 항로가 준비되었습니다."), SUCCESS));
                Button acceptPending = primaryButton(routeRerouting ? "대체 항로 적용 중…" : "준비된 대체 항로 적용");
                acceptPending.setEnabled(!routeLoading && !routeRerouting);
                acceptPending.setOnClickListener(v -> acceptPendingReroute());
                routeDetails.addView(acceptPending, new LinearLayout.LayoutParams(-1, dp(48)));
            } else if (store.daysSinceRouteActivity() >= 3) {
                routeDetails.addView(note("자동 재항해 확인 중 · 최근 " + store.daysSinceRouteActivity()
                        + "일 동안 활동이 없어 백그라운드에서 대체 항로를 준비합니다.", DANGER));
            }
            Button reroute = outlineButton(routeRerouting ? "재항해 중…" : "마감·시간 부족으로 수동 재항해");
            reroute.setEnabled(!routeLoading && !routeRerouting);
            reroute.setOnClickListener(v -> showRerouteDialog());
            routeDetails.addView(reroute);

            if (currentRoute.sources != null && !currentRoute.sources.isEmpty()) {
                LinearLayout sourcePanel = addExpandable(routeDetails,
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
            expandRouteDetailsAfterLoad = false;
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
                    if (expandRouteDetailsAfterLoad) routeDetailsExpanded = true;
                    expandRouteDetailsAfterLoad = false;
                    routeLoading = false;
                    routeError = "";
                    store.touchRouteActivity();
                    if (currentTab == 0) renderTab(0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    expandRouteDetailsAfterLoad = false;
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
        routeDetailsExpanded = false;
        expandRouteDetailsAfterLoad = false;
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

    /** 학습 탭 상단의 '학습 자료 | 승급 퀴즈' 전환 세그먼트입니다. */
    private void addLearningSegment(int activeTab) {
        LinearLayout segment = row();
        segment.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable segmentBg = new GradientDrawable();
        segmentBg.setColor(Color.parseColor("#E3EEF1"));
        segmentBg.setCornerRadius(dp(24));
        segment.setBackground(segmentBg);
        segment.setPadding(dp(4), dp(4), dp(4), dp(4));
        segment.addView(learningSegmentButton("학습 자료", activeTab == 1, 1),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        segment.addView(learningSegmentButton("승급 퀴즈", activeTab == 2, 2),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-1, -2);
        segmentParams.setMargins(0, 0, 0, dp(10));
        content.addView(segment, segmentParams);
    }

    private Button learningSegmentButton(String labelText, boolean selected, int targetTab) {
        Button button = new Button(this);
        button.setText(labelText);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setTextColor(selected ? NAVY : MUTED);
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(20));
            button.setBackground(bg);
            button.setElevation(dp(1));
        } else {
            button.setBackgroundColor(Color.TRANSPARENT);
        }
        button.setOnClickListener(v -> showApp(targetTab));
        return button;
    }

    private void renderLearning() {
        addLearningSegment(1);
        addAiSearchBox(learningSubTab, "AI에게 물어보세요 · \"20분 안에 볼 입문 영상\"", learningSearchLoading, learningSearchResponse);

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

        if (learningSearchResponse != null && learningSearchResponse.items != null && !learningSearchResponse.items.isEmpty()) {
            content.addView(sectionTitle("AI 검색 결과"));
            content.addView(body(learningSearchResponse.summary));
            int shown = 0;
            for (ApiModels.ContentDto dto : learningSearchResponse.items) {
                if ("paper".equals(learningSubTab) && "paper".equals(dto.contentType)) {
                    addPaperCard(paperFromDto(dto));
                    shown++;
                } else if ("video".equals(learningSubTab) && "video".equals(dto.contentType)) {
                    addContentCard(contentFromDto(dto), false);
                    shown++;
                }
            }
            if (shown == 0) content.addView(note("현재 하위 탭과 일치하는 검색 결과가 없습니다.", MUTED));
        }

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
        // 퀴즈 응시 중에는 몰입형 응시 화면만 표시합니다.
        if (isQuizTakingActive()) {
            renderQuizTaking();
            return;
        }
        addLearningSegment(2);
        String currentTier = store.getTier();
        content.addView(sectionTitle("승급 퀴즈"));
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
            loading.addView(big("문제를 생성하고 있습니다"));
            loading.addView(body(llmClient.isConfigured()
                    ? "AI가 영상 주제와 현재 프로필을 바탕으로 4지선다 문제를 구성합니다."
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
                    total + "문제 · 합격선 " + pass + "문제 · 전 문항 4지선다 · 제한 시간 " + quizTimeLimitText(total),
                    dp(68),
                    dp(80)
            ));
            startCard.addView(body(llmClient.isConfigured()
                    ? "BluePath AI가 공공·기관 자료를 검색해 근거 기반 문제를 생성합니다."
                    : "연결이 어려운 상황에서도 해양 특화 로컬 문제은행으로 학습할 수 있습니다."));
            Button generate = primaryButton(llmClient.isConfigured() ? "AI 퀴즈 생성" : "해양 퀴즈 시작");
            generate.setOnClickListener(v -> generateQuizForCurrentTier());
            startCard.addView(generate, new LinearLayout.LayoutParams(-1, dp(48)));
            content.addView(startCard);
            return;
        }

        boolean gradingFailed = quizAwaitingResult && !quizSubmitted && !quizSubmitting;

        LinearLayout session = card();
        session.addView(tierSummaryRow(
                quizAttemptTier,
                plainTierText(quizAttemptTier) + " 승급 세션",
                activeQuiz.size() + "문제 · 합격선 " + PromotionRules.passCount(quizAttemptTier)
                        + "문제 · 제한 시간 " + quizTimeLimitText(activeQuiz.size()) + " · 출제: " + quizSource,
                dp(68),
                dp(80)
        ));
        if (quizTimedOut) session.addView(note("제한 시간이 종료되어 그 시점의 답안이 자동으로 제출되었습니다.", DANGER));
        if (!quizNotice.isEmpty()) session.addView(note(quizNotice, gradingFailed ? DANGER : MUTED));
        content.addView(session);

        if (quizSubmitting) {
            LinearLayout loading = card();
            loading.addView(big("답안을 채점하고 있습니다"));
            loading.addView(body("답안과 승급 조건을 검증하고 있습니다. 잠시만 기다려 주세요."));
            ProgressBar progress = new ProgressBar(this);
            loading.addView(progress);
            content.addView(loading);
            return;
        }

        if (gradingFailed) {
            LinearLayout failed = card();
            failed.addView(big("채점을 완료하지 못했습니다"));
            failed.addView(body("마지막 문항까지 모두 진행했지만 채점 결과를 확정하지 못했습니다. "
                    + "답안은 그대로 보관되어 있으니 다시 채점을 요청해 주세요."));
            int answered = 0;
            for (int value : selectedAnswers) if (value >= 0) answered++;
            failed.addView(body("응답 " + answered + "문제 · 미응답 " + (activeQuiz.size() - answered) + "문제"));

            Button retrySubmit = primaryButton("다시 채점 요청");
            retrySubmit.setOnClickListener(v -> submitQuiz(quizTimedOut));
            failed.addView(retrySubmit, new LinearLayout.LayoutParams(-1, dp(50)));

            Button abandon = outlineButton("채점을 포기하고 퀴즈 종료");
            abandon.setOnClickListener(v -> {
                clearQuizSession();
                showApp(2);
            });
            LinearLayout.LayoutParams abandonParams = new LinearLayout.LayoutParams(-1, dp(46));
            abandonParams.setMargins(0, dp(8), 0, 0);
            failed.addView(abandon, abandonParams);
            content.addView(failed);
            return;
        }

        addQuizResultCard();

        for (int i = 0; i < activeQuiz.size(); i++) addQuizQuestionCard(activeQuiz.get(i), i);

        Button backToQuizHome = primaryButton("결과 닫고 퀴즈 시작 화면으로");
        backToQuizHome.setOnClickListener(v -> {
            clearQuizSession();
            showApp(2);
        });
        content.addView(backToQuizHome, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private void renderQuizTaking() {
        int total = activeQuiz.size();
        if (quizCurrentIndex < 0) quizCurrentIndex = 0;
        if (quizCurrentIndex > total - 1) quizCurrentIndex = total - 1;
        final int idx = quizCurrentIndex;
        QuizQuestion q = activeQuiz.get(idx);
        int pass = PromotionRules.passCount(quizAttemptTier);

        LinearLayout topBar = row();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topBarParams = new LinearLayout.LayoutParams(-1, -2);
        topBarParams.setMargins(0, 0, 0, dp(6));
        topBar.setLayoutParams(topBarParams);

        Button exit = outlineButton("✕");
        exit.setTextSize(18);
        exit.setOnClickListener(v -> confirmExitQuiz());
        topBar.addView(exit, new LinearLayout.LayoutParams(dp(48), dp(42)));

        TextView sessionLabel = label(plainTierText(quizAttemptTier) + " 승급 세션");
        sessionLabel.setGravity(Gravity.CENTER);
        topBar.addView(sessionLabel, new LinearLayout.LayoutParams(0, -2, 1));

        boolean marked = quizMarked.length > idx && quizMarked[idx];
        Button flag = outlineButton(marked ? "🚩 표시됨" : "🏳 문제 표시");
        flag.setTextSize(12);
        flag.setOnClickListener(v -> {
            if (quizMarked.length > idx) {
                quizMarked[idx] = !quizMarked[idx];
                showApp(2);
            }
        });
        topBar.addView(flag, new LinearLayout.LayoutParams(-2, dp(42)));
        content.addView(topBar);

        content.addView(quizQuestionNavigator(total, idx));

        LinearLayout infoRow = row();
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView positionText = label("문항 " + (idx + 1) + " / " + total);
        infoRow.addView(positionText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView passText = label("합격선 " + pass + "문제");
        passText.setGravity(Gravity.END);
        infoRow.addView(passText, new LinearLayout.LayoutParams(-2, -2));
        content.addView(infoRow);

        if (idx == 0 && !quizNotice.isEmpty()) content.addView(note(quizNotice, MUTED));

        LinearLayout card = card();

        LinearLayout headerRow = row();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView topic = label(q.topic);
        topic.setPadding(0, 0, 0, 0);
        headerRow.addView(topic, new LinearLayout.LayoutParams(0, -2, 1));

        quizTimerRing = new QuizTimerRingView(this);
        headerRow.addView(quizTimerRing, new LinearLayout.LayoutParams(dp(56), dp(56)));
        card.addView(headerRow);

        card.addView(big(q.question));

        for (int i = 0; i < q.options.length; i++) {
            final int optionIndex = i;
            boolean selected = selectedAnswers.length > idx && selectedAnswers[idx] == i;
            card.addView(quizOptionView(q.options[i], i, selected, () -> {
                if (selectedAnswers.length > idx) {
                    selectedAnswers[idx] = optionIndex;
                    showApp(2);
                }
            }));
        }
        content.addView(card);

        LinearLayout buttonRow = row();
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(-1, -2);
        buttonRowParams.setMargins(0, dp(6), 0, dp(12));
        buttonRow.setLayoutParams(buttonRowParams);

        Button previous = outlineButton("← 이전");
        previous.setTextSize(15);
        previous.setEnabled(idx > 0);
        previous.setAlpha(idx > 0 ? 1f : 0.4f);
        previous.setOnClickListener(v -> moveQuizTo(idx - 1));
        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        previousParams.setMargins(0, 0, dp(5), 0);
        buttonRow.addView(previous, previousParams);

        Button next = outlineButton("다음 →");
        next.setTextSize(15);
        next.setEnabled(idx < total - 1);
        next.setAlpha(idx < total - 1 ? 1f : 0.4f);
        next.setOnClickListener(v -> moveQuizTo(idx + 1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        nextParams.setMargins(dp(5), 0, 0, 0);
        buttonRow.addView(next, nextParams);
        content.addView(buttonRow);

        int answered = 0;
        for (int value : selectedAnswers) if (value >= 0) answered++;
        Button submit = primaryButton("답안 제출 · " + answered + " / " + total + " 응답");
        submit.setOnClickListener(v -> confirmSubmitQuiz());
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-1, dp(52));
        submitParams.setMargins(0, 0, 0, dp(12));
        content.addView(submit, submitParams);

        startOrResumeQuizTimer();
    }

    /**
     * 문항 번호를 끊어진 조각으로 보여 주는 진행 표시입니다. 각 조각은 현재 문항, 응답 완료,
     * 표시해 둔 문항을 구분하며 눌러서 해당 문항으로 바로 이동할 수 있습니다.
     */
    private View quizQuestionNavigator(int total, int currentIndex) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(-1, -2);
        containerParams.setMargins(0, dp(4), 0, dp(2));
        container.setLayoutParams(containerParams);

        int rows = Math.max(1, (total + 9) / 10);
        int perRow = Math.max(1, (total + rows - 1) / rows);
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            LinearLayout rowView = row();
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, rowIndex == 0 ? 0 : dp(5), 0, 0);
            rowView.setLayoutParams(rowParams);

            int start = rowIndex * perRow;
            int end = Math.min(total, start + perRow);
            for (int i = start; i < start + perRow; i++) {
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, dp(30), 1);
                chipParams.setMargins(i == start ? 0 : dp(3), 0, 0, 0);
                if (i < end) {
                    rowView.addView(quizQuestionChip(i, i == currentIndex), chipParams);
                } else {
                    rowView.addView(new View(this), chipParams);
                }
            }
            container.addView(rowView);
        }
        return container;
    }

    private View quizQuestionChip(int index, boolean current) {
        boolean answered = selectedAnswers.length > index && selectedAnswers[index] >= 0;
        boolean marked = quizMarked.length > index && quizMarked[index];

        TextView chip = new TextView(this);
        chip.setText(String.valueOf(index + 1));
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(11);
        chip.setTypeface(current || answered ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(current ? Color.WHITE : (answered ? OCEAN : MUTED));
        chip.setClickable(true);
        chip.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(7));
        bg.setColor(current ? OCEAN : (answered ? Color.parseColor("#E4FBFA") : Color.WHITE));
        int strokeColor;
        if (marked) strokeColor = Color.parseColor("#D97706");
        else if (current || answered) strokeColor = OCEAN;
        else strokeColor = Color.parseColor("#CBD5E1");
        bg.setStroke(dp(marked || current ? 2 : 1), strokeColor);
        chip.setBackground(bg);

        chip.setOnClickListener(v -> moveQuizTo(index));
        return chip;
    }

    private View quizOptionView(String text, int index, boolean selected, Runnable onSelect) {
        LinearLayout option = new LinearLayout(this);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(14), dp(14), dp(14), dp(14));
        option.setClickable(true);
        option.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(selected ? Color.parseColor("#E4FBFA") : Color.WHITE);
        bg.setStroke(dp(selected ? 2 : 1), selected ? OCEAN : Color.parseColor("#CBD5E1"));
        option.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4));
        option.setLayoutParams(params);

        TextView marker = new TextView(this);
        marker.setText(String.valueOf((char) ('①' + index)));
        marker.setTextColor(selected ? OCEAN : MUTED);
        marker.setTextSize(20);
        marker.setTypeface(Typeface.DEFAULT_BOLD);
        marker.setGravity(Gravity.CENTER);
        option.addView(marker, new LinearLayout.LayoutParams(dp(30), -2));

        TextView optionText = new TextView(this);
        optionText.setText(tierText(text));
        optionText.setTextColor(selected ? NAVY : TEXT);
        optionText.setTextSize(15);
        optionText.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        optionText.setLineSpacing(dp(2), 1.05f);
        optionText.setPadding(dp(8), 0, 0, 0);
        option.addView(optionText, new LinearLayout.LayoutParams(0, -2, 1));

        option.setOnClickListener(v -> onSelect.run());
        return option;
    }

    private void confirmExitQuiz() {
        new AlertDialog.Builder(this)
                .setTitle("퀴즈를 나가시겠어요?")
                .setMessage("지금 나가면 진행 중인 퀴즈 답안이 저장되지 않고 사라집니다.")
                .setNegativeButton("계속 풀기", null)
                .setPositiveButton("나가기", (dialog, which) -> {
                    clearQuizSession();
                    showApp(2);
                })
                .show();
    }

    private void moveQuizTo(int index) {
        if (activeQuiz.isEmpty()) return;
        int target = Math.max(0, Math.min(activeQuiz.size() - 1, index));
        if (target == quizCurrentIndex) return;
        quizCurrentIndex = target;
        showApp(2);
    }

    private void confirmSubmitQuiz() {
        int answered = 0;
        for (int value : selectedAnswers) if (value >= 0) answered++;
        int unanswered = activeQuiz.size() - answered;
        new AlertDialog.Builder(this)
                .setTitle("답안을 제출할까요?")
                .setMessage(unanswered == 0
                        ? "모든 문항에 답했습니다. 제출한 뒤에는 답안을 수정할 수 없습니다."
                        : "아직 " + unanswered + "문항이 미응답 상태입니다. 지금 제출하면 미응답 문항은 오답으로 처리됩니다.")
                .setNegativeButton("계속 풀기", null)
                .setPositiveButton("제출", (dialog, which) -> {
                    quizAwaitingResult = true;
                    submitQuiz(quizTimedOut);
                })
                .show();
    }

    private void submitQuizOnTimeout() {
        cancelQuizTimer();
        quizTimedOut = true;
        quizAwaitingResult = true;
        submitQuiz(true);
    }

    private long quizTimeLimitMs(int questionCount) {
        return QUIZ_TIME_PER_QUESTION_MS * Math.max(0, questionCount);
    }

    private String quizTimeLimitText(int questionCount) {
        long seconds = quizTimeLimitMs(questionCount) / 1_000L;
        if (seconds < 60L) return seconds + "초";
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return remainder == 0L ? minutes + "분" : minutes + "분 " + remainder + "초";
    }

    private void generateQuizForCurrentTier() {
        final String tier = store.getTier();
        quizGenerating = true;
        quizSubmitted = false;
        quizAwaitingResult = false;
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
                    source = QUIZ_SOURCE_SERVER;
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
            final boolean finalServerAuthoritative = QUIZ_SOURCE_SERVER.equals(source);
            final String finalNotice = finalServerAuthoritative ? notice : (notice + (notice.isEmpty() ? "" : "\n")
                    + "오프라인 문제은행은 연습 모드입니다. 정답 해설은 제공되지만 XP와 승급에는 반영되지 않습니다.");
            runOnUiThread(() -> {
                quizGenerating = false;
                activeQuiz = result == null ? new ArrayList<>() : new ArrayList<>(result);
                selectedAnswers = new int[activeQuiz.size()];
                Arrays.fill(selectedAnswers, -1);
                quizMarked = new boolean[activeQuiz.size()];
                quizCurrentIndex = 0;
                quizSource = finalSource;
                quizServerAuthoritative = finalServerAuthoritative;
                quizNotice = finalNotice;
                quizTimedOut = false;
                if (activeQuiz.size() != PromotionRules.questionCount(tier)) {
                    activeQuiz.clear();
                    selectedAnswers = new int[0];
                    quizMarked = new boolean[0];
                    quizDeadlineElapsedRealtime = 0L;
                    quizSessionTotalMs = 0L;
                    quizNotice = "필요한 문제 수를 충족하지 못해 세션을 시작하지 않았습니다. 다시 생성해 주세요.";
                } else {
                    quizSessionTotalMs = quizTimeLimitMs(activeQuiz.size());
                    quizDeadlineElapsedRealtime = SystemClock.elapsedRealtime() + quizSessionTotalMs;
                }
                showApp(2);
            });
        });
    }

    private void addQuizQuestionCard(QuizQuestion q, int questionIndex) {
        LinearLayout card = card();
        boolean marked = quizMarked.length > questionIndex && quizMarked[questionIndex];
        card.addView(label((marked ? "🚩 " : "") + (questionIndex + 1) + " / " + activeQuiz.size() + " · " + q.topic));
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

    private void submitQuiz(boolean timedOut) {
        if (quizSubmitted || quizSubmitting) return;
        if (activeQuiz.isEmpty() || selectedAnswers.length != activeQuiz.size()) {
            quizNotice = "퀴즈 상태가 올바르지 않아 채점할 수 없습니다. 새 퀴즈를 생성해 주세요.";
            toast("퀴즈를 다시 생성해 주세요.");
            showApp(2);
            return;
        }

        cancelQuizTimer();
        quizTimedOut = timedOut;
        quizDeadlineElapsedRealtime = 0L;
        if (quizServerAuthoritative) {
            quizSubmitting = true;
            quizNotice = "답안과 승급 조건을 검증하고 있습니다.";
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
                        quizAwaitingResult = false;
                        quizNotice = "검증 완료 · 임의 스냅샷 값은 승급에 사용되지 않습니다.";
                        showApp(2);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        quizSubmitting = false;
                        quizNotice = "채점에 실패해 결과를 확정하지 않았습니다: " + safeMessage(error);
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
        quizAwaitingResult = false;
        quizNotice = "연습 결과입니다. 검증이 없어 XP·숙련도·승급은 변경하지 않았습니다.";
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
        quizMarked = new boolean[0];
        quizCurrentIndex = 0;
        quizSubmitted = false;
        quizGenerating = false;
        quizSubmitting = false;
        quizAwaitingResult = false;
        quizServerAuthoritative = false;
        quizAttemptTier = "";
        quizSource = "";
        quizNotice = "";
        quizCorrect = 0;
        quizAwardedXp = 0;
        quizTimedOut = false;
        quizDeadlineElapsedRealtime = 0L;
        quizSessionTotalMs = 0L;
        llmClient.clearQuizSession();
        cancelQuizTimer();
    }

    private void startOrResumeQuizTimer() {
        if (quizSubmitted || quizSubmitting || quizAwaitingResult
                || activeQuiz.isEmpty() || quizDeadlineElapsedRealtime <= 0L) return;
        long remaining = quizDeadlineElapsedRealtime - SystemClock.elapsedRealtime();
        if (remaining <= 0L) {
            submitQuizOnTimeout();
            return;
        }
        updateQuizTimerRing(remaining);
        quizCountDownTimer = new CountDownTimer(remaining, 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateQuizTimerRing(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                updateQuizTimerRing(0L);
                submitQuizOnTimeout();
            }
        }.start();
    }

    private void updateQuizTimerRing(long remainingMillis) {
        if (quizTimerRing == null) return;
        long seconds = Math.max(0L, (remainingMillis + 999L) / 1_000L);
        String label = seconds >= 60L
                ? String.format(Locale.KOREA, "%d:%02d", seconds / 60L, seconds % 60L)
                : String.valueOf(seconds);
        boolean urgent = seconds <= 30L;
        quizTimerRing.setColors(
                urgent ? DANGER : OCEAN,
                Color.parseColor("#E2E8F0"),
                urgent ? DANGER : NAVY
        );
        quizTimerRing.setRemaining(label,
                quizSessionTotalMs <= 0L ? 0f : (float) remainingMillis / quizSessionTotalMs);
    }

    private void cancelQuizTimer() {
        if (quizCountDownTimer != null) {
            quizCountDownTimer.cancel();
            quizCountDownTimer = null;
        }
    }

    private void renderSchedule() {
        content.addView(sectionTitle("AI로 활동 찾기"));
        addAiSearchBox("schedule", "AI로 활동 찾기 · 지역·대상·주제로 질문하세요", scheduleSearchLoading, scheduleSearchResponse);

        if (scheduleSearchResponse != null && scheduleSearchResponse.items != null && !scheduleSearchResponse.items.isEmpty()) {
            content.addView(body(scheduleSearchResponse.summary));
            for (ApiModels.ContentDto dto : scheduleSearchResponse.items) {
                if ("event".equals(dto.contentType)) addEventCard(eventFromDto(dto));
                else addProgramCard(programFromDto(dto));
            }
            content.addView(note("AI 검색 결과는 위 영역에만 표시되며, 아래 일정 둘러보기에는 영향을 주지 않습니다.", MUTED));
            Button closeResults = outlineButton("AI 검색 결과 닫기");
            closeResults.setOnClickListener(v -> {
                scheduleSearchResponse = null;
                showApp(3);
            });
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(44));
            closeParams.setMargins(0, 0, 0, dp(12));
            content.addView(closeResults, closeParams);
        }

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
        UserProfile p = store.getProfile();
        String tier = store.getTier();

        if (agentChat.isEmpty()) {
            addAgentBubble(false, "안녕하세요, " + store.getNickname() + "님! 🌊 해양 진로·자격·교육에 대해 무엇이든 물어보세요. "
                    + "현재 " + plainTierText(tier) + " 티어와 관심 분야(" + safeOr(p.interest, "해양") + ")에 맞춰 답해 드릴게요."
                    + (llmClient.isConfigured() ? "" : "\n\n지금은 서버 연결 전이라 오프라인 해양 상담 엔진으로 기본 경로를 안내합니다."));
        }
        for (String[] message : agentChat) {
            addAgentBubble("user".equals(message[0]), message[1]);
        }
        if (agentLoading) {
            LinearLayout thinking = agentBubbleBox(false);
            thinking.addView(body("여러 자료를 검토해 답변을 구성하고 있습니다…"));
            thinking.addView(new ProgressBar(this));
        }

        if (!agentChat.isEmpty() && contentScroll != null) {
            contentScroll.post(() -> contentScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** AI 진로 상담 하단 고정 입력 영역 (추천 질문 칩 + 입력바). */
    private LinearLayout buildAgentComposer() {
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setBackgroundColor(Color.WHITE);
        composer.setElevation(dp(6));
        composer.setPadding(dp(14), dp(8), dp(14), dp(12));

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = row();
        String[] chips = {"내 티어에서 시작할 진로", "항해사 역량 로드맵", "스마트 항만 직무", "해양환경 연구자 준비", "최신 자격·교육 확인 방법"};
        for (String chip : chips) {
            TextView suggestion = new TextView(this);
            suggestion.setText(chip);
            suggestion.setTextSize(13);
            suggestion.setTextColor(OCEAN);
            suggestion.setPadding(dp(14), dp(9), dp(14), dp(9));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(Color.WHITE);
            chipBg.setCornerRadius(dp(18));
            chipBg.setStroke(dp(1), Color.parseColor("#B8D7DF"));
            suggestion.setBackground(chipBg);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2);
            chipParams.setMargins(0, 0, dp(8), 0);
            suggestion.setLayoutParams(chipParams);
            suggestion.setOnClickListener(v -> requestAgentAnswer(chip));
            chipRow.addView(suggestion);
        }
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams chipScrollParams = new LinearLayout.LayoutParams(-1, -2);
        chipScrollParams.setMargins(0, 0, 0, dp(8));
        composer.addView(chipScroll, chipScrollParams);

        LinearLayout inputRow = row();
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = inputField("궁금한 진로를 물어보세요…", "");
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button send = primaryButton("➤");
        send.setEnabled(!agentLoading);
        send.setOnClickListener(v -> requestAgentAnswer(input.getText().toString()));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(56), dp(48));
        sendParams.setMargins(dp(8), 0, 0, 0);
        inputRow.addView(send, sendParams);
        composer.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));
        return composer;
    }

    /** 대화 말풍선 컨테이너를 만들어 content에 붙이고 반환합니다. */
    private LinearLayout agentBubbleBox(boolean fromUser) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        if (fromUser) {
            bg.setColors(new int[]{NAVY, OCEAN});
            bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            bg.setCornerRadii(new float[]{dp(16), dp(16), dp(4), dp(4), dp(16), dp(16), dp(16), dp(16)});
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), Color.parseColor("#DCEBEF"));
            bg.setCornerRadii(new float[]{dp(4), dp(4), dp(16), dp(16), dp(16), dp(16), dp(16), dp(16)});
        }
        bubble.setBackground(bg);

        LinearLayout lineRow = row();
        lineRow.setGravity(fromUser ? Gravity.END : Gravity.START);
        if (!fromUser) {
            TextView avatar = new TextView(this);
            avatar.setText("✦");
            avatar.setTextColor(Color.WHITE);
            avatar.setTextSize(14);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable avatarBg = new GradientDrawable();
            avatarBg.setShape(GradientDrawable.OVAL);
            avatarBg.setColor(OCEAN);
            avatar.setBackground(avatarBg);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            avatarParams.setMargins(0, 0, dp(8), 0);
            avatar.setLayoutParams(avatarParams);
            lineRow.addView(avatar);
        }
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(-2, -2);
        bubbleParams.setMargins(fromUser ? dp(48) : 0, dp(4), fromUser ? 0 : dp(48), dp(4));
        lineRow.addView(bubble, bubbleParams);
        content.addView(lineRow, new LinearLayout.LayoutParams(-1, -2));
        return bubble;
    }

    private void addAgentBubble(boolean fromUser, String text) {
        LinearLayout bubble = agentBubbleBox(fromUser);
        TextView message = body(text);
        if (fromUser) message.setTextColor(Color.WHITE);
        bubble.addView(message);
    }

    private void requestAgentAnswer(String question) {
        final String trimmed = question == null ? "" : question.trim();
        if (trimmed.isEmpty()) {
            toast("질문을 입력해 주세요.");
            return;
        }
        agentChat.add(new String[]{"user", trimmed});
        agentLoading = true;
        showApp(4);
        executor.execute(() -> {
            String answer;
            if (llmClient.isConfigured()) {
                try {
                    UserProfile profile = store.getProfile();
                    answer = invokeAgentAnswerCompat(trimmed, profile);
                } catch (Exception e) {
                    answer = "LLM 호출에 실패해 로컬 상담으로 전환했습니다.\n\n"
                            + RecommendationEngine.answerAgent(trimmed, store.getProfile(), store.getTier())
                            + "\n\n오류: " + safeMessage(e);
                }
            } else {
                answer = RecommendationEngine.answerAgent(trimmed, store.getProfile(), store.getTier());
            }
            final String result = answer;
            runOnUiThread(() -> {
                agentLoading = false;
                agentChat.add(new String[]{"assistant", result});
                showApp(4);
            });
        });
    }

    /** 커뮤니티 우측 하단의 작은 글쓰기 버튼입니다. */
    private void addCommunityWriteFab() {
        LinearLayout writeFab = new LinearLayout(this);
        writeFab.setOrientation(LinearLayout.HORIZONTAL);
        writeFab.setGravity(Gravity.CENTER_VERTICAL);
        writeFab.setPadding(dp(6), 0, dp(12), 0);
        writeFab.setClickable(true);
        writeFab.setFocusable(true);
        writeFab.setContentDescription("커뮤니티 글쓰기");
        writeFab.setElevation(dp(10));

        GradientDrawable fabBackground = new GradientDrawable();
        fabBackground.setColor(OCEAN);
        fabBackground.setCornerRadius(dp(24));
        fabBackground.setStroke(dp(1), Color.parseColor("#66FFFFFF"));
        writeFab.setBackground(fabBackground);

        TextView pencil = new TextView(this);
        pencil.setText("✎");
        pencil.setTextColor(OCEAN);
        pencil.setTextSize(18);
        pencil.setTypeface(Typeface.DEFAULT_BOLD);
        pencil.setGravity(Gravity.CENTER);
        pencil.setIncludeFontPadding(false);

        GradientDrawable plusBackground = new GradientDrawable();
        plusBackground.setShape(GradientDrawable.OVAL);
        plusBackground.setColor(Color.WHITE);
        pencil.setBackground(plusBackground);
        writeFab.addView(pencil, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView label = new TextView(this);
        label.setText("글쓰기");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(7), 0, 0, 0);
        writeFab.addView(label, new LinearLayout.LayoutParams(-2, -1));

        writeFab.setOnClickListener(v -> openCommunityPostScreen());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(106), dp(48), Gravity.END | Gravity.BOTTOM);
        // 하단 내비게이션 바와 겹치지 않도록 바 높이만큼 띄웁니다.
        params.setMargins(dp(14), dp(14), dp(14), dp(82));
        appRoot.addView(writeFab, params);
    }

    private void renderCommunity() {
        if (isCommunityOverlayOpen(5)) {
            renderCommunityOverlay();
            return;
        }
        if (communityDetailPost != null) {
            renderCommunityDetailScreen();
            return;
        }

        LinearLayout scopeRow = row();
        scopeRow.setGravity(Gravity.CENTER_VERTICAL);
        scopeRow.addView(communityScopeChip("전체 글", "all"));
        scopeRow.addView(communityScopeChip("팔로잉", "following"));
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(-2, -2);
        scopeParams.setMargins(0, 0, 0, dp(8));
        content.addView(scopeRow, scopeParams);

        LinearLayout segment = row();
        segment.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable segmentBg = new GradientDrawable();
        segmentBg.setColor(Color.parseColor("#E3EEF1"));
        segmentBg.setCornerRadius(dp(24));
        segment.setBackground(segmentBg);
        segment.setPadding(dp(4), dp(4), dp(4), dp(4));
        segment.addView(communitySegmentButton("전체", "all"), new LinearLayout.LayoutParams(0, dp(40), 1));
        segment.addView(communitySegmentButton("자유 게시판", "free"), new LinearLayout.LayoutParams(0, dp(40), 1));
        segment.addView(communitySegmentButton("질문 게시판", "question"), new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-1, -2);
        segmentParams.setMargins(0, 0, 0, dp(10));
        content.addView(segment, segmentParams);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = row();
        chipRow.addView(communityTagChip("#전체", communityTag.isEmpty(), () -> switchCommunityTag("")));
        String[] tagSet = "question".equals(communityCategory) ? COMMUNITY_TAGS_QUESTION : COMMUNITY_TAGS_FREE;
        for (String tagName : tagSet) {
            chipRow.addView(communityTagChip("#" + tagName, tagName.equals(communityTag),
                    () -> switchCommunityTag(tagName)));
        }
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-1, -2);
        chipParams.setMargins(0, 0, 0, dp(6));
        content.addView(chipScroll, chipParams);

        LinearLayout searchCard = card();
        LinearLayout searchRow = row();
        communitySearchInput = inputField("제목·본문·작성자 검색", communityQuery);
        communitySearchInput.setSingleLine(true);
        searchRow.addView(communitySearchInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button searchButton = primaryButton("검색");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(78), dp(48));
        searchParams.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchParams);
        searchButton.setOnClickListener(v -> runCommunitySearchNow());
        communitySearchInput.setOnEditorActionListener((v, actionId, event) -> {
            runCommunitySearchNow();
            return true;
        });
        searchCard.addView(searchRow);

        communitySearchClearButton = outlineButton("검색어 지우기");
        communitySearchClearButton.setVisibility(communityQuery.isEmpty() ? View.GONE : View.VISIBLE);
        communitySearchClearButton.setOnClickListener(v -> {
            if (communitySearchInput != null) {
                communitySearchInput.setText("");
                communitySearchInput.requestFocus();
            }
        });
        searchCard.addView(communitySearchClearButton, new LinearLayout.LayoutParams(-1, dp(42)));
        content.addView(searchCard);

        communitySearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                String nextQuery = editable == null ? "" : editable.toString().trim();
                if (communitySearchClearButton != null) {
                    communitySearchClearButton.setVisibility(nextQuery.isEmpty() ? View.GONE : View.VISIBLE);
                }
                if (nextQuery.equals(communityQuery)) return;
                communityQuery = nextQuery;
                scheduleCommunitySearch(nextQuery.isEmpty() ? 0L : COMMUNITY_SEARCH_DEBOUNCE_MS);
            }
        });

        communityResultsContainer = new LinearLayout(this);
        communityResultsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(communityResultsContainer, new LinearLayout.LayoutParams(-1, -2));
        renderCommunityResults();

        if (!communityInitialized && !communityLoading) requestCommunityRefresh();
    }

    private String tabGuideText(int tab) {
        switch (tab) {
            case 1: return "관심 분야, 현재 티어와 학습 목표에 맞는 해양 자료를 문장으로 검색하고, 영상과 논문 탭을 구분해 필요한 콘텐츠를 탐색해 보세요. "
                    + "AI 검색은 앱에 등록된 자료를 우선 활용하며, 가능한 경우 실시간 웹 근거까지 함께 검토해 결과와 요약을 보여줍니다.\n\n"
                    + "영상 탭에서는 입문·진로 탐색·직무 심화 난도별 라이브러리를 확인할 수 있습니다. 각 카드에서 권장 티어, 적합도, 출처, 소요 시간, 분야, 연결 진로와 추천 이유를 살펴보고, "
                    + "영상을 시작하거나 이어서 시청하고 찜 목록에 저장할 수 있습니다. 시청 후에는 핵심 내용을 제출해 학습 완료를 인증하고 XP와 역량 기록에 반영할 수 있습니다. 논문 탭에서는 저자·연도·학술지·DOI·초록을 확인하고 원문을 읽은 뒤 요약을 학습 증거로 저장할 수 있습니다.";
            case 2: return "현재 통합 티어와 다음 승급 기준을 확인한 뒤, 내 관심 분야와 학습 기록에 맞춘 4지선다 퀴즈에 도전해 보세요. "
                    + "서버 연결 시에는 해양 AI가 앱 자료와 공공·기관 근거를 바탕으로 문제를 만들고, 연결이 어려울 때는 검증된 해양 로컬 문제은행으로 자동 전환됩니다.\n\n"
                    + "퀴즈 세션은 티어별 문항 수와 합격선이 적용되며 30초 제한 시간이 지나면 미응답 문항까지 자동 제출됩니다. 제출 후에는 점수, 정답 여부, 내가 고른 답, 정답과 해설, 획득 XP와 승급 결과를 문항별로 확인할 수 있습니다. "
                    + "각 주제의 정답·오답 기록은 MY의 역량 여권과 다음 학습·진로 추천에 반영되며, 같은 티어의 새 퀴즈에 다시 도전하거나 승급 기준 전체 매뉴얼을 확인할 수 있습니다.";
            case 3: return "지역, 대상, 시기와 관심 주제를 문장으로 입력해 해양 교육·행사를 AI로 찾거나, 분야 태그와 진행 상태를 선택해 전체 일정을 직접 둘러보세요. "
                    + "AI 검색 결과는 앱 자료와 가능한 실시간 웹 근거를 바탕으로 별도 영역에 표시되므로, 아래의 기본 일정 목록과 필터는 그대로 유지됩니다.\n\n"
                    + "오프라인 일정은 월별 달력에서 점으로 표시되며 이전·다음 달을 이동하고 날짜를 눌러 해당 일정을 모아볼 수 있습니다. 온라인·상시 활동은 별도 목록에서 더보기와 접기를 지원합니다. "
                    + "각 교육 과정과 이벤트 카드에서는 모집 상태, 대상, 운영 방식, 기간, 설명, 추천 점수와 추천 이유를 확인하고 찜 목록에 저장할 수 있으며, 신청 가능한 교육 과정은 기기 캘린더에 바로 추가할 수 있습니다. 종료된 일정도 유사 활동 탐색과 관심 분석을 위한 아카이브로 확인할 수 있습니다.";
            case 4: return "궁금한 해양 직무, 전공, 자격, 프로젝트와 학습 순서를 자유롭게 질문하면 BluePath AI가 내 관심 분야, 현재 티어와 앱의 학습 자료를 함께 검토해 맞춤 답변을 제공합니다. "
                    + "서버가 연결된 경우 기관 자료와 설정된 실시간 웹 검색 결과를 근거로 답변하고, 연결 전에는 오프라인 해양 상담 엔진으로 기본 진로 경로를 안내합니다.\n\n"
                    + "직접 질문을 입력하거나 항해사 역량 로드맵, 스마트 항만 직무, 해양환경 연구자 준비 같은 추천 질문을 바로 선택할 수 있습니다. 답변 아래에서는 NCS 기반 추천 직무를 살펴보며 직무 적합도, 권장 티어, 업무 설명, 추천 이유, 필요한 역량, 연결 기관과 근무지 예시를 확인하고, "
                    + "역량 진단 → 근거 영상 학습 → 승급 퀴즈 → 실제 교육 과정 → 자격·프로젝트 증빙으로 이어지는 준비 항로를 설계할 수 있습니다.";
            case 5: return "해양 진로를 준비하는 사람들이 후기와 질문을 나누는 공간이에요.\n\n"
                    + "① 탭으로 게시판 고르기\n전체 · 자유 게시판 · 질문 게시판을 오갈 수 있어요.\n\n"
                    + "② 태그로 좁혀 보기\n탭 아래 태그를 누르면 주제별로 글이 걸러집니다.\n\n"
                    + "③ 글쓰기 · 답변하기\n오른쪽 아래 ✏️ 버튼으로 글을 쓰고, 질문 글에는 답변을 남길 수 있어요.\n\n"
                    + "④ 답변 채택\n질문 작성자가 답변 하나를 채택하면 「채택 완료」 표시가 붙고 목록 맨 위에 고정돼요.\n\n"
                    + "⑤ 팔로우\n글·댓글의 「＋ 팔로우」 버튼을 누르면 그 사람을 팔로우합니다. 맨 위 「팔로잉」 칩을 누르면 팔로우한 사람의 글만 모아 볼 수 있어요.\n\n"
                    + "⑥ 프로필 보기\n닉네임이나 프로필 사진을 누르면 그 사람의 프로필로 이동해 팔로워·팔로잉 목록과 작성한 글을 확인할 수 있습니다.\n\n"
                    + "서로의 진로를 응원하는 공간이에요. 비방·홍보 글은 신고할 수 있고, 차단한 사용자의 글과 댓글은 목록에서 제외됩니다.";
            case 6: return "내 프로필 사진, 관심 분야, 목표, 통합 티어, XP, 팔로워·팔로잉 수와 학습·찜·퀴즈 통계를 한곳에서 확인하세요. "
                    + "Ocean Skill Map에서는 퀴즈, 학습 완료와 현장 미션으로 쌓인 분야별 숙련도와 증거를 살펴보고, 노드를 눌러 점수 근거, 하위 역량, NCS 연계, 연결 진로와 다음 추천 활동을 확인할 수 있습니다.\n\n"
                    + "검증된 학습·미션 기록은 목표 진로 준비도와 증거 코드가 포함된 해양 역량 포트폴리오로 미리 보거나 PDF로 생성해 공유할 수 있습니다. 승급·학습 리포트에서는 티어별 최고 퀴즈 점수와 최근 결과를 확인하고, 완료한 영상과 찜한 항목도 다시 살펴볼 수 있습니다.\n\n"
                    + "연령대, 관심 분야, 학습 목적과 현재 수준을 수정하고 프로필 사진을 업로드할 수 있으며, 미성년 계정의 보호자 동의와 클라우드 동기화, 최신 학습 자료 불러오기, 로그아웃을 관리할 수 있습니다. "
                    + "매일 학습 알림의 시간 설정·해제와 시험·자격 일정의 캘린더 추가를 지원하며, 플래티넘 이상에서는 다이아 고급 퀴즈·자격 증빙·해양 프로젝트 제출 및 검토 상태를 관리할 수 있습니다. 필요할 때는 기기의 프로필과 학습 기록 전체를 초기화할 수 있습니다.";
            default: return "";
        }
    }

    private void showTabGuideDialog(int tab) {
        new AlertDialog.Builder(this)
                .setTitle("🧭 " + tabTitle(tab) + " 사용법")
                .setMessage(tabGuideText(tab))
                .setPositiveButton("확인", null)
                .show();
    }

    private void switchCommunityCategory(String category) {
        if (category == null || category.equals(communityCategory)) return;
        cancelPendingCommunitySearch();
        communityRequestVersion++;
        communityCategory = category;
        communityTag = "";
        communityMeta = null;
        communityLoading = false;
        communityInitialized = false;
        communityError = "";
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        communityExpandedPosts.clear();
        if (currentTab == 5) showApp(5);
    }

    private void switchCommunityTag(String tag) {
        String next = tag == null ? "" : tag;
        if (next.equals(communityTag)) return;
        cancelPendingCommunitySearch();
        communityRequestVersion++;
        communityTag = next;
        communityLoading = false;
        communityInitialized = false;
        communityError = "";
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        communityExpandedPosts.clear();
        if (currentTab == 5) showApp(5);
    }

    private TextView communityScopeChip(String labelText, String scope) {
        boolean selected = scope.equals(communityScope);
        TextView chip = new TextView(this);
        chip.setText(labelText);
        chip.setTextSize(12);
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(selected ? Color.WHITE : MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? NAVY : Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), selected ? NAVY : Color.parseColor("#CBD9DE"));
        chip.setBackground(bg);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> switchCommunityScope(scope));
        return chip;
    }

    private void switchCommunityScope(String scope) {
        if (scope == null || scope.equals(communityScope)) return;
        cancelPendingCommunitySearch();
        communityScope = scope;
        communityRequestVersion++;
        communityMeta = null;
        communityLoading = false;
        communityInitialized = false;
        communityError = "";
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        communityExpandedPosts.clear();
        if (currentTab == 5) showApp(5);
    }

    private void switchCommunitySort(String sort) {
        if (sort == null || sort.equals(communitySort)) return;
        communitySort = sort;
        communityRequestVersion++;
        communityInitialized = false;
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        if (currentTab == 5) showApp(5);
    }

    private Button communitySegmentButton(String labelText, String category) {
        boolean selected = category.equals(communityCategory);
        Button button = new Button(this);
        button.setText(labelText);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setTextColor(selected ? NAVY : MUTED);
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(20));
            button.setBackground(bg);
            button.setElevation(dp(1));
        } else {
            button.setBackgroundColor(Color.TRANSPARENT);
        }
        button.setOnClickListener(v -> switchCommunityCategory(category));
        return button;
    }

    private TextView communityTagChip(String labelText, boolean selected, Runnable onClick) {
        TextView chip = new TextView(this);
        chip.setText(labelText);
        chip.setTextSize(11);
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        String tag = labelText == null ? "" : labelText.replaceFirst("^#", "");
        chip.setTextColor(communityTagTextColor(tag, selected));
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(communityTagBackgroundColor(tag, selected));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(selected ? 2 : 1), selected ? OCEAN : Color.parseColor("#D2E1E5"));
        chip.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(5), 0);
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    private int communityTagBackgroundColor(String tag, boolean selected) {
        if (selected) {
            switch (tag) {
                case "후기": return Color.parseColor("#23876E");
                case "정보공유": return Color.parseColor("#1687A0");
                case "진로고민": return Color.parseColor("#A56E09");
                case "자격증·시험": return Color.parseColor("#6961B3");
                case "모임·번개": return Color.parseColor("#B94B73");
                case "학습자료": return Color.parseColor("#3976B8");
                case "입시": return Color.parseColor("#B96834");
                case "현직에게": return Color.parseColor("#258478");
                default: return NAVY;
            }
        }
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

    private int communityTagTextColor(String tag, boolean selected) {
        if (selected) return Color.WHITE;
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

    private TextView communityTierBadge(String tier) {
        TextView badge = new TextView(this);
        badge.setText(tier);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        int color;
        switch (tier == null ? "" : tier) {
            case "실버": color = Color.parseColor("#8C9BAB"); break;
            case "골드": color = Color.parseColor("#C99B12"); break;
            case "플래티넘": color = Color.parseColor("#2AA6A0"); break;
            case "다이아": color = Color.parseColor("#3E7BD6"); break;
            default: color = Color.parseColor("#A8763E"); break;
        }
        badge.setTextColor(color);
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F4F8FA"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), color);
        badge.setBackground(bg);
        return badge;
    }

    private String communityRelativeTime(String value) {
        if (value == null || value.trim().isEmpty()) return "방금 전";
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date parsed = format.parse(value.length() > 19 ? value.substring(0, 19) : value);
            if (parsed == null) return readableDate(value);
            long minutes = Math.max(0L, (System.currentTimeMillis() - parsed.getTime()) / 60000L);
            if (minutes < 1) return "방금 전";
            if (minutes < 60) return minutes + "분 전";
            long hours = minutes / 60;
            if (hours < 24) return hours + "시간 전";
            long days = hours / 24;
            if (days < 7) return days + "일 전";
            return readableDate(value);
        } catch (Exception ignored) {
            return readableDate(value);
        }
    }

    private void scheduleCommunitySearch(long delayMs) {
        cancelPendingCommunitySearch();
        communitySearchRunnable = () -> {
            communitySearchRunnable = null;
            requestCommunityRefresh();
        };
        mainHandler.postDelayed(communitySearchRunnable, Math.max(0L, delayMs));
    }

    private void cancelPendingCommunitySearch() {
        if (communitySearchRunnable == null) return;
        mainHandler.removeCallbacks(communitySearchRunnable);
        communitySearchRunnable = null;
    }

    private void runCommunitySearchNow() {
        if (communitySearchInput != null) {
            communityQuery = communitySearchInput.getText().toString().trim();
        }
        cancelPendingCommunitySearch();
        requestCommunityRefresh();
    }

    private void renderCommunityResults() {
        if (communityResultsContainer == null) return;
        // 상세·프로필·팔로우 목록이 떠 있는 동안에는 뒤에 숨은 목록을 다시 그리지 않습니다.
        if (communityDetailPost != null || isCommunityOverlayOpen(5)) return;
        communityResultsContainer.removeAllViews();
        followViewRefreshers.clear();

        if (communityMeta != null) {
            LinearLayout metaRow = row();
            metaRow.setGravity(Gravity.CENTER_VERTICAL);
            String countText = "question".equals(communityCategory)
                    ? "질문 " + communityMeta.postCount + "개 · 미답변 " + communityMeta.unansweredCount + "개"
                    : "게시글 " + communityMeta.postCount + "개";
            TextView count = label(countText);
            metaRow.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
            TextView sortView = label(("popular".equals(communitySort) ? "인기순" : "최신순") + " ▾");
            sortView.setTextColor(OCEAN);
            sortView.setPadding(dp(10), dp(6), dp(2), dp(6));
            sortView.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("정렬")
                    .setItems(new String[]{"최신순", "인기순"}, (dialog, which) ->
                            switchCommunitySort(which == 1 ? "popular" : "latest"))
                    .show());
            metaRow.addView(sortView);
            communityResultsContainer.addView(metaRow);

            ApiModels.CommunityPostDto hot = communityMeta.weeklyHot;
            if (hot != null && communityQuery.isEmpty() && communityTag.isEmpty()) {
                TextView banner = new TextView(this);
                int hotComments = hot.commentCount > 0 ? hot.commentCount
                        : (hot.comments == null ? 0 : hot.comments.size());
                String hotLabel = "question".equals(communityCategory) ? "이번 주 인기 질문" : "이번 주 인기";
                banner.setText("🔥 " + hotLabel + " · " + hot.title + " (댓글 " + hotComments + ")");
                banner.setTextColor(Color.WHITE);
                banner.setTextSize(14);
                banner.setTypeface(Typeface.DEFAULT_BOLD);
                banner.setSingleLine(true);
                banner.setEllipsize(android.text.TextUtils.TruncateAt.END);
                banner.setPadding(dp(16), dp(13), dp(16), dp(13));
                GradientDrawable bannerBg = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{NAVY, Color.parseColor("#0E7490")});
                bannerBg.setCornerRadius(dp(14));
                banner.setBackground(bannerBg);
                LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(-1, -2);
                bannerParams.setMargins(0, dp(4), 0, dp(10));
                banner.setLayoutParams(bannerParams);
                banner.setOnClickListener(v -> openCommunityDetail(hot));
                communityResultsContainer.addView(banner);
            }
        }

        if (!communityError.isEmpty()) {
            communityResultsContainer.addView(note(communityError, DANGER));
            Button retry = primaryButton("다시 시도");
            retry.setOnClickListener(v -> requestCommunityRefresh());
            LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-1, dp(48));
            retryParams.setMargins(0, 0, 0, dp(12));
            communityResultsContainer.addView(retry, retryParams);
        }

        if (communityPosts.isEmpty() && communityLoading) {
            LinearLayout loading = card();
            loading.addView(big(communityQuery.isEmpty()
                    ? "커뮤니티를 불러오고 있습니다"
                    : "검색 결과를 빠르게 찾고 있습니다"));
            loading.addView(new ProgressBar(this));
            communityResultsContainer.addView(loading);
            return;
        }

        if (communityPosts.isEmpty()) {
            LinearLayout empty = card();
            if (!communityQuery.isEmpty()) {
                empty.addView(big("검색 결과가 없습니다"));
                empty.addView(body("다른 검색어를 입력해 보세요."));
            } else if ("following".equals(communityScope)) {
                empty.addView(big("팔로우한 사용자의 글이 없습니다"));
                empty.addView(body("전체 글에서 마음에 드는 작성자를 팔로우하면 이곳에 모아서 볼 수 있어요."));
                Button browseAll = outlineButton("전체 글 보기");
                browseAll.setOnClickListener(v -> switchCommunityScope("all"));
                empty.addView(browseAll, new LinearLayout.LayoutParams(-1, dp(44)));
            } else {
                empty.addView(big("아직 게시글이 없습니다"));
                empty.addView(body("첫 번째 해양 이야기를 남겨 보세요."));
            }
            communityResultsContainer.addView(empty);
            return;
        }

        for (ApiModels.CommunityPostDto post : communityPosts) {
            addCommunityPostCard(communityResultsContainer, post);
        }
        if (communityLoading) {
            communityResultsContainer.addView(new ProgressBar(this));
        } else if (communityHasMore) {
            Button more = outlineButton("게시글 더 보기");
            more.setOnClickListener(v -> requestCommunityPage(true));
            communityResultsContainer.addView(more, new LinearLayout.LayoutParams(-1, dp(48)));
        }
    }

    /** 당겨서 새로고침은 지금 보고 있는 화면만 다시 불러옵니다. */
    private void requestCommunityScreenRefresh() {
        if (isCommunityOverlayOpen(5)) {
            if (!communityFollowListUserId.isEmpty()) loadFollowList();
            else loadCommunityProfile();
            return;
        }
        requestCommunityRefresh();
    }

    private void requestCommunityRefresh() {
        communityInitialized = false;
        communityOffset = 0;
        communityHasMore = true;
        communityPosts.clear();
        int requestVersion = ++communityRequestVersion;
        loadCommunityPage(false, requestVersion);
        if (communityDetailPost != null) refreshCommunityDetail();
    }

    private void requestCommunityPage(boolean append) {
        if (!append) {
            requestCommunityRefresh();
            return;
        }
        if (communityLoading || !communityHasMore) return;
        loadCommunityPage(true, communityRequestVersion);
    }

    private void loadCommunityPage(boolean append, int requestVersion) {
        communityLoading = true;
        communityError = "";
        renderCommunityResults();

        final int requestedOffset = append ? communityOffset : 0;
        final String requestedCategory = communityCategory;
        final String requestedQuery = communityQuery;
        final String requestedTag = communityTag;
        final String requestedSort = communitySort;
        final String requestedScope = communityScope;
        communityExecutor.execute(() -> {
            try {
                List<ApiModels.CommunityPostDto> result = cloudRepository.communityPosts(
                        requestedCategory, requestedQuery, requestedTag, requestedSort,
                        requestedScope, "", COMMUNITY_PAGE_SIZE, requestedOffset);
                ApiModels.CommunityFeedMetaDto meta = null;
                if (!append) {
                    try {
                        meta = cloudRepository.communityFeedMeta(requestedCategory, requestedScope);
                    } catch (Exception ignored) {
                        // 요약 정보(카운트·인기글)는 실패해도 목록 표시를 막지 않습니다.
                    }
                }
                final ApiModels.CommunityFeedMetaDto loadedMeta = meta;
                runOnUiThread(() -> {
                    if (requestVersion != communityRequestVersion
                            || !requestedCategory.equals(communityCategory)
                            || !requestedScope.equals(communityScope)
                            || !requestedQuery.equals(communityQuery)) return;

                    List<ApiModels.CommunityPostDto> page = result == null ? new ArrayList<>() : result;
                    if (!append) communityPosts = new ArrayList<>();
                    communityPosts.addAll(page);
                    if (loadedMeta != null) communityMeta = loadedMeta;
                    communityOffset = requestedOffset + page.size();
                    communityHasMore = page.size() == COMMUNITY_PAGE_SIZE;
                    communityLoading = false;
                    communityInitialized = true;
                    renderCommunityResults();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestVersion != communityRequestVersion
                            || !requestedCategory.equals(communityCategory)
                            || !requestedScope.equals(communityScope)
                            || !requestedQuery.equals(communityQuery)) return;
                    communityLoading = false;
                    communityInitialized = true;
                    communityError = "커뮤니티 연결 실패: " + safeMessage(e);
                    renderCommunityResults();
                });
            }
        });
    }

    private void openCommunityPostScreen() {
        Intent intent = new Intent(this, CommunityPostActivity.class);
        intent.putExtra(CommunityPostActivity.EXTRA_CATEGORY, communityCategory);
        communityPostLauncher.launch(intent);
    }

    private void addCommunityPostCard(LinearLayout parent, ApiModels.CommunityPostDto post) {
        LinearLayout card = card();
        card.setLayoutTransition(new LayoutTransition());
        boolean expanded = communityExpandedPosts.contains(post.id);
        int commentTotal = post.commentCount > 0 ? post.commentCount
                : (post.comments == null ? 0 : post.comments.size());

        LinearLayout authorRow = row();
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        View avatar = communityAvatar(post.author, dp(40));
        avatar.setOnClickListener(v -> openCommunityProfile(post.author.userId));
        authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout nameRow = row();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setPadding(dp(8), 0, 0, 0);
        TextView nickname = body(post.author.nickname);
        nickname.setTypeface(Typeface.DEFAULT_BOLD);
        nickname.setPadding(0, 0, dp(6), 0);
        nickname.setOnClickListener(v -> openCommunityProfile(post.author.userId));
        nameRow.addView(nickname);
        nameRow.addView(communityTierBadge(post.author.tier));
        authorRow.addView(nameRow, new LinearLayout.LayoutParams(0, -2, 1));
        TextView timeMeta = label(("question".equals(post.category) ? "질문" : "자유")
                + " · " + communityRelativeTime(post.createdAt));
        timeMeta.setPadding(0, 0, 0, 0);
        authorRow.addView(timeMeta);
        card.addView(authorRow);

        if ("question".equals(post.category)) {
            LinearLayout statusRow = row();
            boolean acceptedDone = post.acceptedCommentId != null && !post.acceptedCommentId.isEmpty();
            TextView status = new TextView(this);
            status.setText(acceptedDone ? "채택 완료" : "답변 대기");
            status.setTextSize(11);
            status.setTypeface(Typeface.DEFAULT_BOLD);
            status.setTextColor(acceptedDone ? Color.parseColor("#1D7A4C") : Color.parseColor("#C24A5A"));
            status.setPadding(dp(10), dp(3), dp(10), dp(3));
            GradientDrawable statusBg = new GradientDrawable();
            statusBg.setColor(acceptedDone ? Color.parseColor("#E1F5E9") : Color.parseColor("#FBE4E8"));
            statusBg.setCornerRadius(dp(10));
            status.setBackground(statusBg);
            statusRow.addView(status);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
            statusParams.setMargins(0, dp(6), 0, 0);
            card.addView(statusRow, statusParams);
        }

        TextView title = big(post.title);
        card.addView(title);

        TextView preview = body(communityPlainPreview(post.body));
        preview.setMaxLines(2);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        preview.setVisibility(expanded ? View.GONE : View.VISIBLE);
        card.addView(preview);

        if (post.tags != null && !post.tags.isEmpty()) {
            LinearLayout tagRow = row();
            tagRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            for (String tagName : post.tags) {
                tagRow.addView(communityTagChip("#" + tagName, false, () -> switchCommunityTag(tagName)));
            }
            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(-2, -2);
            tagParams.setMargins(0, dp(4), 0, dp(2));
            card.addView(tagRow, tagParams);
        }

        LinearLayout footer = row();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        int likeCount = 0;
        if (post.reactions != null) {
            for (ApiModels.ReactionSummary reaction : post.reactions) {
                if ("👍".equals(reaction.emoji)) likeCount = reaction.count;
            }
        }
        TextView counts = label("👍 " + likeCount + " · 💬 "
                + ("question".equals(post.category) ? "답변 " : "") + commentTotal);
        counts.setPadding(0, 0, 0, 0);
        footer.addView(counts, new LinearLayout.LayoutParams(0, -2, 1));
        if (!isMyAuthor(post.author)) {
            footer.addView(followToggleButton(post.author, 11), new LinearLayout.LayoutParams(dp(84), dp(32)));
        }
        card.addView(footer);

        View.OnClickListener open = v -> openCommunityDetail(post);
        card.setOnClickListener(open);
        title.setOnClickListener(open);
        preview.setOnClickListener(open);
        parent.addView(card);
    }

    private String communityPlainPreview(String value) {
        String safe = value == null ? "" : value;
        if (safe.startsWith(RICH_BODY_MARKER)) {
            safe = Html.fromHtml(safe.substring(RICH_BODY_MARKER.length()), Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return safe.replaceAll("\\s+", " ").trim();
    }

    private void openCommunityDetail(ApiModels.CommunityPostDto post) {
        // 프로필 화면에서 글을 열었다면 뒤로 가기로 그 프로필에 되돌아갈 수 있도록 기억해 둡니다.
        communityDetailReturnProfileUserId = communityProfileUserId;
        communityProfileUserId = "";
        communityFollowListUserId = "";
        communityDetailPost = post;
        communityOverlayTab = 5;
        if (currentTab == 5) showApp(5);
        refreshCommunityDetail();
    }

    private void closeCommunityDetail() {
        communityDetailPost = null;
        String returnProfile = communityDetailReturnProfileUserId;
        communityDetailReturnProfileUserId = "";
        if (!returnProfile.isEmpty()) {
            openCommunityProfile(returnProfile);
            return;
        }
        if (currentTab == 5) showApp(5);
    }

    private void refreshCommunityDetail() {
        ApiModels.CommunityPostDto current = communityDetailPost;
        if (current == null) return;
        communityExecutor.execute(() -> {
            try {
                ApiModels.CommunityPostDto fresh = cloudRepository.communityPost(current.id);
                runOnUiThread(() -> {
                    if (communityDetailPost == null || !communityDetailPost.id.equals(current.id)) return;
                    communityDetailPost = fresh;
                    if (currentTab == 5) showApp(5);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (communityDetailPost == null || !communityDetailPost.id.equals(current.id)) return;
                    toast("게시글을 다시 불러오지 못했습니다: " + safeMessage(e));
                    closeCommunityDetail();
                });
            }
        });
    }

    private void renderCommunityDetailScreen() {
        ApiModels.CommunityPostDto post = communityDetailPost;
        if (post == null) return;
        boolean question = "question".equals(post.category);

        LinearLayout topBar = row();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = sectionTitle("‹ " + (question ? "질문" : "게시글"));
        back.setOnClickListener(v -> closeCommunityDetail());
        topBar.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = sectionTitle("⋯");
        menu.setPadding(dp(12), dp(12), dp(4), dp(8));
        menu.setOnClickListener(v -> {
            String[] options = post.canEdit
                    ? new String[]{"수정", "삭제"}
                    : new String[]{"신고", "작성자 차단"};
            new AlertDialog.Builder(this).setItems(options, (dialog, which) -> {
                if (post.canEdit) {
                    if (which == 0) openCommunityPostEditScreen(post);
                    else confirmDeletePost(post.id);
                } else {
                    if (which == 0) showReportDialog("post", post.id);
                    else confirmBlockUser(post.author.userId, post.author.nickname);
                }
            }).show();
        });
        topBar.addView(menu);
        content.addView(topBar);

        LinearLayout card = card();
        LinearLayout authorRow = row();
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        View authorAvatar = communityAvatar(post.author, dp(48));
        authorAvatar.setOnClickListener(v -> openCommunityProfile(post.author.userId));
        authorRow.addView(authorAvatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout authorText = new LinearLayout(this);
        authorText.setOrientation(LinearLayout.VERTICAL);
        authorText.setPadding(dp(10), 0, 0, 0);
        LinearLayout nameRow = row();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nickname = body(post.author.nickname);
        nickname.setTypeface(Typeface.DEFAULT_BOLD);
        nickname.setPadding(0, 0, dp(6), 0);
        nickname.setOnClickListener(v -> openCommunityProfile(post.author.userId));
        nameRow.addView(nickname);
        nameRow.addView(communityTierBadge(post.author.tier));
        authorText.addView(nameRow);
        String authorMetaPrefix = (question ? "질문 게시판" : "자유 게시판") + " · " + readableDate(post.createdAt)
                + (question ? " · 조회 " + post.viewCount : "") + " · 팔로워 ";
        TextView authorMeta = label(authorMetaPrefix + post.author.followerCount);
        authorText.addView(authorMeta);
        authorRow.addView(authorText, new LinearLayout.LayoutParams(0, -2, 1));
        if (!isMyAuthor(post.author)) {
            // 팔로우하면 버튼과 함께 이 줄의 팔로워 수도 즉시 따라 바뀌어야 합니다.
            registerFollowRefresher(post.author.userId,
                    () -> authorMeta.setText(authorMetaPrefix + post.author.followerCount));
            authorRow.addView(followToggleButton(post.author, 12), new LinearLayout.LayoutParams(dp(92), dp(38)));
        }
        card.addView(authorRow);

        card.addView(big(post.title));
        card.addView(communityRichBody(post.body));

        LinearLayout badgeRow = row();
        badgeRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        boolean acceptedDone = post.acceptedCommentId != null && !post.acceptedCommentId.isEmpty();
        if (question) {
            TextView status = new TextView(this);
            status.setText(acceptedDone ? "채택 완료" : "답변 대기");
            status.setTextSize(11);
            status.setTypeface(Typeface.DEFAULT_BOLD);
            status.setTextColor(acceptedDone ? Color.parseColor("#1D7A4C") : Color.parseColor("#C24A5A"));
            status.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable statusBg = new GradientDrawable();
            statusBg.setColor(acceptedDone ? Color.parseColor("#E1F5E9") : Color.parseColor("#FBE4E8"));
            statusBg.setCornerRadius(dp(10));
            status.setBackground(statusBg);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
            statusParams.setMargins(0, 0, dp(8), 0);
            badgeRow.addView(status, statusParams);
        }
        if (post.tags != null) {
            for (String tagName : post.tags) {
                badgeRow.addView(communityTagChip("#" + tagName, false, () -> {
                    closeCommunityDetail();
                    switchCommunityTag(tagName);
                }));
            }
        }
        if (badgeRow.getChildCount() > 0) {
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
            badgeParams.setMargins(0, dp(8), 0, dp(2));
            card.addView(badgeRow, badgeParams);
        }
        card.addView(reactionBar("post", post.id, post.reactions));
        content.addView(card);

        int topLevelCount = 0;
        if (post.comments != null) {
            for (ApiModels.CommunityCommentDto comment : post.comments) {
                if (comment.parentId == null || comment.parentId.isEmpty()) topLevelCount++;
            }
        }
        content.addView(sectionTitle(question
                ? "답변 " + topLevelCount + (acceptedDone ? " · 채택 1" : "")
                : "댓글 " + topLevelCount));
        addCommunityDetailComments(post);

        LinearLayout inputRow = row();
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText commentInput = inputField(question ? "답변을 남겨 보세요…" : "댓글을 남겨 보세요…", "");
        inputRow.addView(commentInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button send = primaryButton("➤");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(56), dp(48));
        sendParams.setMargins(dp(8), 0, 0, 0);
        send.setOnClickListener(v -> {
            String value = commentInput.getText().toString().trim();
            if (value.isEmpty()) return;
            send.setEnabled(false);
            executor.execute(() -> {
                try {
                    cloudRepository.createCommunityComment(post.id, value, null);
                    runOnUiThread(this::refreshCommunityDetail);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        send.setEnabled(true);
                        toast((question ? "답변" : "댓글") + " 작성 실패: " + safeMessage(e));
                    });
                }
            });
        });
        inputRow.addView(send, sendParams);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.setMargins(0, dp(10), 0, dp(20));
        content.addView(inputRow, inputParams);
    }

    private void addCommunityDetailComments(ApiModels.CommunityPostDto post) {
        if (post.comments == null || post.comments.isEmpty()) return;
        boolean question = "question".equals(post.category);
        Map<String, ApiModels.CommunityCommentDto> commentById = new LinkedHashMap<>();
        Map<String, List<ApiModels.CommunityCommentDto>> childrenByParent = new LinkedHashMap<>();
        for (ApiModels.CommunityCommentDto comment : post.comments) {
            commentById.put(comment.id, comment);
            String key = comment.parentId == null ? "" : comment.parentId;
            childrenByParent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(comment);
        }
        List<ApiModels.CommunityCommentDto> topLevel = childrenByParent.get("");
        if (topLevel == null) return;

        List<ApiModels.CommunityCommentDto> ordered = new ArrayList<>();
        for (ApiModels.CommunityCommentDto comment : topLevel) {
            if (comment.id.equals(post.acceptedCommentId)) ordered.add(0, comment);
            else ordered.add(comment);
        }

        // 닉네임 문자열 비교는 계정 ID가 다르거나 닉네임 변경 시 오판하므로 계정 기반으로 판별합니다.
        boolean viewerIsAuthor = post.canEdit || isMyAuthor(post.author);
        for (ApiModels.CommunityCommentDto comment : ordered) {
            boolean accepted = comment.id.equals(post.acceptedCommentId);
            LinearLayout holder = new LinearLayout(this);
            holder.setOrientation(LinearLayout.VERTICAL);
            if (accepted) {
                holder.setPadding(dp(8), dp(8), dp(8), dp(8));
                GradientDrawable acceptedBg = new GradientDrawable();
                acceptedBg.setColor(Color.parseColor("#F2FBF5"));
                acceptedBg.setCornerRadius(dp(14));
                acceptedBg.setStroke(dp(2), Color.parseColor("#3EAF6E"));
                holder.setBackground(acceptedBg);
                TextView acceptedLabel = label("✅ 채택된 답변 · 질문자가 선택했어요");
                acceptedLabel.setTextColor(Color.parseColor("#1D7A4C"));
                holder.addView(acceptedLabel);
            }
            addCommentBox(holder, post, comment, null, false);
            if (question && viewerIsAuthor && !accepted && !isMyAuthor(comment.author)) {
                Button accept = outlineButton("✅ 이 답변 채택");
                accept.setTextSize(12);
                accept.setOnClickListener(v -> requestAcceptAnswer(post.id, comment.id));
                LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(-1, dp(40));
                acceptParams.setMargins(0, dp(2), 0, dp(4));
                holder.addView(accept, acceptParams);
            }
            List<ApiModels.CommunityCommentDto> replies = new ArrayList<>();
            collectCommentReplies(comment.id, childrenByParent, replies);
            if (!replies.isEmpty()) {
                LinearLayout replyGroup = new LinearLayout(this);
                replyGroup.setOrientation(LinearLayout.VERTICAL);
                replyGroup.setPadding(dp(24), 0, 0, 0);
                for (ApiModels.CommunityCommentDto reply : replies) {
                    addCommentBox(replyGroup, post, reply, commentById.get(reply.parentId), true);
                }
                holder.addView(replyGroup);
            }
            LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(-1, -2);
            holderParams.setMargins(0, 0, 0, dp(8));
            content.addView(holder, holderParams);
        }
    }

    private void requestAcceptAnswer(String postId, String commentId) {
        new AlertDialog.Builder(this)
                .setTitle("답변 채택")
                .setMessage("이 답변을 채택할까요? 채택 후에도 다른 답변으로 변경할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("채택", (dialog, which) -> executor.execute(() -> {
                    try {
                        ApiModels.CommunityPostDto updated = cloudRepository.acceptCommunityAnswer(postId, commentId);
                        runOnUiThread(() -> {
                            if (communityDetailPost != null && communityDetailPost.id.equals(postId)) {
                                communityDetailPost = updated;
                                if (currentTab == 5) showApp(5);
                            }
                            toast("답변을 채택했습니다.");
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("답변 채택 실패: " + safeMessage(e)));
                    }
                }))
                .show();
    }

    private void addCommentChildren(LinearLayout container, ApiModels.CommunityPostDto post, String parentId, int depth) {
        if (post.comments == null) return;
        Map<String, ApiModels.CommunityCommentDto> commentById = new LinkedHashMap<>();
        Map<String, List<ApiModels.CommunityCommentDto>> childrenByParent = new LinkedHashMap<>();
        for (ApiModels.CommunityCommentDto comment : post.comments) {
            commentById.put(comment.id, comment);
            String key = comment.parentId == null ? "" : comment.parentId;
            childrenByParent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(comment);
        }

        List<ApiModels.CommunityCommentDto> topLevel = childrenByParent.get("");
        if (topLevel == null) return;
        for (ApiModels.CommunityCommentDto comment : topLevel) {
            addCommentBox(container, post, comment, null, false);
            List<ApiModels.CommunityCommentDto> replies = new ArrayList<>();
            collectCommentReplies(comment.id, childrenByParent, replies);
            if (!replies.isEmpty()) {
                Button replyToggle = outlineButton("답글 " + replies.size() + "개 보기");
                LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(40));
                toggleParams.setMargins(dp(24), dp(4), 0, dp(2));
                container.addView(replyToggle, toggleParams);

                LinearLayout replyGroup = new LinearLayout(this);
                replyGroup.setOrientation(LinearLayout.VERTICAL);
                replyGroup.setPadding(dp(24), 0, 0, 0);
                replyGroup.setVisibility(View.GONE);
                for (ApiModels.CommunityCommentDto reply : replies) {
                    ApiModels.CommunityCommentDto replyTarget = commentById.get(reply.parentId);
                    addCommentBox(replyGroup, post, reply, replyTarget, true);
                }
                container.addView(replyGroup);
                replyToggle.setOnClickListener(v -> {
                    boolean hidden = replyGroup.getVisibility() != View.VISIBLE;
                    replyGroup.setVisibility(hidden ? View.VISIBLE : View.GONE);
                    replyToggle.setText(hidden ? "답글 숨기기" : "답글 " + replies.size() + "개 보기");
                });
            }
        }
    }

    private void collectCommentReplies(String parentId,
                                       Map<String, List<ApiModels.CommunityCommentDto>> childrenByParent,
                                       List<ApiModels.CommunityCommentDto> output) {
        List<ApiModels.CommunityCommentDto> children = childrenByParent.get(parentId);
        if (children == null) return;
        for (ApiModels.CommunityCommentDto child : children) {
            output.add(child);
            collectCommentReplies(child.id, childrenByParent, output);
        }
    }

    private void addCommentBox(LinearLayout container, ApiModels.CommunityPostDto post,
                               ApiModels.CommunityCommentDto comment,
                               ApiModels.CommunityCommentDto replyTarget,
                               boolean replyLevel) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(replyLevel ? Color.parseColor("#E9F4F8") : Color.parseColor("#F1F8FA"));
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        View commentAvatar = communityAvatar(comment.author, dp(34));
        commentAvatar.setOnClickListener(v -> openCommunityProfile(comment.author.userId));
        head.addView(commentAvatar, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView name = body(comment.author.nickname);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(dp(8), 0, 0, 0);
        name.setOnClickListener(v -> openCommunityProfile(comment.author.userId));
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TierShieldView commentShield = tierShield(comment.author.tier);
        LinearLayout.LayoutParams commentShieldParams = new LinearLayout.LayoutParams(dp(30), dp(36));
        commentShieldParams.setMargins(dp(4), 0, dp(4), 0);
        head.addView(commentShield, commentShieldParams);
        if (!isMyAuthor(comment.author)) {
            head.addView(followToggleButton(comment.author, 10), new LinearLayout.LayoutParams(dp(80), dp(32)));
        }
        // 수정·삭제·신고·차단은 게시글과 동일하게 댓글 우측 ⋯ 메뉴로 정리합니다.
        TextView commentMenu = body("⋯");
        commentMenu.setTypeface(Typeface.DEFAULT_BOLD);
        commentMenu.setGravity(Gravity.CENTER);
        commentMenu.setContentDescription("댓글 관리 메뉴");
        commentMenu.setOnClickListener(v -> {
            String[] options = comment.canEdit
                    ? new String[]{"수정", "삭제"}
                    : new String[]{"신고", "작성자 차단"};
            new AlertDialog.Builder(this).setItems(options, (dialog, which) -> {
                if (comment.canEdit) {
                    if (which == 0) showEditCommentDialog(comment);
                    else confirmDeleteComment(comment.id);
                } else {
                    if (which == 0) showReportDialog("comment", comment.id);
                    else confirmBlockUser(comment.author.userId, comment.author.nickname);
                }
            }).show();
        });
        LinearLayout.LayoutParams commentMenuParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        commentMenuParams.setMargins(dp(2), 0, 0, 0);
        head.addView(commentMenu, commentMenuParams);
        box.addView(head);
        box.addView(replyLevel && replyTarget != null
                ? replyMentionBody(replyTarget.author.nickname, comment.body)
                : body(comment.body));
        box.addView(label(readableDate(comment.createdAt)));
        box.addView(reactionBar("comment", comment.id, comment.reactions));

        // 답글만 가벼운 텍스트 버튼으로 남깁니다.
        TextView reply = label("답글 쓰기");
        reply.setTextColor(OCEAN);
        reply.setTypeface(Typeface.DEFAULT_BOLD);
        reply.setPadding(dp(4), dp(2), dp(10), dp(4));
        reply.setOnClickListener(v -> showCommunityCommentDialog(
                post.id, comment.id, "@" + comment.author.nickname + "에게 답글"));
        box.addView(reply, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(2));
        container.addView(box, params);
    }

    private TextView replyMentionBody(String nickname, String commentBody) {
        TextView view = body("");
        String mention = "@" + nickname;
        SpannableStringBuilder text = new SpannableStringBuilder(mention + " " + commentBody);
        text.setSpan(new ForegroundColorSpan(Color.parseColor("#2563EB")), 0, mention.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, mention.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
        return view;
    }

    private View reactionBar(String targetType, String targetId, List<ApiModels.ReactionSummary> reactions) {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        // 텍스트 버튼 대신 작은 이모지 버튼: 누르면 이모지 팔레트가 열립니다.
        TextView reaction = new TextView(this);
        reaction.setText("😊＋");
        reaction.setTextSize(14);
        reaction.setGravity(Gravity.CENTER);
        reaction.setContentDescription("공감 이모지 선택");
        GradientDrawable reactionBg = new GradientDrawable();
        reactionBg.setColor(Color.WHITE);
        reactionBg.setCornerRadius(dp(19));
        reactionBg.setStroke(dp(1), Color.parseColor("#BFE0E6"));
        reaction.setBackground(reactionBg);
        reaction.setOnClickListener(v -> showReactionBubble(reaction, targetType, targetId, reactions));
        LinearLayout.LayoutParams reactionParams = new LinearLayout.LayoutParams(dp(54), dp(36));
        reactionParams.setMargins(0, dp(6), dp(6), dp(6));
        bar.addView(reaction, reactionParams);

        HorizontalScrollView summaries = new HorizontalScrollView(this);
        summaries.setHorizontalScrollBarEnabled(false);
        LinearLayout summaryRow = row();
        if (reactions != null) {
            for (ApiModels.ReactionSummary item : reactions) {
                if (item.count <= 0) continue;
                TextView chip = label(item.emoji + " " + item.count);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(9), 0, dp(9), 0);
                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setColor(item.reactedByMe ? Color.parseColor("#DDF7F7") : Color.parseColor("#F1F5F9"));
                chipBg.setCornerRadius(dp(17));
                chipBg.setStroke(dp(1), item.reactedByMe ? OCEAN : Color.parseColor("#CBD5E1"));
                chip.setBackground(chipBg);
                chip.setOnClickListener(v -> showReactionBubble(reaction, targetType, targetId, reactions));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
                params.setMargins(0, dp(8), dp(5), dp(8));
                summaryRow.addView(chip, params);
            }
        }
        summaries.addView(summaryRow);
        bar.addView(summaries, new LinearLayout.LayoutParams(0, dp(50), 1));
        return bar;
    }

    private void showReactionBubble(View anchor, String targetType, String targetId,
                                    List<ApiModels.ReactionSummary> reactions) {
        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🌊"};
        Set<String> selected = new HashSet<>();
        if (reactions != null) {
            for (ApiModels.ReactionSummary item : reactions) if (item.reactedByMe) selected.add(item.emoji);
        }

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(28));
        background.setStroke(dp(1), Color.parseColor("#C7DDE3"));
        bubble.setBackground(background);

        int popupWidth = dp(12 + emojis.length * 36);
        PopupWindow popup = new PopupWindow(bubble, popupWidth, dp(54), true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        for (String emoji : emojis) {
            TextView item = new TextView(this);
            item.setText(emoji);
            item.setTextSize(20);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(emoji + (selected.contains(emoji) ? " 공감 취소" : " 공감"));
            if (selected.contains(emoji)) {
                GradientDrawable selectedBg = new GradientDrawable();
                selectedBg.setColor(Color.parseColor("#DDF7F7"));
                selectedBg.setCornerRadius(dp(21));
                item.setBackground(selectedBg);
            }
            item.setOnClickListener(v -> {
                popup.dismiss();
                toggleReaction(targetType, targetId, emoji);
            });
            bubble.addView(item, new LinearLayout.LayoutParams(dp(36), dp(44)));
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int desiredLeft = Math.max(dp(8), Math.min(location[0] - dp(8), screenWidth - popupWidth - dp(8)));
        popup.showAsDropDown(anchor, desiredLeft - location[0], -anchor.getHeight() - dp(62));
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

    private void openCommunityPostEditScreen(ApiModels.CommunityPostDto post) {
        Intent intent = new Intent(this, CommunityPostActivity.class);
        intent.putExtra(CommunityPostActivity.EXTRA_CATEGORY, post.category);
        intent.putExtra(CommunityPostActivity.EXTRA_POST_ID, post.id);
        intent.putExtra(CommunityPostActivity.EXTRA_POST_TITLE, post.title);
        intent.putExtra(CommunityPostActivity.EXTRA_POST_BODY, post.body);
        intent.putStringArrayListExtra(CommunityPostActivity.EXTRA_POST_TAGS,
                post.tags == null ? new ArrayList<>() : new ArrayList<>(post.tags));
        communityPostLauncher.launch(intent);
    }

    private TextView communityRichBody(String value) {
        TextView view = body("");
        String safe = value == null ? "" : value;
        if (!safe.startsWith(RICH_BODY_MARKER)) {
            view.setText(safe);
            return view;
        }
        Spanned rich = Html.fromHtml(safe.substring(RICH_BODY_MARKER.length()), Html.FROM_HTML_MODE_LEGACY);
        view.setText(rich);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLinksClickable(true);
        return view;
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
        boolean blockedNow = communityProfile != null && communityProfile.isBlocked
                && communityProfile.profile != null && userId.equals(communityProfile.profile.userId);
        new AlertDialog.Builder(this)
                .setTitle(blockedNow ? "차단 해제" : "사용자 차단")
                .setMessage(blockedNow
                        ? nickname + "님의 차단을 해제할까요? 다시 글과 댓글이 표시됩니다."
                        : nickname + "님의 게시글과 댓글을 숨길까요? 다시 누르면 차단을 해제할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton(blockedNow ? "차단 해제" : "차단", (dialog, which) -> executor.execute(() -> {
                    try {
                        boolean blocked = cloudRepository.toggleBlock(userId);
                        runOnUiThread(() -> {
                            toast(blocked ? "사용자를 차단했습니다." : "차단을 해제했습니다.");
                            requestCommunityRefresh();
                            if (userId.equals(communityProfileUserId)) loadCommunityProfile();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast("차단 처리 실패: " + safeMessage(e)));
                    }
                }))
                .show();
    }

    /** Ocean Skill Map 상세 전체 화면: 인터랙티브 맵 + 노드 상세 + 전체 숙련도. */
    private void renderSkillMapScreen() {
        LinearLayout topBar = row();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = sectionTitle("‹ Ocean Skill Map");
        back.setOnClickListener(v -> {
            skillMapOpen = false;
            showApp(6);
        });
        topBar.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(topBar);

        Map<String, Integer> masteryMap = currentSkillMastery();
        Map<String, Integer> evidenceMap = currentSkillEvidence();
        String targetCareer = store.getTargetCareer();
        int readiness = SkillProfileCatalog.careerReadiness(targetCareer, masteryMap);
        int totalEvidence = 0;
        for (String topic : SkillProfileCatalog.TOPICS) totalEvidence += evidenceMap.getOrDefault(topic, 0);
        content.addView(label("목표 진로 " + targetCareer + " · 준비도 " + readiness + "점 · 증거 " + totalEvidence + "개"));

        LinearLayout skillCard = card();
        skillCard.addView(body("퀴즈 문항, 학습 완료와 현장 미션 증거를 분야별 노드로 연결합니다. 노드를 누르면 점수 근거, NCS 역량, 연결 진로와 다음 활동을 확인할 수 있습니다."));
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
        content.addView(skillCard);

        content.addView(sectionTitle("전체 숙련도"));
        LinearLayout masteryCard = card();
        for (String topic : SkillProfileCatalog.TOPICS) addSkillProgress(masteryCard, topic);
        content.addView(masteryCard);
    }

    /** 시안 기준 통합 프로필 편집 전체 화면: 아바타·닉네임·연령대·관심 분야·학습 목적·현재 수준. */
    private void renderProfileEditScreen() {
        UserProfile p = store.getProfile();

        LinearLayout topBar = row();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = sectionTitle("‹ 프로필 편집");
        back.setOnClickListener(v -> {
            profileEditOpen = false;
            showApp(6);
        });
        topBar.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(topBar);

        // 아바타 선택 카드
        LinearLayout avatarCard = card();
        avatarCard.setGravity(Gravity.CENTER_HORIZONTAL);
        String[] icons = {"🌊", "🐳", "⚓", "🐬", "⛵", "🪸", "🐚", "🧭"};
        final String[] selectedEmoji = {store.getAvatarEmoji()};

        TextView preview = new TextView(this);
        preview.setTextSize(38);
        preview.setGravity(Gravity.CENTER);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.OVAL);
        previewBg.setColor(Color.parseColor("#D9F4FF"));
        previewBg.setStroke(dp(2), OCEAN);
        preview.setBackground(previewBg);
        preview.setText(selectedEmoji[0].isEmpty() ? icons[0] : selectedEmoji[0]);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(84), dp(84));
        previewParams.gravity = Gravity.CENTER_HORIZONTAL;
        previewParams.setMargins(0, dp(4), 0, dp(12));
        avatarCard.addView(preview, previewParams);

        HorizontalScrollView emojiScroll = new HorizontalScrollView(this);
        emojiScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout emojiRow = row();
        List<TextView> emojiViews = new ArrayList<>();
        Runnable restyleEmojis = () -> {
            for (TextView view : emojiViews) {
                boolean selected = view.getText().toString().equals(
                        selectedEmoji[0].isEmpty() ? icons[0] : selectedEmoji[0]);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(Color.parseColor("#EFF9F9"));
                bg.setStroke(dp(2), selected ? OCEAN : Color.parseColor("#D8E4E8"));
                view.setBackground(bg);
            }
        };
        for (String icon : icons) {
            TextView emoji = new TextView(this);
            emoji.setText(icon);
            emoji.setTextSize(20);
            emoji.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            emojiParams.setMargins(0, 0, dp(8), 0);
            emoji.setLayoutParams(emojiParams);
            emoji.setOnClickListener(v -> {
                selectedEmoji[0] = icon;
                preview.setText(icon);
                restyleEmojis.run();
            });
            emojiViews.add(emoji);
            emojiRow.addView(emoji);
        }
        restyleEmojis.run();
        emojiScroll.addView(emojiRow);
        avatarCard.addView(emojiScroll, new LinearLayout.LayoutParams(-1, -2));

        Button uploadPhoto = outlineButton("사진 업로드");
        uploadPhoto.setOnClickListener(v -> profileImagePicker.launch(new String[]{"image/jpeg", "image/png", "image/webp"}));
        LinearLayout.LayoutParams uploadParams = new LinearLayout.LayoutParams(dp(140), dp(42));
        uploadParams.gravity = Gravity.CENTER_HORIZONTAL;
        uploadParams.setMargins(0, dp(10), 0, 0);
        avatarCard.addView(uploadPhoto, uploadParams);
        content.addView(avatarCard);

        content.addView(label("닉네임"));
        EditText nicknameField = inputField("", store.getNickname());
        nicknameField.setEnabled(false);
        content.addView(nicknameField, new LinearLayout.LayoutParams(-1, dp(52)));

        final String[] age = {p.ageGroup};
        final String[] interest = {p.interest};
        final String[] goal = {p.goal};
        final String[] level = {p.level};
        content.addView(label("연령대"));
        addChoiceChipRows(new String[]{"초등학생", "중학생", "고등학생", "대학생", "성인", "직장인", "학부모/가족"}, 5, age);
        content.addView(label("관심 분야"));
        addChoiceChipRows(new String[]{"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"}, 4, interest);
        content.addView(label("학습 목적"));
        addChoiceChipRows(new String[]{"흥미", "체험", "진로탐색", "자격증", "직무역량 강화", "가족 교육"}, 4, goal);
        content.addView(label("현재 수준"));
        addChoiceChipRows(new String[]{"입문", "기초", "중급", "심화", "실무"}, 5, level);

        Button saveProfile = primaryButton("프로필 저장");
        saveProfile.setOnClickListener(v -> {
            store.setAvatarEmoji(selectedEmoji[0]);
            store.saveProfile(new UserProfile(age[0], interest[0], goal[0], level[0],
                    RecommendationEngine.persona(age[0], goal[0], interest[0]), p.xp));
            toast("프로필을 저장했습니다.");
            profileEditOpen = false;
            if (store.requiresGuardianConsent() && !store.hasGuardianConsent()) {
                showGuardianConsentDialog(false);
            } else {
                if (viewModel.isCloudConfigured()) viewModel.syncNow();
                showApp(6);
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(52));
        saveParams.setMargins(0, dp(16), 0, dp(24));
        content.addView(saveProfile, saveParams);
    }

    /** 단일 선택 pill 칩 그룹을 perRow개씩 줄바꿈해 content에 추가합니다. */
    private void addChoiceChipRows(String[] options, int perRow, String[] selectedHolder) {
        List<TextView> chips = new ArrayList<>();
        Runnable restyle = () -> {
            for (TextView chip : chips) {
                boolean selected = chip.getText().toString().equals(selectedHolder[0]);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(selected ? NAVY : Color.WHITE);
                bg.setCornerRadius(dp(20));
                if (!selected) bg.setStroke(dp(1), Color.parseColor("#D8E4E8"));
                chip.setBackground(bg);
                chip.setTextColor(selected ? Color.WHITE : TEXT);
                chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
        };
        LinearLayout currentRow = null;
        for (int i = 0; i < options.length; i++) {
            if (i % perRow == 0) {
                currentRow = row();
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(0, 0, 0, dp(8));
                content.addView(currentRow, rowParams);
            }
            String option = options[i];
            TextView chip = new TextView(this);
            chip.setText(option);
            chip.setTextSize(13);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2);
            chipParams.setMargins(0, 0, dp(8), 0);
            chip.setLayoutParams(chipParams);
            chip.setOnClickListener(v -> {
                selectedHolder[0] = option;
                restyle.run();
            });
            chips.add(chip);
            currentRow.addView(chip);
        }
        restyle.run();
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

    /**
     * 알약 모양 팔로우 토글 버튼입니다. 같은 사용자의 버튼이 화면에 여러 개 있어도
     * {@link #followViewRefreshers}에 등록해 두어 한 번의 탭으로 모두 함께 갱신됩니다.
     */
    private Button followToggleButton(ApiModels.ProfileSummary author, float textSize) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(textSize);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        Runnable restyle = () -> styleFollowButton(button, author.isFollowing);
        restyle.run();
        registerFollowRefresher(author.userId, restyle);
        button.setOnClickListener(v -> toggleFollow(author.userId));
        return button;
    }

    private void styleFollowButton(Button button, boolean following) {
        button.setText(following ? "팔로잉" : "＋ 팔로우");
        button.setTextColor(following ? MUTED : Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(following ? Color.WHITE : OCEAN);
        bg.setStroke(dp(1), following ? Color.parseColor("#CBD9DE") : OCEAN);
        button.setBackground(bg);
        // 배경 드로어블을 바꾸면 패딩이 초기화되므로 항상 그 뒤에 다시 지정합니다.
        button.setPadding(dp(10), 0, dp(10), 0);
    }

    /**
     * 작성자가 로그인한 본인인지 판별합니다. userId 비교가 정확하지만, 이번 변경 이전에
     * 로그인해 둔 세션에는 userId가 없으므로 대시보드 동기화 전까지는 닉네임으로 대신 확인합니다.
     */
    private boolean isMyAuthor(ApiModels.ProfileSummary author) {
        if (author == null) return false;
        String myId = store.getAccountUserId();
        if (!myId.isEmpty() && author.userId != null && !author.userId.isEmpty()) {
            return myId.equals(author.userId);
        }
        return store.getNickname().equals(author.nickname);
    }

    private void registerFollowRefresher(String userId, Runnable refresher) {
        if (userId == null || userId.isEmpty()) return;
        List<Runnable> refreshers = followViewRefreshers.get(userId);
        if (refreshers == null) {
            refreshers = new ArrayList<>();
            followViewRefreshers.put(userId, refreshers);
        }
        refreshers.add(refresher);
    }

    private void refreshFollowViews(String userId) {
        List<Runnable> refreshers = followViewRefreshers.get(userId);
        if (refreshers == null) return;
        for (Runnable refresher : new ArrayList<>(refreshers)) refresher.run();
    }

    private boolean isFollowingUser(String userId) {
        ApiModels.ProfileSummary found = findLoadedProfile(userId);
        return found != null && found.isFollowing;
    }

    private ApiModels.ProfileSummary findLoadedProfile(String userId) {
        for (ApiModels.CommunityPostDto post : communityPosts) {
            ApiModels.ProfileSummary hit = findProfileInPost(post, userId);
            if (hit != null) return hit;
        }
        for (ApiModels.CommunityPostDto post : communityProfilePosts) {
            ApiModels.ProfileSummary hit = findProfileInPost(post, userId);
            if (hit != null) return hit;
        }
        ApiModels.ProfileSummary hit = findProfileInPost(communityDetailPost, userId);
        if (hit != null) return hit;
        if (communityProfile != null && communityProfile.profile != null
                && userId.equals(communityProfile.profile.userId)) {
            return communityProfile.profile;
        }
        for (ApiModels.ProfileSummary summary : communityFollowList) {
            if (userId.equals(summary.userId)) return summary;
        }
        return null;
    }

    private ApiModels.ProfileSummary findProfileInPost(ApiModels.CommunityPostDto post, String userId) {
        if (post == null) return null;
        if (post.author != null && userId.equals(post.author.userId)) return post.author;
        if (post.comments != null) {
            for (ApiModels.CommunityCommentDto comment : post.comments) {
                if (comment.author != null && userId.equals(comment.author.userId)) return comment.author;
            }
        }
        return null;
    }

    /** 메모리에 올라와 있는 모든 작성자 정보의 팔로우 상태와 팔로워 수를 한꺼번에 맞춰 줍니다. */
    private void applyFollowState(String userId, boolean following) {
        for (ApiModels.CommunityPostDto post : communityPosts) applyFollowStateToPost(post, userId, following);
        for (ApiModels.CommunityPostDto post : communityProfilePosts) applyFollowStateToPost(post, userId, following);
        applyFollowStateToPost(communityDetailPost, userId, following);
        if (communityProfile != null) applyFollowStateToProfile(communityProfile.profile, userId, following);
        for (ApiModels.ProfileSummary summary : communityFollowList) {
            applyFollowStateToProfile(summary, userId, following);
        }
    }

    private void applyFollowStateToPost(ApiModels.CommunityPostDto post, String userId, boolean following) {
        if (post == null) return;
        applyFollowStateToProfile(post.author, userId, following);
        if (post.comments == null) return;
        for (ApiModels.CommunityCommentDto comment : post.comments) {
            applyFollowStateToProfile(comment.author, userId, following);
        }
    }

    private void applyFollowStateToProfile(ApiModels.ProfileSummary summary, String userId, boolean following) {
        if (summary == null || !userId.equals(summary.userId) || summary.isFollowing == following) return;
        summary.isFollowing = following;
        summary.followerCount = Math.max(0, summary.followerCount + (following ? 1 : -1));
    }

    /**
     * 서버 응답을 기다리지 않고 버튼 상태를 먼저 바꾼 뒤 요청을 보냅니다.
     * 실패하면 이전 상태로 되돌리므로 목록 전체를 다시 불러올 필요가 없습니다.
     */
    private void toggleFollow(String userId) {
        if (userId == null || userId.isEmpty()) return;
        if (store.isMe(userId)) {
            toast("자기 자신은 팔로우할 수 없습니다.");
            return;
        }
        if (!followRequestsInFlight.add(userId)) return;

        boolean next = !isFollowingUser(userId);
        applyFollowState(userId, next);
        store.setFollowingCount(store.getFollowingCount() + (next ? 1 : -1));
        refreshFollowViews(userId);

        executor.execute(() -> {
            try {
                ApiModels.FollowResponse response = cloudRepository.toggleFollow(userId);
                runOnUiThread(() -> {
                    followRequestsInFlight.remove(userId);
                    if (response.following != next) {
                        applyFollowState(userId, response.following);
                        refreshFollowViews(userId);
                    }
                    toast(response.following ? "팔로우했습니다." : "팔로우를 취소했습니다.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    followRequestsInFlight.remove(userId);
                    applyFollowState(userId, !next);
                    store.setFollowingCount(store.getFollowingCount() + (next ? -1 : 1));
                    refreshFollowViews(userId);
                    toast("팔로우 처리 실패: " + safeMessage(e));
                });
            }
        });
    }

    private boolean isCommunityOverlayOpen(int tab) {
        if (communityOverlayTab != tab) return false;
        return !communityProfileUserId.isEmpty() || !communityFollowListUserId.isEmpty();
    }

    /** 팔로우 목록은 프로필 위에 겹쳐 뜨므로, 열려 있으면 목록을 먼저 그립니다. */
    private void renderCommunityOverlay() {
        if (!communityFollowListUserId.isEmpty()) {
            renderCommunityFollowListScreen();
            return;
        }
        renderCommunityProfileScreen();
    }

    private void openCommunityProfile(String userId) {
        if (userId == null || userId.isEmpty()) return;
        if (userId.equals(communityProfileUserId) && communityFollowListUserId.isEmpty()) return;
        communityProfileUserId = userId;
        communityProfile = null;
        communityProfilePosts = new ArrayList<>();
        communityProfileError = "";
        communityProfileLoading = true;
        communityFollowListUserId = "";
        communityFollowList = new ArrayList<>();
        communityOverlayTab = 5;
        showApp(5);
        loadCommunityProfile();
    }

    private void closeCommunityProfile() {
        communityProfileUserId = "";
        communityProfile = null;
        communityProfilePosts = new ArrayList<>();
        communityProfileError = "";
        communityProfileLoading = false;
        if (currentTab == 5) showApp(5);
    }

    private void loadCommunityProfile() {
        final String userId = communityProfileUserId;
        if (userId.isEmpty()) return;
        communityExecutor.execute(() -> {
            try {
                ApiModels.CommunityUserProfileDto profile = cloudRepository.communityUserProfile(userId);
                List<ApiModels.CommunityPostDto> posts = profile.isBlocked
                        ? new ArrayList<>()
                        : cloudRepository.communityPosts("all", "", "", "latest", "all", userId,
                                COMMUNITY_PAGE_SIZE, 0);
                runOnUiThread(() -> {
                    if (!userId.equals(communityProfileUserId)) return;
                    communityProfile = profile;
                    communityProfilePosts = posts == null ? new ArrayList<>() : posts;
                    communityProfileLoading = false;
                    communityProfileError = "";
                    if (currentTab == communityOverlayTab) showApp(currentTab);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!userId.equals(communityProfileUserId)) return;
                    communityProfileLoading = false;
                    communityProfileError = "프로필을 불러오지 못했습니다: " + safeMessage(e);
                    if (currentTab == communityOverlayTab) showApp(currentTab);
                });
            }
        });
    }

    private void renderCommunityProfileScreen() {
        LinearLayout topBar = row();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = sectionTitle("‹ 프로필");
        back.setOnClickListener(v -> closeCommunityProfile());
        topBar.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(topBar);

        if (!communityProfileError.isEmpty()) {
            content.addView(note(communityProfileError, DANGER));
            Button retry = primaryButton("다시 시도");
            retry.setOnClickListener(v -> {
                communityProfileError = "";
                communityProfileLoading = true;
                showApp(5);
                loadCommunityProfile();
            });
            content.addView(retry, new LinearLayout.LayoutParams(-1, dp(48)));
            return;
        }

        if (communityProfile == null || communityProfile.profile == null) {
            LinearLayout loading = card();
            loading.addView(big("프로필을 불러오고 있습니다"));
            loading.addView(new ProgressBar(this));
            content.addView(loading);
            return;
        }

        ApiModels.ProfileSummary author = communityProfile.profile;
        boolean self = communityProfile.isMe || isMyAuthor(author);

        if (!self) {
            TextView menu = sectionTitle("⋯");
            menu.setPadding(dp(10), dp(12), dp(4), dp(8));
            menu.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setItems(new String[]{"신고", communityProfile.isBlocked ? "차단 해제" : "차단"}, (dialog, which) -> {
                        if (which == 0) showReportDialog("user", author.userId);
                        else confirmBlockUser(author.userId, author.nickname);
                    }).show());
            topBar.addView(menu);
        }

        LinearLayout header = card();
        LinearLayout identity = row();
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.addView(communityAvatar(author, dp(64)), new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout identityText = new LinearLayout(this);
        identityText.setOrientation(LinearLayout.VERTICAL);
        identityText.setPadding(dp(12), 0, 0, 0);
        LinearLayout nameRow = row();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nickname = big(author.nickname);
        nickname.setPadding(0, 0, dp(6), 0);
        nameRow.addView(nickname);
        nameRow.addView(communityTierBadge(author.tier));
        identityText.addView(nameRow);
        identityText.addView(label(readableDate(author.joinedAt) + " 가입"));
        identity.addView(identityText, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(identity);

        LinearLayout stats = row();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(profileStatBlock("게시글", communityProfile.postCount, null), profileStatParams());
        TextView followerValue = profileStatValue(author.followerCount);
        stats.addView(profileStatBlock("팔로워", followerValue,
                () -> openFollowList(author.userId, "followers")), profileStatParams());
        stats.addView(profileStatBlock("팔로잉", profileStatValue(author.followingCount),
                () -> openFollowList(author.userId, "following")), profileStatParams());
        registerFollowRefresher(author.userId,
                () -> followerValue.setText(String.valueOf(author.followerCount)));
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.setMargins(0, dp(10), 0, dp(4));
        header.addView(stats, statsParams);

        if (!self && !communityProfile.isBlocked) {
            header.addView(followToggleButton(author, 14), new LinearLayout.LayoutParams(-1, dp(46)));
        }
        content.addView(header);

        if (communityProfile.isBlocked) {
            LinearLayout blockedCard = card();
            blockedCard.addView(big("차단한 사용자입니다"));
            blockedCard.addView(body(author.nickname + "님의 글과 댓글은 목록에 표시되지 않습니다."));
            Button unblock = outlineButton("차단 해제");
            unblock.setOnClickListener(v -> confirmBlockUser(author.userId, author.nickname));
            blockedCard.addView(unblock, new LinearLayout.LayoutParams(-1, dp(44)));
            content.addView(blockedCard);
            return;
        }

        content.addView(sectionTitle("작성한 글 " + communityProfile.postCount));
        if (communityProfilePosts.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(body(self ? "아직 작성한 글이 없습니다." : "아직 공개된 글이 없습니다."));
            content.addView(empty);
            return;
        }
        for (ApiModels.CommunityPostDto post : communityProfilePosts) {
            addCommunityPostCard(content, post);
        }
    }

    private LinearLayout.LayoutParams profileStatParams() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private TextView profileStatValue(int value) {
        TextView view = new TextView(this);
        view.setText(String.valueOf(value));
        view.setTextColor(NAVY);
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout profileStatBlock(String labelText, int value, Runnable onClick) {
        return profileStatBlock(labelText, profileStatValue(value), onClick);
    }

    private LinearLayout profileStatBlock(String labelText, TextView valueView, Runnable onClick) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER);
        block.setPadding(dp(4), dp(6), dp(4), dp(6));
        block.addView(valueView);
        TextView caption = label(labelText);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(2), 0, 0);
        block.addView(caption);
        if (onClick != null) block.setOnClickListener(v -> onClick.run());
        return block;
    }

    private void openFollowList(String userId, String mode) {
        if (userId == null || userId.isEmpty()) {
            toast("계정 정보를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        if (currentTab != 5) {
            // MY 탭에서 연 목록은 커뮤니티 탭에 열려 있던 프로필 위에 겹치면 안 됩니다.
            communityProfileUserId = "";
            communityProfile = null;
            communityProfilePosts = new ArrayList<>();
        }
        communityFollowListUserId = userId;
        communityFollowListMode = mode;
        communityFollowList = new ArrayList<>();
        communityFollowListError = "";
        communityFollowListLoading = true;
        communityOverlayTab = currentTab;
        showApp(currentTab);
        loadFollowList();
    }

    private void closeFollowList() {
        communityFollowListUserId = "";
        communityFollowList = new ArrayList<>();
        communityFollowListError = "";
        communityFollowListLoading = false;
        if (currentTab == communityOverlayTab) showApp(currentTab);
    }

    private void loadFollowList() {
        final String userId = communityFollowListUserId;
        final String mode = communityFollowListMode;
        if (userId.isEmpty()) return;
        communityExecutor.execute(() -> {
            try {
                ApiModels.FollowListResponse response = "following".equals(mode)
                        ? cloudRepository.communityFollowing(userId, 50, 0)
                        : cloudRepository.communityFollowers(userId, 50, 0);
                runOnUiThread(() -> {
                    if (!userId.equals(communityFollowListUserId) || !mode.equals(communityFollowListMode)) return;
                    communityFollowList = response.users == null ? new ArrayList<>() : response.users;
                    communityFollowListLoading = false;
                    communityFollowListError = "";
                    if (currentTab == communityOverlayTab) showApp(currentTab);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!userId.equals(communityFollowListUserId) || !mode.equals(communityFollowListMode)) return;
                    communityFollowListLoading = false;
                    communityFollowListError = "목록을 불러오지 못했습니다: " + safeMessage(e);
                    if (currentTab == communityOverlayTab) showApp(currentTab);
                });
            }
        });
    }

    private void renderCommunityFollowListScreen() {
        boolean following = "following".equals(communityFollowListMode);
        TextView back = sectionTitle("‹ " + (following ? "팔로잉" : "팔로워"));
        back.setOnClickListener(v -> closeFollowList());
        content.addView(back);

        if (!communityFollowListError.isEmpty()) {
            content.addView(note(communityFollowListError, DANGER));
            Button retry = primaryButton("다시 시도");
            retry.setOnClickListener(v -> {
                communityFollowListError = "";
                communityFollowListLoading = true;
                showApp(currentTab);
                loadFollowList();
            });
            content.addView(retry, new LinearLayout.LayoutParams(-1, dp(48)));
            return;
        }

        if (communityFollowListLoading) {
            LinearLayout loading = card();
            loading.addView(big("목록을 불러오고 있습니다"));
            loading.addView(new ProgressBar(this));
            content.addView(loading);
            return;
        }

        if (communityFollowList.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(big(following ? "팔로우한 사용자가 없습니다" : "아직 팔로워가 없습니다"));
            empty.addView(body(following
                    ? "커뮤니티에서 마음에 드는 작성자를 팔로우해 보세요."
                    : "꾸준히 글과 답변을 남기면 팔로워가 늘어납니다."));
            content.addView(empty);
            return;
        }

        for (ApiModels.ProfileSummary person : communityFollowList) {
            content.addView(followListRow(person));
        }
    }

    private LinearLayout followListRow(ApiModels.ProfileSummary person) {
        LinearLayout rowCard = card();
        LinearLayout personRow = row();
        personRow.setGravity(Gravity.CENTER_VERTICAL);
        personRow.addView(communityAvatar(person, dp(44)), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout personText = new LinearLayout(this);
        personText.setOrientation(LinearLayout.VERTICAL);
        personText.setPadding(dp(10), 0, dp(8), 0);
        LinearLayout nameRow = row();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nickname = body(person.nickname);
        nickname.setTypeface(Typeface.DEFAULT_BOLD);
        nickname.setPadding(0, 0, dp(6), 0);
        nameRow.addView(nickname);
        nameRow.addView(communityTierBadge(person.tier));
        personText.addView(nameRow);
        TextView followerLine = label("팔로워 " + person.followerCount);
        registerFollowRefresher(person.userId,
                () -> followerLine.setText("팔로워 " + person.followerCount));
        personText.addView(followerLine);
        personRow.addView(personText, new LinearLayout.LayoutParams(0, -2, 1));
        if (!isMyAuthor(person)) {
            personRow.addView(followToggleButton(person, 11), new LinearLayout.LayoutParams(dp(88), dp(34)));
        }
        rowCard.addView(personRow);
        rowCard.setOnClickListener(v -> openCommunityProfile(person.userId));
        return rowCard;
    }

    private TextView myFollowCountView(String text, String mode) {
        TextView view = new TextView(this);
        view.setText(text + " ›");
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(5), dp(14), 0);
        view.setOnClickListener(v -> openFollowList(store.getAccountUserId(), mode));
        return view;
    }

    private void renderMyPage() {
        if (isCommunityOverlayOpen(6)) {
            renderCommunityOverlay();
            return;
        }
        if (profileEditOpen) {
            renderProfileEditScreen();
            return;
        }
        if (skillMapOpen) {
            renderSkillMapScreen();
            return;
        }
        UserProfile p = store.getProfile();
        String tier = store.getTier();

        // 시안 기준 컴팩트 프로필 카드: 네이비 배경 + 편집 칩 + XP 진행바
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.VERTICAL);
        profileCard.setBackgroundResource(R.drawable.bg_ocean_header);
        profileCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams profileCardParams = new LinearLayout.LayoutParams(-1, -2);
        profileCardParams.setMargins(0, 0, 0, dp(10));
        profileCard.setLayoutParams(profileCardParams);

        LinearLayout profileTop = row();
        profileTop.setGravity(Gravity.CENTER_VERTICAL);
        profileTop.addView(profileAvatar(store.getNickname(), store.getProfileImageUrl(), dp(56)), new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        profileText.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout nameRow = row();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView myName = new TextView(this);
        myName.setText(store.getNickname());
        myName.setTextColor(Color.WHITE);
        myName.setTextSize(18);
        myName.setTypeface(Typeface.DEFAULT_BOLD);
        myName.setPadding(0, 0, dp(8), 0);
        nameRow.addView(myName);
        nameRow.addView(communityTierBadge(plainTierText(tier)));
        profileText.addView(nameRow);
        TextView myMeta = new TextView(this);
        myMeta.setText(p.ageGroup + " · " + p.interest + " · " + p.goal);
        myMeta.setTextColor(Color.parseColor("#C9FFFF"));
        myMeta.setTextSize(12);
        myMeta.setPadding(0, dp(3), 0, 0);
        profileText.addView(myMeta);
        LinearLayout followRow = row();
        followRow.setGravity(Gravity.CENTER_VERTICAL);
        followRow.addView(myFollowCountView("팔로워 " + store.getFollowerCount(), "followers"));
        followRow.addView(myFollowCountView("팔로잉 " + store.getFollowingCount(), "following"));
        profileText.addView(followRow);
        profileTop.addView(profileText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView editProfile = new TextView(this);
        editProfile.setText("편집");
        editProfile.setTextSize(13);
        editProfile.setTypeface(Typeface.DEFAULT_BOLD);
        editProfile.setTextColor(Color.WHITE);
        editProfile.setGravity(Gravity.CENTER);
        GradientDrawable editBg = new GradientDrawable();
        editBg.setColor(Color.parseColor("#267B91"));
        editBg.setCornerRadius(dp(16));
        editProfile.setBackground(editBg);
        editProfile.setOnClickListener(v -> {
            profileEditOpen = true;
            showApp(6);
        });
        profileTop.addView(editProfile, new LinearLayout.LayoutParams(dp(58), dp(32)));
        profileCard.addView(profileTop);

        int xp = p.xp;
        int base = UserStore.tierBaseXp(tier);
        int next = UserStore.nextTierXp(tier);
        int progress = "다이아".equals(tier)
                ? 100
                : Math.min(100, Math.max(0, (xp - base) * 100 / Math.max(1, next - base)));
        LinearLayout xpRow = row();
        xpRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView xpText = new TextView(this);
        xpText.setText("XP " + xp + ("다이아".equals(tier) ? " · 최고 티어"
                : " · " + plainTierText(PromotionRules.nextTier(tier)) + "까지 " + Math.max(0, next - xp)));
        xpText.setTextColor(Color.WHITE);
        xpText.setTextSize(13);
        xpText.setTypeface(Typeface.DEFAULT_BOLD);
        xpRow.addView(xpText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView xpPercent = new TextView(this);
        xpPercent.setText(progress + "%");
        xpPercent.setTextColor(CYAN);
        xpPercent.setTextSize(13);
        xpPercent.setTypeface(Typeface.DEFAULT_BOLD);
        xpRow.addView(xpPercent);
        LinearLayout.LayoutParams xpRowParams = new LinearLayout.LayoutParams(-1, -2);
        xpRowParams.setMargins(0, dp(12), 0, 0);
        profileCard.addView(xpRow, xpRowParams);
        ProgressBar xpBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        xpBar.setMax(100);
        xpBar.setProgress(progress);
        xpBar.setProgressTintList(ColorStateList.valueOf(CYAN));
        xpBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B6983")));
        LinearLayout.LayoutParams xpBarParams = new LinearLayout.LayoutParams(-1, dp(7));
        xpBarParams.setMargins(0, dp(6), 0, 0);
        profileCard.addView(xpBar, xpBarParams);
        content.addView(profileCard);

        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "검증 완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getBookmarks().size()), "찜"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "응시"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        addActivityHistorySection();

        Map<String, Integer> masteryMap = currentSkillMastery();
        Map<String, Integer> evidenceMap = currentSkillEvidence();
        LinearLayout passportHead = row();
        passportHead.setGravity(Gravity.CENTER_VERTICAL);
        passportHead.addView(sectionTitle("Ocean Skill Passport"), new LinearLayout.LayoutParams(0, -2, 1));
        TextView openMap = label("자세히보기 ›");
        openMap.setTextColor(OCEAN);
        openMap.setTextSize(14);
        openMap.setPadding(dp(12), dp(10), dp(2), dp(8));
        openMap.setOnClickListener(v -> {
            skillMapOpen = true;
            showApp(6);
        });
        passportHead.addView(openMap);
        content.addView(passportHead);

        // 요약 카드: 준비도·강점·보강 팁만 보여주고, 자세한 내용은 스킬 맵 화면에서 확인
        LinearLayout skillCard = card();
        String targetCareer = store.getTargetCareer();
        int readiness = SkillProfileCatalog.careerReadiness(targetCareer, masteryMap);
        LinearLayout summaryRow = row();
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        OceanSkillMapView miniMap = new OceanSkillMapView(this);
        miniMap.setSkillData(masteryMap, evidenceMap);
        miniMap.setCompactScale(0.5f);
        summaryRow.addView(miniMap, new LinearLayout.LayoutParams(dp(175), dp(170)));
        LinearLayout summaryText = new LinearLayout(this);
        summaryText.setOrientation(LinearLayout.VERTICAL);
        summaryText.setPadding(dp(10), 0, 0, 0);
        TextView score = huge(readiness + "점");
        score.setPadding(0, 0, 0, 0);
        summaryText.addView(score);
        summaryText.addView(label(targetCareer + " 준비도"));
        String strongTopic = "";
        String weakTopic = "";
        int strongScore = -1;
        int weakScore = Integer.MAX_VALUE;
        for (String topic : SkillProfileCatalog.TOPICS) {
            int value = masteryMap.getOrDefault(topic, 50);
            if (value > strongScore) { strongScore = value; strongTopic = topic; }
            if (value < weakScore) { weakScore = value; weakTopic = topic; }
        }
        summaryText.addView(note(strongTopic + " " + strongScore + " 강점", SUCCESS));
        int predicted = SkillProfileCatalog.predictedNextScore(weakScore, evidenceMap.getOrDefault(weakTopic, 0));
        if (predicted > weakScore) {
            summaryText.addView(note(weakTopic + " 보강 시 +" + (predicted - weakScore) + "점", OCEAN));
        }
        summaryRow.addView(summaryText, new LinearLayout.LayoutParams(0, -2, 1));
        skillCard.addView(summaryRow);
        miniMap.setOnSkillSelectedListener(topic -> {
            skillMapOpen = true;
            showApp(6);
        });
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
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("로그아웃할까요?")
                .setMessage("로그아웃하면 로그인 화면으로 돌아갑니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (dialog, which) -> viewModel.logout())
                .show());
        cloudCard.addView(logout);
        content.addView(cloudCard);

        content.addView(sectionTitle("학습 알림 & 캘린더"));
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

    private LinearLayout addRouteDetailsExpandable(LinearLayout parent) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutTransition(new LayoutTransition());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(8), 0, dp(4));
        TextView titleView = label(routeDetailsExpanded ? "접기" : "상세 항로 보기");
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = new TextView(this);
        arrow.setText(routeDetailsExpanded ? "▴" : "▾");
        arrow.setTextColor(OCEAN);
        arrow.setTextSize(16);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(routeDetailsExpanded ? View.VISIBLE : View.GONE);

        header.setOnClickListener(v -> {
            routeDetailsExpanded = panel.getVisibility() != View.VISIBLE;
            panel.setVisibility(routeDetailsExpanded ? View.VISIBLE : View.GONE);
            titleView.setText(routeDetailsExpanded ? "접기" : "상세 항로 보기");
            arrow.setText(routeDetailsExpanded ? "▴" : "▾");
        });

        wrapper.addView(header);
        wrapper.addView(panel);
        parent.addView(wrapper);
        return panel;
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
        if (!store.isContentStarted(item.id)) {
            toast("먼저 앱 내 검증 플레이어에서 영상 학습을 시작해 주세요.");
            return;
        }
        if (!store.hasVerifiedVideoCompletion(item.id, item.minutes)) {
            toast("실제 재생 기준 70% 이상과 최소 학습 시간을 충족해야 합니다. 현재 "
                    + store.getVideoProgressPercent(item.id) + "% · " + store.getVideoWatchSeconds(item.id) + "초입니다.");
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(body("서버가 중복을 제거한 실제 시청 구간을 검증했습니다. 영상에서 배운 핵심 내용을 한 문장 이상 기록하면 완료 소감으로 저장됩니다."));
        EditText reflection = inputField("핵심 내용 또는 새롭게 알게 된 점", "");
        reflection.setSingleLine(false);
        reflection.setMinLines(3);
        form.addView(reflection);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("학습 완료 인증")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("완료 인증", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = reflection.getText().toString().trim();
            if (value.length() < 10) {
                reflection.setError("10자 이상으로 핵심 내용을 작성해 주세요.");
                return;
            }
            store.markCompleted(item.id);
            store.saveContentReflection(item.id, value);
            viewModel.recordLearning("video", item.id, item.title, "completed_with_reflection");
            recordRouteCompletionByTarget(item.id);
            dialog.dismiss();
            toast("서버 검증 학습에 완료 소감을 저장했습니다.");
            showApp(currentTab);
        }));
        dialog.show();
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

    /** 찜 토글 직후 학습 헤더의 ❤ 개수를 갱신합니다. */
    private void refreshHeaderWishCount() {
        if (headerWishButton == null) return;
        int wishCount = store.getBookmarks().size();
        headerWishButton.setText("❤ " + wishCount);
        headerWishButton.setContentDescription("찜 목록 열기 · " + wishCount + "개");
    }

    /** 헤더 ❤ 버튼: 찜한 항목 목록을 보여주고, 선택하면 해당 자료로 이동합니다. */
    private void showBookmarkListDialog() {
        final List<String> ids = new ArrayList<>(store.getBookmarks());
        if (ids.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("찜 목록")
                    .setMessage("아직 찜한 항목이 없습니다.\n학습 자료나 일정 카드의 ♡ 버튼으로 저장할 수 있어요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        String[] titles = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) titles[i] = displayNameForId(ids.get(i));
        new AlertDialog.Builder(this)
                .setTitle("찜 목록 · " + ids.size() + "개")
                .setItems(titles, (dialog, which) -> openBookmarkedItem(ids.get(which)))
                .setNegativeButton("닫기", null)
                .show();
    }

    /** 찜 목록에서 선택한 항목을 종류에 맞게 엽니다. (영상=검증 플레이어, 논문=원문, 일정=일정 화면) */
    private void openBookmarkedItem(String id) {
        for (ContentItem item : DataRepository.contents()) {
            if (item.id.equals(id)) { openVerifiedContent(item); return; }
        }
        for (PaperItem item : DataRepository.papers()) {
            if (item.id.equals(id)) { openUrl(item.url); return; }
        }
        for (ProgramItem item : DataRepository.programs()) {
            if (item.id.equals(id)) { showApp(3); return; }
        }
        for (EventItem item : DataRepository.events()) {
            if (item.id.equals(id)) { showApp(3); return; }
        }
        toast("연결된 자료를 찾지 못했습니다.");
    }

    private void addContentCard(ContentItem item, boolean compact) {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        int score = RecommendationEngine.scoreContent(item, p, tier, store);
        boolean completed = store.getCompletedContentIds().contains(item.id);
        boolean started = store.isContentStarted(item.id);
        LinearLayout card = card();
        card.setLayoutTransition(new LayoutTransition());

        String learningState = completed
                ? "학습 완료"
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
        title.setTextSize(compact ? 13.5f : 15);
        summaryCopy.addView(title);
        summaryCopy.addView(body((item.minutes > 0 ? "영상 " + item.minutes + "분" : "영상") + " · 적합도 " + score));
        summaryRow.addView(summaryCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView bookmarkHeart = new TextView(this);
        bookmarkHeart.setTextSize(compact ? 20 : 24);
        bookmarkHeart.setGravity(Gravity.CENTER);
        bookmarkHeart.setClickable(true);
        bookmarkHeart.setFocusable(true);
        updateBookmarkHeart(bookmarkHeart, store.isBookmarked(item.id), item.title);
        bookmarkHeart.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            boolean bookmarked = store.isBookmarked(item.id);
            updateBookmarkHeart(bookmarkHeart, bookmarked, item.title);
            refreshHeaderWishCount();
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
        details.addView(body("출처: " + item.source));
        details.addView(body("분야: " + item.topic + " · 연결 진로: " + item.careerTag));
        addReasonList(details, RecommendationEngine.contentReasons(item, p, tier, store));
        if (completed) {
            details.addView(note("학습 완료 인증 · XP 반영됨", SUCCESS));
        } else if (started) {
            details.addView(note("검증 시청 " + store.getVideoProgressPercent(item.id) + "% · "
                    + store.getVideoWatchSeconds(item.id) + "초 · 기준 충족 후 핵심 내용을 제출하세요.", OCEAN));
        } else {
            details.addView(note("앱 내 검증 플레이어에서 시청 기록을 쌓은 뒤 학습 완료를 인증할 수 있습니다.", MUTED));
        }

        Button watch = primaryButton(started ? "영상 계속 보기" : "영상 학습 시작");
        watch.setOnClickListener(v -> {
            store.markContentStarted(item.id);
            viewModel.recordLearning("video", item.id, item.title, "started");
            Intent verified = new Intent(this, VerifiedVideoActivity.class);
            verified.putExtra(VerifiedVideoActivity.EXTRA_CONTENT_ID, item.id);
            verified.putExtra(VerifiedVideoActivity.EXTRA_TITLE, item.title);
            verified.putExtra(VerifiedVideoActivity.EXTRA_URL, item.url);
            verified.putExtra(VerifiedVideoActivity.EXTRA_MINUTES, item.minutes);
            startActivity(verified);
        });
        details.addView(watch, new LinearLayout.LayoutParams(-1, dp(44)));

        if (started && !completed) {
            Button complete = outlineButton("학습 완료 인증");
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

    private TextView tierChip(String tier) {
        String value = plainTierText(tier);
        TextView chip = new TextView(this);
        chip.setText(value);
        chip.setTextSize(currentTab == 0 ? 10 : 11);
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

    private TextView bookmarkHeart(String itemId, String title, String recordType) {
        TextView heart = new TextView(this);
        heart.setTextSize(currentTab == 0 ? 20 : 24);
        heart.setGravity(Gravity.CENTER);
        heart.setClickable(true);
        heart.setFocusable(true);
        updateBookmarkHeart(heart, store.isBookmarked(itemId), title);
        heart.setOnClickListener(v -> {
            store.toggleBookmark(itemId);
            boolean bookmarked = store.isBookmarked(itemId);
            updateBookmarkHeart(heart, bookmarked, title);
            refreshHeaderWishCount();
            viewModel.recordLearning(recordType, itemId, title, bookmarked ? "saved" : "removed");
            toast(bookmarked ? "찜 목록에 저장했습니다." : "찜을 해제했습니다.");
        });
        return heart;
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
        LinearLayout paperTitleRow = row();
        paperTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        paperTitleRow.addView(big("▤ " + item.title), new LinearLayout.LayoutParams(0, -2, 1));
        paperTitleRow.addView(bookmarkHeart(item.id, item.title, "bookmark"),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(paperTitleRow);
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
        actions.addView(open, new LinearLayout.LayoutParams(-1, dp(44)));
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
        card.setLayoutTransition(new LayoutTransition());
        card.addView(label(item.topic + " · 추천 " + score + "점 · " + status));
        LinearLayout programTitleRow = row();
        programTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        programTitleRow.addView(big("📚 " + item.title), new LinearLayout.LayoutParams(0, -2, 1));
        programTitleRow.addView(bookmarkHeart(item.id, item.title, "program"),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(programTitleRow);
        card.addView(body(item.startDate + " ~ " + item.endDate + " · " + item.target + " · " + item.method));

        TextView detailToggle = label("자세히 보기 ▾");
        detailToggle.setTextColor(OCEAN);
        detailToggle.setGravity(Gravity.CENTER_VERTICAL);
        detailToggle.setPadding(dp(2), dp(10), dp(2), dp(8));
        detailToggle.setClickable(true);
        detailToggle.setFocusable(true);
        detailToggle.setContentDescription(item.title + " 상세 정보 펼치기");
        card.addView(detailToggle);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.addView(body("시간대 " + item.timezone
                + (item.capacity > 0 ? " · 정원 " + item.capacity + "명" : "")
                + (item.waitlistAvailable ? " · 대기 신청 가능" : "")
                + (item.applicationDeadline.isEmpty() ? "" : " · 신청 마감 " + item.applicationDeadline)));
        if (!item.source.trim().isEmpty()) details.addView(body("출처: " + item.source));
        details.addView(body(item.description));
        addReasonList(details, RecommendationEngine.programReasons(item, p, store));
        if (archived) details.addView(note("현재 신청 가능한 일정이 아니라 교육 이력과 수요 분석을 위한 아카이브 자료입니다.", MUTED));
        if (!archived && !item.applicationUrl.trim().isEmpty()) {
            Button apply = primaryButton("공식 신청 페이지 열기");
            apply.setOnClickListener(v -> openUrl(item.applicationUrl));
            details.addView(apply);
        }
        if (!archived && !"데이터 확인 필요".equals(status)) {
            Button calendar = outlineButton("내 캘린더에 추가");
            calendar.setOnClickListener(v -> addProgramToCalendar(item));
            details.addView(calendar);
        }
        if (store.hasCloudSession()) {
            Button participation = outlineButton("신청·참석·수료 기록");
            participation.setOnClickListener(v -> showProgramParticipationDialog(item));
            details.addView(participation);
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

    private void addEventCard(EventItem item) {
        String status = RecommendationEngine.scheduleStatus(item.startDate, item.endDate);
        LinearLayout card = card();
        card.addView(label(item.category + " · " + item.target + " · " + status));
        LinearLayout eventTitleRow = row();
        eventTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        eventTitleRow.addView(big("🎪 " + item.title), new LinearLayout.LayoutParams(0, -2, 1));
        eventTitleRow.addView(bookmarkHeart(item.id, item.title, "event"),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(eventTitleRow);
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
        // 키보드가 올라오면 하단 고정 입력바(채팅 등)가 함께 올라오도록 리사이즈 모드 사용
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        return field;
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(NAVY);
        v.setTextSize(currentTab == 0 ? 18 : 20);
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
        v.setTextSize(currentTab == 0 ? 15 : 17);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(2), 0, dp(6));
        return v;
    }

    private TextView body(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(TEXT);
        v.setTextSize(currentTab == 0 ? 12.5f : 14);
        v.setLineSpacing(dp(2), 1.05f);
        v.setPadding(0, dp(4), 0, dp(6));
        return v;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(tierText(text));
        v.setTextColor(MUTED);
        v.setTextSize(currentTab == 0 ? 11 : 12);
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
        b.setTextSize(currentTab == 0 ? 13 : 14);
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
        b.setTextSize(currentTab == 0 ? 12 : 13);
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

    private void addAiSearchBox(String resourceType, String hint, boolean loading, ApiModels.AiSearchResponse response) {
        // 시안 기준 컴팩트 검색바: 🔍 + 한 줄 입력 + 우측 'AI 검색' 알약 버튼.
        LinearLayout searchBar = row();
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.WHITE);
        barBg.setCornerRadius(dp(24));
        barBg.setStroke(dp(1), Color.parseColor("#D7E7EC"));
        searchBar.setBackground(barBg);
        searchBar.setElevation(dp(2));

        TextView icon = new TextView(this);
        icon.setText("🔍");
        icon.setTextSize(15);
        icon.setGravity(Gravity.CENTER);
        searchBar.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(40)));

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(2), 0, dp(6), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !loading) {
                requestAiSearch(resourceType, input.getText().toString());
                return true;
            }
            return false;
        });
        searchBar.addView(input, new LinearLayout.LayoutParams(0, dp(44), 1));

        TextView search = new TextView(this);
        search.setText(loading ? "검색 중…" : "AI 검색");
        search.setTextSize(12);
        search.setTypeface(Typeface.DEFAULT_BOLD);
        search.setTextColor(Color.WHITE);
        search.setGravity(Gravity.CENTER);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(loading ? MUTED : NAVY);
        searchBg.setCornerRadius(dp(18));
        search.setBackground(searchBg);
        if (!loading) search.setOnClickListener(v -> requestAiSearch(resourceType, input.getText().toString()));
        searchBar.addView(search, new LinearLayout.LayoutParams(dp(66), dp(36)));

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2);
        barParams.setMargins(0, dp(2), 0, dp(10));
        content.addView(searchBar, barParams);

        if (loading) content.addView(new ProgressBar(this));
        if (response != null) {
            content.addView(note(response.usedLiveWeb ? "앱 자료와 실시간 웹 근거를 함께 검토했습니다." : "앱에 등록된 자료를 기준으로 검색했습니다.", OCEAN));
        }
    }

    /**
     * Calls the configured LLM client without binding MainActivity to one historical
     * answerAgent overload. Some project revisions expose three, four, or five
     * parameters; this adapter supplies every supported context value by type.
     */
    private String invokeAgentAnswerCompat(String question, UserProfile profile) throws Exception {
        List<ContentItem> recommendations = RecommendationEngine.recommendedContents(
                profile, store.getTier(), store);
        String promotionManual = PromotionRules.fullManualPlain();
        java.lang.reflect.Method selected = null;
        Object[] selectedArguments = null;

        for (java.lang.reflect.Method method : llmClient.getClass().getMethods()) {
            if (!"answerAgent".equals(method.getName())
                    || !String.class.isAssignableFrom(method.getReturnType())) continue;
            Object[] arguments = buildAgentArguments(
                    method.getParameterTypes(), question, profile, recommendations, promotionManual);
            if (arguments == null) continue;
            if (selected == null || method.getParameterTypes().length > selected.getParameterTypes().length) {
                selected = method;
                selectedArguments = arguments;
            }
        }

        if (selected == null) {
            throw new NoSuchMethodException("지원 가능한 answerAgent 메서드를 찾지 못했습니다.");
        }
        Object result = invokeReflective(selected, llmClient, selectedArguments);
        return result == null ? "" : result.toString();
    }

    private Object[] buildAgentArguments(Class<?>[] parameterTypes, String question,
                                         UserProfile profile, List<ContentItem> recommendations,
                                         String promotionManual) {
        Object[] arguments = new Object[parameterTypes.length];
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == String.class) {
                if (stringIndex == 0) arguments[i] = question;
                else if (stringIndex == 1) arguments[i] = store.getTier();
                else if (stringIndex == 2) arguments[i] = promotionManual;
                else arguments[i] = "";
                stringIndex++;
            } else if (type.isAssignableFrom(UserProfile.class)) {
                arguments[i] = profile;
            } else if (List.class.isAssignableFrom(type)) {
                arguments[i] = recommendations;
            } else {
                return null;
            }
        }
        return arguments;
    }

    /**
     * Supports repository revisions where aiSearch accepts only a query, a query
     * plus resource type, or an additional numeric result limit.
     */
    private ApiModels.AiSearchResponse invokeAiSearchCompat(String query, String resourceType) throws Exception {
        java.lang.reflect.Method selected = null;
        Object[] selectedArguments = null;

        for (java.lang.reflect.Method method : cloudRepository.getClass().getMethods()) {
            if (!"aiSearch".equals(method.getName())
                    || !ApiModels.AiSearchResponse.class.isAssignableFrom(method.getReturnType())) continue;
            Object[] arguments = buildAiSearchArguments(method.getParameterTypes(), query, resourceType);
            if (arguments == null) continue;
            if (selected == null || method.getParameterTypes().length > selected.getParameterTypes().length) {
                selected = method;
                selectedArguments = arguments;
            }
        }

        if (selected == null) {
            throw new NoSuchMethodException("지원 가능한 aiSearch 메서드를 찾지 못했습니다.");
        }
        Object result = invokeReflective(selected, cloudRepository, selectedArguments);
        return (ApiModels.AiSearchResponse) result;
    }

    private Object[] buildAiSearchArguments(Class<?>[] parameterTypes, String query, String resourceType) {
        Object[] arguments = new Object[parameterTypes.length];
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == String.class) {
                if (stringIndex == 0) arguments[i] = query;
                else if (stringIndex == 1) arguments[i] = resourceType;
                else arguments[i] = "";
                stringIndex++;
            } else if (type == int.class || type == Integer.class) {
                arguments[i] = 12;
            } else if (type == long.class || type == Long.class) {
                arguments[i] = 12L;
            } else if (type == boolean.class || type == Boolean.class) {
                arguments[i] = false;
            } else {
                return null;
            }
        }
        return arguments;
    }

    private Object invokeReflective(java.lang.reflect.Method method, Object receiver,
                                    Object[] arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause == null ? e : cause);
        }
    }

    private void requestAiSearch(String resourceType, String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            toast("찾고 싶은 자료를 입력해 주세요.");
            return;
        }
        boolean schedule = "schedule".equals(resourceType);
        if (schedule) scheduleSearchLoading = true; else learningSearchLoading = true;
        showApp(schedule ? 3 : 1);
        executor.execute(() -> {
            try {
                ApiModels.AiSearchResponse result = invokeAiSearchCompat(value, resourceType);
                runOnUiThread(() -> {
                    if (schedule) {
                        scheduleSearchResponse = result;
                        scheduleSearchLoading = false;
                        showApp(3);
                    } else {
                        learningSearchResponse = result;
                        learningSearchLoading = false;
                        showApp(1);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (schedule) scheduleSearchLoading = false; else learningSearchLoading = false;
                    toast("AI 검색 실패: " + safeMessage(e));
                    showApp(schedule ? 3 : 1);
                });
            }
        });
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

    /**
     * 커뮤니티에서 쓰는 아바타입니다. 내가 고른 이모지 아바타는 내 계정에만 적용하고,
     * 다른 사용자에게는 닉네임에서 뽑은 기본 아이콘을 보여 줍니다.
     */
    private View communityAvatar(ApiModels.ProfileSummary author, int size) {
        if (author == null) return profileAvatar("", "", size);
        return profileAvatar(author.nickname, author.profileImageUrl, size, isMyAuthor(author));
    }

    private View profileAvatar(String nickname, String imageUrl, int size) {
        return profileAvatar(nickname, imageUrl, size, true);
    }

    private View profileAvatar(String nickname, String imageUrl, int size, boolean self) {
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(imageUrl).circleCrop().into(image);
            return image;
        }
        TextView fallback = new TextView(this);
        String name = nickname == null || nickname.trim().isEmpty() ? "B" : nickname.trim();
        String[] icons = {"🌊", "🐳", "⚓", "🐬", "⛵", "🪸", "🐚", "🧭"};
        String chosen = self ? store.getAvatarEmoji() : "";
        fallback.setText(chosen.isEmpty() ? icons[(name.hashCode() & 0x7fffffff) % icons.length] : chosen);
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
