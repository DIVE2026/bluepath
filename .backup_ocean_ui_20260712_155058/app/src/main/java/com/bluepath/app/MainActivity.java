package com.bluepath.app;

import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.storage.UserStore;
import com.bluepath.app.util.MarineLlmClient;
import com.bluepath.app.util.NotificationHelper;
import com.bluepath.app.util.PromotionRules;
import com.bluepath.app.util.RecommendationEngine;
import com.bluepath.app.view.OceanGraphicView;
import com.bluepath.app.viewmodel.BluePathViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout content;
    private FrameLayout appRoot;
    private LinearLayout sidebar;
    private View sidebarScrim;
    private int currentTab = 0;

    private List<QuizQuestion> activeQuiz = new ArrayList<>();
    private int[] selectedAnswers = new int[0];
    private boolean quizGenerating = false;
    private boolean quizSubmitted = false;
    private String quizAttemptTier = "";
    private String quizSource = "";
    private String quizNotice = "";
    private int quizCorrect = 0;
    private int quizAwardedXp = 0;

    private boolean agentLoading = false;
    private String agentLastAnswer = "질문을 입력하면 해양 AI가 학습·승급·진로 경로를 안내합니다. 온라인 답변에는 근거 자료가 함께 표시됩니다.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DataRepository.initialize(this);
        store = new UserStore(this);
        llmClient = new MarineLlmClient(store);
        viewModel = new ViewModelProvider(this).get(BluePathViewModel.class);
        viewModel.operation().observe(this, state -> {
            if (state == null || "처리 중…".equals(state.message)) return;
            toast(state.message);
            if (!state.success) return;
            if ("login".equals(state.type) || "register".equals(state.type)) {
                if (store.hasProfile()) {
                    showApp(0);
                    if (viewModel.isCloudConfigured()) viewModel.refreshCatalog();
                } else {
                    showOnboarding();
                }
                return;
            }
            if ("password_reset".equals(state.type) || "logout".equals(state.type)) {
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

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showWelcomeScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundResource(R.drawable.bg_welcome);
        setContentView(root);

        TextView compass = new TextView(this);
        compass.setText("✦  DATA · SKILL · CAREER  ✦");
        compass.setTextColor(Color.parseColor("#BFFBFA"));
        compass.setTextSize(12);
        compass.setTypeface(Typeface.DEFAULT_BOLD);
        compass.setGravity(Gravity.CENTER);
        root.addView(compass);

        OceanGraphicView graphic = new OceanGraphicView(this);
        LinearLayout.LayoutParams graphicParams = new LinearLayout.LayoutParams(-1, dp(245));
        graphicParams.setMargins(0, dp(18), 0, dp(18));
        root.addView(graphic, graphicParams);

        TextView brand = new TextView(this);
        brand.setText("BLUEPATH");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(48);
        brand.setLetterSpacing(0.12f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);

        TextView tagline = new TextView(this);
        tagline.setText("나의 현재 수준에서 해양 전문가가 되기까지\n데이터로 항로를 설계하는 스마트 해도");
        tagline.setTextColor(Color.parseColor("#D9FFFF"));
        tagline.setTextSize(16);
        tagline.setGravity(Gravity.CENTER);
        tagline.setLineSpacing(dp(4), 1.05f);
        tagline.setPadding(0, dp(12), 0, dp(18));
        root.addView(tagline);


        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        Button start = new Button(this);
        start.setText("시작하기  →");
        start.setAllCaps(false);
        start.setTextColor(NAVY);
        start.setTextSize(16);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable startBg = new GradientDrawable();
        startBg.setColor(Color.WHITE);
        startBg.setCornerRadius(dp(18));
        start.setBackground(startBg);
        start.setOnClickListener(v -> showLoginScreen());
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView foot = new TextView(this);
        foot.setText("국립해양박물관 · 해양교육 · NCS 진로 데이터 기반");
        foot.setTextColor(Color.parseColor("#B9E8ED"));
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
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        OceanGraphicView graphic = new OceanGraphicView(this);
        root.addView(graphic, new LinearLayout.LayoutParams(-1, dp(135)));
        root.addView(title("BluePath 로그인"));
        root.addView(body("로그인 후에만 홈·학습·퀴즈·일정·진로·AI·MY 항로를 이용할 수 있습니다."));

        if (store.hasCloudSession()) {
            LinearLayout sessionCard = card();
            sessionCard.addView(label("SAVED SESSION"));
            sessionCard.addView(big(store.getAccountDisplayName()));
            sessionCard.addView(body(store.getAccountEmail() + " 계정으로 안전하게 계속할 수 있습니다."));
            Button continueButton = primaryButton("이 계정으로 계속하기");
            continueButton.setOnClickListener(v -> {
                if (store.hasProfile()) showApp(0); else showOnboarding();
            });
            sessionCard.addView(continueButton, new LinearLayout.LayoutParams(-1, dp(50)));
            root.addView(sessionCard);
            root.addView(label("다른 계정으로 로그인"));
        }

        LinearLayout form = card();
        EditText email = inputField("email@example.com", store.getAccountEmail());
        EditText password = inputField("8자 이상 비밀번호", "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(label("이메일"));
        form.addView(email);
        form.addView(label("비밀번호"));
        form.addView(password);

        Button login = primaryButton("로그인");
        login.setOnClickListener(v -> {
            String emailValue = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            if (emailValue.isEmpty() || passwordValue.length() < 8) {
                toast("이메일과 8자 이상의 비밀번호를 입력해 주세요.");
                return;
            }
            viewModel.login(emailValue, passwordValue);
        });
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(-1, dp(52));
        loginParams.setMargins(0, dp(16), 0, dp(6));
        form.addView(login, loginParams);

        Button forgot = outlineButton("비밀번호를 잊어버렸나요?");
        forgot.setOnClickListener(v -> showForgotPasswordScreen());
        form.addView(forgot, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(form);

        LinearLayout signupCard = card();
        signupCard.addView(big("처음 오셨나요?"));
        signupCard.addView(body("회원가입 후 연령·관심 분야·목표를 설정하면 개인 맞춤 해양 항로가 생성됩니다."));
        Button signup = outlineButton("회원가입");
        signup.setOnClickListener(v -> showRegisterScreen());
        signupCard.addView(signup, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(signupCard);

        Button back = outlineButton("이전 화면으로 돌아가기");
        back.setOnClickListener(v -> showWelcomeScreen());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(46)));

        if (!viewModel.isCloudConfigured()) {
            root.addView(note("이 빌드에는 BLUEPATH_API_BASE_URL이 설정되지 않았습니다. 회원가입과 로그인을 사용하려면 서버 주소를 설정해 주세요.", DANGER));
        }
    }

    private void showRegisterScreen() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(title("BluePath 회원가입"));
        root.addView(body("계정을 만든 뒤 해양 인재 프로필을 설정합니다. 미성년 사용자의 보호자 동의는 프로필 설정 단계에서 이어집니다."));
        LinearLayout form = card();
        EditText email = inputField("email@example.com", "");
        EditText password = inputField("8자 이상 비밀번호", "");
        EditText confirm = inputField("비밀번호 다시 입력", "");
        EditText guardian = inputField("보호자 이메일 (선택)", "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(label("이메일")); form.addView(email);
        form.addView(label("비밀번호")); form.addView(password);
        form.addView(label("비밀번호 확인")); form.addView(confirm);
        form.addView(label("보호자 이메일")); form.addView(guardian);
        Button create = primaryButton("계정 만들기");
        create.setOnClickListener(v -> {
            String emailValue = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            if (emailValue.isEmpty() || passwordValue.length() < 8) {
                toast("이메일과 8자 이상의 비밀번호를 입력해 주세요.");
                return;
            }
            if (!passwordValue.equals(confirm.getText().toString())) {
                toast("비밀번호 확인이 일치하지 않습니다.");
                return;
            }
            viewModel.register(emailValue, passwordValue, guardian.getText().toString().trim());
        });
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, dp(52));
        createParams.setMargins(0, dp(16), 0, 0);
        form.addView(create, createParams);
        root.addView(form);
        Button back = outlineButton("로그인으로 돌아가기");
        back.setOnClickListener(v -> showLoginScreen());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void showForgotPasswordScreen() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(title("비밀번호 재설정"));
        root.addView(body("가입한 이메일을 입력하면 안전한 일회용 재설정 링크를 보냅니다. 계정 존재 여부는 보안을 위해 화면에 구분해서 표시하지 않습니다."));
        LinearLayout form = card();
        EditText email = inputField("email@example.com", store.getAccountEmail());
        form.addView(label("가입 이메일"));
        form.addView(email);
        Button request = primaryButton("재설정 안내 보내기");
        request.setOnClickListener(v -> {
            String value = email.getText().toString().trim();
            if (value.isEmpty() || !value.contains("@")) {
                toast("올바른 이메일을 입력해 주세요.");
                return;
            }
            viewModel.requestPasswordReset(value);
        });
        LinearLayout.LayoutParams requestParams = new LinearLayout.LayoutParams(-1, dp(52));
        requestParams.setMargins(0, dp(16), 0, 0);
        form.addView(request, requestParams);
        root.addView(form);
        Button back = outlineButton("로그인으로 돌아가기");
        back.setOnClickListener(v -> showLoginScreen());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void showOnboarding() {
        if (!store.hasCloudSession()) {
            showLoginScreen();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        OceanGraphicView graphic = new OceanGraphicView(this);
        root.addView(graphic, new LinearLayout.LayoutParams(-1, dp(150)));

        TextView title = title("BluePath\n스마트 해도 AI");
        root.addView(title);
        root.addView(body("해양 콘텐츠, 역량 진단, 실제 교육 일정과 NCS 진로 로드맵을 하나의 항해 경로로 연결합니다. 첫 항로를 만들기 위해 해양 인재 프로필을 설정하세요."));

        Spinner age = spinner(new String[]{"초등학생", "중학생", "고등학생", "대학생", "성인", "직장인", "학부모/가족"});
        Spinner interest = spinner(new String[]{"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"});
        Spinner goal = spinner(new String[]{"흥미", "체험", "진로탐색", "자격증", "직무역량 강화", "가족 교육"});
        Spinner level = spinner(new String[]{"입문", "기초", "중급", "심화", "실무"});

        root.addView(label("연령대")); root.addView(age);
        root.addView(label("관심 분야")); root.addView(interest);
        root.addView(label("학습 목적")); root.addView(goal);
        root.addView(label("현재 수준")); root.addView(level);

        Button start = primaryButton("나의 해양 인재 DNA 시작하기");
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
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(52));
        startParams.setMargins(0, dp(18), 0, 0);
        root.addView(start, startParams);
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
        currentTab = tab;
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(BG);
        setContentView(appRoot);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(BG);
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
        TextView h = new TextView(this);
        h.setText("⚓ BluePath");
        h.setTextColor(Color.WHITE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        brand.addView(h);
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

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(22));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

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

        LinearLayout account = card();
        account.addView(label("SIGNED IN"));
        account.addView(big(store.getAccountDisplayName()));
        account.addView(body(store.getAccountEmail()));
        sidebar.addView(account);

        addNavButton("⌂  홈", 0);
        addNavButton("▶  학습", 1);
        addNavButton("✓  퀴즈", 2);
        addNavButton("◷  일정", 3);
        addNavButton("⌁  진로", 4);
        addNavButton("✦  AI", 5);
        addNavButton("●  MY", 6);

        View sideSpacer = new View(this);
        sidebar.addView(sideSpacer, new LinearLayout.LayoutParams(1, 0, 1));
        TextView sideNote = body("메뉴는 언제든 열고 닫을 수 있습니다. 각 탭 상단에서 목적과 사용 방법을 먼저 확인하세요.");
        sideNote.setTextColor(MUTED);
        sidebar.addView(sideNote);

        renderTab(tab);
    }

    private String tabTitle(int tab) {
        switch (tab) {
            case 1: return "학습";
            case 2: return "퀴즈";
            case 3: return "일정";
            case 4: return "진로";
            case 5: return "AI";
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

    private void addNavButton(String label, int tab) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), 0, dp(12), 0);
        b.setTextColor(tab == currentTab ? NAVY : TEXT);
        b.setTypeface(Typeface.DEFAULT, tab == currentTab ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackgroundResource(tab == currentTab ? R.drawable.bg_nav_selected : android.R.color.transparent);
        b.setOnClickListener(v -> showApp(tab));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(2), 0, dp(2));
        sidebar.addView(b, params);
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
            case 5: renderAgent(); break;
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
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(28);
        iconView.setGravity(Gravity.CENTER);
        top.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
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
        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(Color.parseColor("#D9FFFF"));
        desc.setTextSize(13);
        desc.setLineSpacing(dp(2), 1.05f);
        desc.setPadding(0, dp(10), 0, 0);
        intro.addView(desc);
        content.addView(intro);
    }

    private void renderHome() {
        addTabIntro("🧭", "TODAY'S ROUTE", "홈 · 오늘의 항로", "현재 티어와 역량 데이터, 다음 학습, 실제 교육 일정과 관람객 수요 근거를 한 화면에서 확인합니다.");
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        String xpTier = store.getXpTier();
        int xp = p.xp;
        int base = UserStore.tierBaseXp(xpTier);
        int next = UserStore.nextTierXp(xpTier);
        int progress = xpTier.equals("다이아") ? 100 : Math.min(100, Math.max(0, (xp - base) * 100 / Math.max(1, next - base)));

        LinearLayout hero = card();
        LinearLayout top = row();
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(label("MY OCEAN ROUTE"));
        heroText.addView(big(p.persona));
        heroText.addView(body(p.interest + " · " + p.goal + " · " + p.level));
        top.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(tierBadge(tier));
        hero.addView(top);
        hero.addView(body("통합 티어 " + PromotionRules.displayName(tier) + " · XP 기준 " + PromotionRules.displayName(xpTier) + " · 퀴즈 획득 " + PromotionRules.displayName(store.getQuizTier())));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); bar.setProgress(progress);
        hero.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));
        hero.addView(body("XP " + xp + " · " + RecommendationEngine.nextAction(p, tier)));
        content.addView(hero);

        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(DataRepository.contents().size()), "전체 영상"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "학습 완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "퀴즈 응시"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        addSkillSummaryCard();

        content.addView(sectionTitle("AI 추천 항로"));
        List<ContentItem> items = RecommendationEngine.recommendedContents(p, tier, store);
        for (int i = 0; i < Math.min(3, items.size()); i++) addContentCard(items.get(i), true);

        content.addView(sectionTitle("다음 승급 체크포인트"));
        LinearLayout ruleCard = card();
        ruleCard.addView(big(PromotionRules.quizRule(tier)));
        ruleCard.addView(body("반복 실패만으로 XP를 얻을 수 없습니다. 최초 합격 또는 이전 최고점 향상에만 보상이 지급되며, 오답 분야는 역량 숙련도와 다음 추천에 반영됩니다."));
        Button goQuiz = primaryButton("승급 퀴즈로 이동");
        goQuiz.setOnClickListener(v -> showApp(2));
        ruleCard.addView(goQuiz, new LinearLayout.LayoutParams(-1, dp(46)));
        content.addView(ruleCard);

        content.addView(sectionTitle("추천 교육 일정"));
        List<ProgramItem> programs = RecommendationEngine.recommendedPrograms(p, store);
        for (int i = 0; i < Math.min(2, programs.size()); i++) addProgramCard(programs.get(i));

        List<String> insights = DataRepository.surveyInsights();
        if (!insights.isEmpty()) {
            content.addView(sectionTitle("관람객 데이터가 말하는 설계 근거"));
            LinearLayout insightCard = card();
            insightCard.addView(big("실제 관람객 " + DataRepository.surveySampleSize() + "명 응답 분석"));
            for (int i = 0; i < Math.min(4, insights.size()); i++) insightCard.addView(body("• " + insights.get(i)));
            insightCard.addView(note("이 수요를 가족형 체험, 모바일 안내, 교육 프로그램 추천의 우선순위에 반영했습니다.", OCEAN));
            content.addView(insightCard);
        }
    }

    private void renderLearning() {
        addTabIntro("🌊", "LEARNING LIBRARY", "학습 · 맞춤 콘텐츠", "관심 분야와 통합 티어뿐 아니라 완료 기록과 퀴즈 오답으로 발견한 역량 갭까지 반영해 다음 영상을 추천합니다.");
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("난도별 해양 영상 라이브러리"));
        content.addView(body("첨부 스프레드시트의 28개 영상을 모두 반영했습니다. 하 10개, 중 10개, 상 8개로 분류되며 추천 점수 안에서는 개인 관심 분야가 우선됩니다."));

        List<ContentItem> all = RecommendationEngine.recommendedContents(p, tier, store);
        addDifficultySection("하", "입문 · " + PromotionRules.displayName("브론즈"), all);
        addDifficultySection("중", "진로 탐색 · " + PromotionRules.displayName("실버"), all);
        addDifficultySection("상", "직무 심화 · " + PromotionRules.displayName("골드"), all);
    }

    private void addDifficultySection(String difficulty, String subtitle, List<ContentItem> all) {
        int count = 0;
        for (ContentItem item : all) if (item.difficulty.equals(difficulty)) count++;
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionTitle(difficulty + " 난도");
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView meta = label(subtitle + " · " + count + "개");
        meta.setGravity(Gravity.END);
        heading.addView(meta);
        content.addView(heading);
        for (ContentItem item : all) if (item.difficulty.equals(difficulty)) addContentCard(item, false);
    }

    private void renderQuiz() {
        addTabIntro("✓", "SKILL CHECK", "퀴즈 · 역량 진단", "승급 점수만 확인하는 것이 아니라 문항별 주제를 역량 증거로 저장해 다음 학습과 진로 추천을 정교하게 만듭니다.");
        String currentTier = store.getTier();
        content.addView(sectionTitle("AI 승급 퀴즈"));
        content.addView(body("현재 통합 티어: " + PromotionRules.displayName(currentTier) + " · " + PromotionRules.quizRule(currentTier)));

        if (PromotionRules.questionCount(currentTier) == 0 && activeQuiz.isEmpty()) {
            LinearLayout card = card();
            card.addView(big("모든 승급 항로를 완료했습니다."));
            card.addView(body("💎 다이아 티어를 달성했습니다. 해양 학습 기록, 프로젝트와 진로 로드맵을 계속 확장해 보세요."));
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
            startCard.addView(big(PromotionRules.displayTransition(currentTier)));
            startCard.addView(body(total + "문제 · 합격선 " + pass + "문제 · 전 문항 4지선다"));
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
        session.addView(big(PromotionRules.displayName(quizAttemptTier) + " 승급 세션"));
        session.addView(body(activeQuiz.size() + "문제 · 합격선 " + PromotionRules.passCount(quizAttemptTier)
                + "문제 · 출제: " + quizSource));
        if (!quizNotice.isEmpty()) session.addView(note(quizNotice, MUTED));
        content.addView(session);

        if (quizSubmitted) addQuizResultCard();

        for (int i = 0; i < activeQuiz.size(); i++) addQuizQuestionCard(activeQuiz.get(i), i);

        if (!quizSubmitted) {
            Button submit = primaryButton("모든 답안 최종 제출 및 채점");
            submit.setOnClickListener(v -> submitQuiz());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
            lp.setMargins(0, dp(6), 0, dp(12));
            content.addView(submit, lp);
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
                    source = "BluePath Marine AI + RAG";
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
            final String finalNotice = notice;
            runOnUiThread(() -> {
                quizGenerating = false;
                activeQuiz = result == null ? new ArrayList<>() : new ArrayList<>(result);
                selectedAnswers = new int[activeQuiz.size()];
                Arrays.fill(selectedAnswers, -1);
                quizSource = finalSource;
                quizNotice = finalNotice;
                if (activeQuiz.size() != PromotionRules.questionCount(tier)) {
                    activeQuiz.clear();
                    selectedAnswers = new int[0];
                    quizNotice = "필요한 문제 수를 충족하지 못해 세션을 시작하지 않았습니다. 다시 생성해 주세요.";
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
            card.addView(body("내 답: " + q.options[selectedAnswers[questionIndex]]));
            card.addView(body("정답: " + (q.answerIndex + 1) + ". " + q.options[q.answerIndex]));
            card.addView(body("해설: " + q.explanation));
        }
        content.addView(card);
    }

    private void submitQuiz() {
        if (activeQuiz.isEmpty() || selectedAnswers.length != activeQuiz.size()) {
            toast("퀴즈를 다시 생성해 주세요.");
            return;
        }
        for (int i = 0; i < selectedAnswers.length; i++) {
            if (selectedAnswers[i] < 0) {
                toast((i + 1) + "번 문제의 답을 선택해 주세요. 모든 문항을 답한 뒤 채점합니다.");
                return;
            }
        }

        int correct = 0;
        for (int i = 0; i < activeQuiz.size(); i++) {
            boolean isCorrect = selectedAnswers[i] == activeQuiz.get(i).answerIndex;
            if (isCorrect) correct++;
            store.recordSkillEvidence(activeQuiz.get(i).topic, isCorrect);
        }
        quizCorrect = correct;
        boolean passed = correct >= PromotionRules.passCount(quizAttemptTier);
        quizAwardedXp = store.calculateQuizXpAward(quizAttemptTier, correct, passed);
        store.recordQuizAttempt(quizAttemptTier, correct, activeQuiz.size(), passed, quizSource);
        viewModel.recordLearning("quiz", quizAttemptTier, PromotionRules.displayName(quizAttemptTier),
                correct + "/" + activeQuiz.size() + (passed ? " passed" : " retry"));
        if (quizAwardedXp > 0) store.addXp(quizAwardedXp);
        if (passed) store.promoteByQuiz(quizAttemptTier);
        quizSubmitted = true;
        showApp(2);
    }

    private void addQuizResultCard() {
        int total = activeQuiz.size();
        boolean passed = quizCorrect >= PromotionRules.passCount(quizAttemptTier);
        int score = total == 0 ? 0 : Math.round(quizCorrect * 100f / total);
        LinearLayout result = card();
        result.addView(big(passed ? "🎉 승급 기준 통과" : "🌊 다시 항해할 준비"));
        result.addView(huge(score + "점"));
        result.addView(body(quizCorrect + " / " + total + " 정답 · 합격선 " + PromotionRules.passCount(quizAttemptTier) + "문제"));
        String passMessage;
        if (passed && "플래티넘".equals(quizAttemptTier)) {
            passMessage = "다이아 고급 퀴즈를 통과했습니다. MY에서 자격 증빙과 해양 프로젝트를 제출해 인증 항로를 완성하세요.";
        } else if (passed) {
            passMessage = PromotionRules.displayName(quizAttemptTier) + "에서 "
                    + PromotionRules.displayName(PromotionRules.nextTier(quizAttemptTier))
                    + "로 승급했습니다. 현재 통합 티어: " + PromotionRules.displayName(store.getTier());
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
        quizAttemptTier = "";
        quizSource = "";
        quizNotice = "";
        quizCorrect = 0;
        quizAwardedXp = 0;
    }

    private void renderSchedule() {
        addTabIntro("◷", "LIVE & ARCHIVE", "일정 · 교육 탐색", "국립해양박물관과 한국해양수산연수원의 제공 데이터를 현재 일정과 종료 아카이브로 구분하고, 추천 근거와 출처를 함께 보여줍니다.");
        UserProfile p = store.getProfile();
        List<ProgramItem> programs = RecommendationEngine.recommendedPrograms(p, store);
        List<EventItem> events = RecommendationEngine.recommendedEvents(p);

        LinearLayout dataCard = card();
        dataCard.addView(big("교육 과정 " + programs.size() + "개 · 행사 " + events.size() + "개"));
        dataCard.addView(body("전체 데이터는 추천 계산과 AI 근거에 사용됩니다. 화면에는 탐색성을 위해 우선순위가 높은 항목을 먼저 표시합니다."));
        dataCard.addView(note("종료된 일정은 현재 모집 중인 과정처럼 보이지 않도록 ‘종료·아카이브’로 명확히 구분합니다.", OCEAN));
        content.addView(dataCard);

        content.addView(sectionTitle("교육 프로그램 스케줄"));
        int shownPrograms = Math.min(20, programs.size());
        for (int i = 0; i < shownPrograms; i++) addProgramCard(programs.get(i));
        if (programs.size() > shownPrograms) content.addView(note("전체 " + programs.size() + "개 중 상위 " + shownPrograms + "개를 표시했습니다. 나머지 데이터도 추천 엔진과 AI 검색에 포함됩니다.", MUTED));

        content.addView(sectionTitle("이벤트·영화·공연 아카이브"));
        int shownEvents = Math.min(12, events.size());
        for (int i = 0; i < shownEvents; i++) addEventCard(events.get(i));
        if (events.size() > shownEvents) content.addView(note("전체 " + events.size() + "개 중 상위 " + shownEvents + "개를 표시했습니다.", MUTED));
    }

    private void renderCareer() {
        addTabIntro("⌁", "NCS CAREER MAP", "진로 · 역량 항로", "통합 티어, 관심 분야, 역량 숙련도를 NCS 핵심 역량과 실제 해양기관 데이터에 연결해 다음 단계를 제안합니다.");
        UserProfile p = store.getProfile();
        content.addView(sectionTitle("NCS 기반 커리어 항로"));
        content.addView(body("관심 분야, 현재 티어, 학습 목표를 바탕으로 직무·필요 역량·근무지·다음 학습을 연결합니다."));
        for (CareerItem c : RecommendationEngine.recommendedCareers(p, store.getTier(), store)) addCareerCard(c);
    }

    private void renderAgent() {
        addTabIntro("✦", "SOURCED MARINE AI", "AI · 근거 기반 상담", "학습·승급·일정·진로 질문을 해양 특화 데이터와 출처를 바탕으로 상담하고, 연결 실패 시 검증된 로컬 안내로 전환합니다.");
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("BluePath Marine AI Agent"));
        content.addView(body(llmClient.isConfigured()
                ? "보안 서버의 해양 AI가 사용자 프로필, 승급 기준, 추천 콘텐츠, 기관 자료를 함께 검색해 출처가 있는 답변을 제공합니다."
                : "오프라인 해양 상담 엔진으로도 학습·승급·진로 질문에 답할 수 있습니다."));

        EditText input = new EditText(this);
        input.setHint("예: 골드 승급을 위해 무엇을 공부해야 해?");
        input.setMinLines(3);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setBackgroundResource(R.drawable.bg_input);
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));

        Button ask = primaryButton("해양 AI에게 상담 받기");
        ask.setOnClickListener(v -> requestAgentAnswer(input.getText().toString()));
        LinearLayout.LayoutParams askParams = new LinearLayout.LayoutParams(-1, dp(50));
        askParams.setMargins(0, dp(10), 0, dp(10));
        content.addView(ask, askParams);

        LinearLayout answerCard = card();
        answerCard.addView(label(llmClient.isConfigured() ? "MARINE AI · SOURCED RESPONSE" : "OFFLINE MARINE GUIDE"));
        if (agentLoading) {
            answerCard.addView(big("답변을 구성하고 있습니다"));
            answerCard.addView(new ProgressBar(this));
        } else {
            answerCard.addView(body(agentLastAnswer));
        }
        content.addView(answerCard);

        content.addView(label("추천 질문"));
        String[] chips = {"내 티어 올리려면?", "내 관심사 영상 추천", "항해사 역량 로드맵", "스마트 항만 직무 알려줘", "퀴즈 방식 설명해줘"};
        for (String chip : chips) {
            Button b = outlineButton(chip);
            b.setOnClickListener(v -> requestAgentAnswer(chip));
            content.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));
        }
    }

    private void requestAgentAnswer(String question) {
        final String trimmed = question == null ? "" : question.trim();
        if (trimmed.isEmpty()) {
            toast("질문을 입력해 주세요.");
            return;
        }
        agentLoading = true;
        showApp(5);
        executor.execute(() -> {
            String answer;
            if (llmClient.isConfigured()) {
                try {
                    UserProfile profile = store.getProfile();
                    answer = llmClient.answerAgent(trimmed, profile, store.getTier(),
                            RecommendationEngine.recommendedContents(profile, store.getTier(), store),
                            PromotionRules.fullManual());
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
                agentLastAnswer = result;
                showApp(5);
            });
        });
    }

    private void renderMyPage() {
        addTabIntro("●", "MY OCEAN PASSPORT", "MY · 나의 해양 여권", "프로필, 분야별 숙련도, 검증된 학습 증거, 승급 기록, 클라우드 동기화와 다이아 인증 경로를 관리합니다.");
        UserProfile p = store.getProfile();
        String tier = store.getTier();

        LinearLayout profileCard = card();
        LinearLayout profileTop = row();
        profileTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = new TextView(this);
        avatar.setText("🌊");
        avatar.setTextSize(32);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(Color.parseColor("#DFFBFA"));
        avatar.setBackground(avatarBg);
        profileTop.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        profileText.setPadding(dp(12), 0, 0, 0);
        profileText.addView(big(p.persona));
        profileText.addView(body(p.ageGroup + " · " + p.interest + " · " + p.goal));
        profileText.addView(label(store.getAccountEmail()));
        profileTop.addView(profileText, new LinearLayout.LayoutParams(0, -2, 1));
        profileTop.addView(tierBadge(tier));
        profileCard.addView(profileTop);
        profileCard.addView(body("통합 티어 " + PromotionRules.displayName(tier) + " · XP 기준 " + PromotionRules.displayName(store.getXpTier()) + " · 퀴즈 기준 " + PromotionRules.displayName(store.getQuizTier())));
        content.addView(profileCard);

        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "검증 완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getBookmarks().size()), "찜"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "응시"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        content.addView(sectionTitle("Ocean Skill Passport"));
        LinearLayout skillCard = card();
        skillCard.addView(big("분야별 역량 숙련도"));
        skillCard.addView(body("퀴즈 문항과 학습 완료 증거를 기반으로 계산합니다. 증거가 없는 분야는 진단 전 기준값 50점으로 표시됩니다."));
        String[] topics = {"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"};
        for (String topic : topics) addSkillProgress(skillCard, topic);
        content.addView(skillCard);

        content.addView(sectionTitle("승급·학습 리포트"));
        LinearLayout report = card();
        report.addView(big("XP " + p.xp + " · " + PromotionRules.quizRule(tier)));
        report.addView(body("최근 퀴즈: " + store.getLastQuizSummary()));
        report.addView(body(PromotionRules.displayName("브론즈") + " 최고 " + store.getBestQuizScore("브론즈") + "/10 · "
                + PromotionRules.displayName("실버") + " 최고 " + store.getBestQuizScore("실버") + "/12 · "
                + PromotionRules.displayName("골드") + " 최고 " + store.getBestQuizScore("골드") + "/15 · "
                + PromotionRules.displayName("플래티넘") + " 고급 " + store.getBestQuizScore("플래티넘") + "/20"));
        Button manual = outlineButton("승급 기준 전체 보기");
        manual.setOnClickListener(v -> showPromotionManual());
        report.addView(manual);
        content.addView(report);

        content.addView(sectionTitle("검증된 학습 기록"));
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

        content.addView(sectionTitle("💎 다이아 인증 항로"));
        LinearLayout diamondCard = card();
        diamondCard.addView(big("고급 퀴즈 · 자격 증빙 · 해양 프로젝트"));
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

        content.addView(sectionTitle("개인정보와 보호자 동의"));
        LinearLayout privacyCard = card();
        privacyCard.addView(big(store.requiresGuardianConsent()
                ? (store.hasGuardianConsent() ? "🛡️ 보호자 동의 완료" : "🛡️ 보호자 동의 필요")
                : "🛡️ 개인정보 보호"));
        privacyCard.addView(body("BluePath는 로그인 계정으로 프로필과 학습 증거를 동기화하며, 인증 토큰은 Android Keystore 기반 저장소로 보호합니다."));
        if (store.requiresGuardianConsent()) {
            Button consent = outlineButton(store.hasGuardianConsent() ? "보호자 동의 정보 확인" : "보호자 동의 진행");
            consent.setOnClickListener(v -> showGuardianConsentDialog(false));
            privacyCard.addView(consent);
        }
        Button privacy = outlineButton("개인정보 안내 보기");
        privacy.setOnClickListener(v -> showPrivacyNotice());
        privacyCard.addView(privacy);
        content.addView(privacyCard);

        content.addView(sectionTitle("앱 관리"));
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

    private void addReasonList(LinearLayout parent, List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) return;
        parent.addView(label("추천 근거"));
        for (int i = 0; i < Math.min(4, reasons.size()); i++) parent.addView(body("• " + reasons.get(i)));
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
            toast("먼저 영상 학습을 시작해 주세요.");
            return;
        }
        long elapsed = store.secondsSinceContentStarted(item.id);
        long minimumSeconds = 45L;
        if (elapsed < minimumSeconds) {
            toast("완료 인증까지 최소 " + (minimumSeconds - elapsed) + "초의 학습 시간이 더 필요합니다.");
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(body("버튼 클릭만으로 완료 처리하지 않습니다. 영상에서 배운 핵심 내용을 한 문장 이상 기록하면 학습 증거와 XP가 인정됩니다."));
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
            store.recordSkillEvidence(item.topic, true);
            int xp = item.difficulty.equals("하") ? 80 : item.difficulty.equals("중") ? 120 : 180;
            store.addXp(xp);
            viewModel.recordLearning("video", item.id, item.title, "completed_with_reflection");
            dialog.dismiss();
            toast("학습 완료가 인증되었습니다. XP +" + xp);
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
        for (CareerItem item : DataRepository.careers()) if (item.id.equals(id)) return item.title;
        return id;
    }

    private void addContentCard(ContentItem item, boolean compact) {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        int score = RecommendationEngine.scoreContent(item, p, tier, store);
        boolean completed = store.getCompletedContentIds().contains(item.id);
        boolean started = store.isContentStarted(item.id);
        LinearLayout card = card();
        card.addView(label(item.difficulty + " 난도 · " + PromotionRules.displayName(item.requiredTier) + " 권장 · 적합도 " + score));
        card.addView(big("▶ " + item.title));
        String duration = item.minutes > 0 ? " · " + item.minutes + "분" : "";
        card.addView(body("출처: " + item.source + duration));
        card.addView(body("분야: " + item.topic + " · 연결 진로: " + item.careerTag));
        addReasonList(card, RecommendationEngine.contentReasons(item, p, tier, store));
        if (completed) card.addView(note("학습 완료 인증 · XP 반영됨", SUCCESS));
        else if (started) card.addView(note("학습 시작 기록됨 · 시청 후 핵심 내용을 제출해 완료를 인증하세요.", OCEAN));

        LinearLayout actionRow = row();
        Button watch = primaryButton(compact ? "열기" : (started ? "영상 계속 보기" : "영상 학습 시작"));
        watch.setOnClickListener(v -> {
            store.markContentStarted(item.id);
            viewModel.recordLearning("video", item.id, item.title, "started");
            showApp(currentTab);
            openUrl(item.url);
        });
        Button save = outlineButton(store.isBookmarked(item.id) ? "찜 해제" : "찜");
        save.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            viewModel.recordLearning("bookmark", item.id, item.title, store.isBookmarked(item.id) ? "saved" : "removed");
            toast(store.isBookmarked(item.id) ? "찜 목록에 저장했습니다." : "찜을 해제했습니다.");
            showApp(currentTab);
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(44), 1);
        left.setMargins(0, 0, dp(6), 0);
        actionRow.addView(watch, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(44), 1);
        right.setMargins(dp(6), 0, 0, 0);
        actionRow.addView(save, right);
        card.addView(actionRow);

        if (started && !completed) {
            Button complete = outlineButton("학습 완료 인증");
            complete.setOnClickListener(v -> showContentCompletionDialog(item));
            card.addView(complete, new LinearLayout.LayoutParams(-1, dp(46)));
        }
        content.addView(card);
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
        if (!archived && !"데이터 확인 필요".equals(status)) {
            Button calendar = outlineButton("내 캘린더에 추가");
            calendar.setOnClickListener(v -> addProgramToCalendar(item));
            card.addView(calendar);
        }
        content.addView(card);
    }

    private void addEventCard(EventItem item) {
        String status = RecommendationEngine.scheduleStatus(item.startDate, item.endDate);
        LinearLayout card = card();
        card.addView(label(item.category + " · " + item.target + " · " + status));
        card.addView(big("🎪 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate));
        card.addView(body(item.description));
        card.addView(body("출처: " + item.source));
        if (RecommendationEngine.isArchived(item.startDate, item.endDate)) {
            card.addView(note("종료된 행사입니다. 유사 프로그램 기획과 개인 관심 분석을 위한 아카이브로 제공합니다.", MUTED));
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
        card.addView(label(item.field + " · 권장 " + PromotionRules.displayName(item.recommendedTier) + " · 적합도 " + score));
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
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        TextView notice = body("보호자는 연령대·관심 분야·학습 목표와 학습 증거가 맞춤 추천 및 계정 동기화에 사용되는 것에 동의합니다. 최소 정보만 수집하며 동의 기록을 MY에서 확인할 수 있습니다.");
        EditText guardianEmail = inputField("보호자 이메일", store.getGuardianEmail());
        form.addView(notice);
        form.addView(label("보호자 이메일"));
        form.addView(guardianEmail);
        new AlertDialog.Builder(this)
                .setTitle("보호자 동의")
                .setView(form)
                .setCancelable(!onboarding)
                .setNegativeButton("동의하지 않음", (dialog, which) -> {
                    store.saveGuardianConsent(false, "");
                    showApp(onboarding ? 0 : 6);
                })
                .setPositiveButton("동의하고 계속", (dialog, which) -> {
                    String email = guardianEmail.getText().toString().trim();
                    if (email.isEmpty()) {
                        toast("보호자 이메일을 입력해 주세요.");
                        showApp(onboarding ? 0 : 6);
                        return;
                    }
                    store.saveGuardianConsent(true, email);
                    if (viewModel.isCloudConfigured()) viewModel.syncNow();
                    toast("보호자 동의 정보를 저장했습니다.");
                    showApp(onboarding ? 0 : 6);
                }).show();
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
                .setMessage(PromotionRules.fullManual())
                .setPositiveButton("확인", null)
                .show();
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
        v.setText(text);
        v.setTextColor(NAVY);
        v.setTextSize(20);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(12), 0, dp(8));
        return v;
    }

    private TextView title(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(NAVY);
        v.setTextSize(29);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(14), 0, dp(12));
        return v;
    }

    private TextView huge(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(NAVY);
        v.setTextSize(34);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(4), 0, dp(6));
        return v;
    }

    private TextView big(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(17);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(2), 0, dp(6));
        return v;
    }

    private TextView body(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(14);
        v.setLineSpacing(dp(2), 1.05f);
        v.setPadding(0, dp(4), 0, dp(6));
        return v;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
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

    private TextView tierBadge(String tier) {
        TextView badge = new TextView(this);
        badge.setText(PromotionRules.displayName(tier));
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        int background;
        int foreground;
        switch (tier) {
            case "실버": background = Color.parseColor("#E2E8F0"); foreground = Color.parseColor("#334155"); break;
            case "골드": background = Color.parseColor("#FEF3C7"); foreground = Color.parseColor("#92400E"); break;
            case "플래티넘": background = Color.parseColor("#E0F2FE"); foreground = Color.parseColor("#075985"); break;
            case "다이아": background = Color.parseColor("#EDE9FE"); foreground = Color.parseColor("#5B21B6"); break;
            default: background = Color.parseColor("#FDE6D3"); foreground = Color.parseColor("#9A3412");
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(14));
        badge.setBackground(bg);
        badge.setTextColor(foreground);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        return badge;
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
        b.setText(text);
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
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(NAVY);
        b.setTextSize(13);
        b.setBackgroundResource(R.drawable.bg_secondary_button);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("링크를 열 수 없습니다: " + url);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
