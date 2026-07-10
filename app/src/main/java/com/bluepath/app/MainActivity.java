package com.bluepath.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.storage.UserStore;
import com.bluepath.app.util.MarineLlmClient;
import com.bluepath.app.util.PromotionRules;
import com.bluepath.app.util.RecommendationEngine;
import com.bluepath.app.view.OceanGraphicView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int currentTab = 0;

    private List<QuizQuestion> activeQuiz = new ArrayList<>();
    private int[] selectedAnswers = new int[0];
    private boolean quizGenerating = false;
    private boolean quizSubmitted = false;
    private String quizAttemptTier = "";
    private String quizSource = "";
    private String quizNotice = "";
    private int quizCorrect = 0;

    private boolean agentLoading = false;
    private String agentLastAnswer = "질문을 입력하면 해양 도메인 LLM이 학습·승급·진로 경로를 안내합니다.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DataRepository.initialize(this);
        store = new UserStore(this);
        llmClient = new MarineLlmClient(store);
        if (!store.hasProfile()) showOnboarding(); else showApp(0);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showOnboarding() {
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
        root.addView(body("해양 콘텐츠, LLM 승급 퀴즈, NCS 진로 로드맵을 하나의 항해 경로로 연결합니다. 먼저 나에게 맞는 해양 인재 프로필을 설정하세요."));

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
            showApp(0);
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, dp(52));
        startParams.setMargins(0, dp(18), 0, 0);
        root.addView(start, startParams);
    }

    private void showApp(int tab) {
        currentTab = tab;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(14), dp(12));
        header.setBackgroundResource(R.drawable.bg_ocean_header);

        LinearLayout headerRow = row();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView h = new TextView(this);
        h.setText("⚓ BluePath");
        h.setTextColor(Color.WHITE);
        h.setTextSize(23);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        brand.addView(h);
        TextView sub = new TextView(this);
        sub.setText("해양 학습 · LLM 퀴즈 · 커리어 항해");
        sub.setTextColor(Color.parseColor("#C9FFFF"));
        sub.setTextSize(12);
        sub.setPadding(0, dp(3), 0, 0);
        brand.addView(sub);
        headerRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        Button manual = outlineButton("승급 기준");
        manual.setTextSize(11);
        manual.setOnClickListener(v -> showPromotionManual());
        headerRow.addView(manual, new LinearLayout.LayoutParams(dp(88), dp(38)));
        header.addView(headerRow);

        root.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(22));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(dp(4), dp(6), dp(4), dp(6));
        root.addView(nav);
        addNavButton("홈", 0);
        addNavButton("학습", 1);
        addNavButton("퀴즈", 2);
        addNavButton("일정", 3);
        addNavButton("진로", 4);
        addNavButton("AI", 5);
        addNavButton("마이", 6);

        renderTab(tab);
    }

    private void addNavButton(String label, int tab) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(1), 0, dp(1), 0);
        b.setTextColor(tab == currentTab ? NAVY : MUTED);
        b.setTypeface(Typeface.DEFAULT, tab == currentTab ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackgroundResource(tab == currentTab ? R.drawable.bg_nav_selected : android.R.color.transparent);
        b.setOnClickListener(v -> showApp(tab));
        nav.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
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

    private void renderHome() {
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
        stats.addView(statCard("28", "전체 영상"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "학습 완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "퀴즈 응시"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        content.addView(sectionTitle("AI 추천 항로"));
        List<ContentItem> items = RecommendationEngine.recommendedContents(p, tier);
        for (int i = 0; i < Math.min(3, items.size()); i++) addContentCard(items.get(i), true);

        content.addView(sectionTitle("다음 승급 체크포인트"));
        LinearLayout ruleCard = card();
        ruleCard.addView(big(PromotionRules.quizRule(tier)));
        ruleCard.addView(body("선택지마다 정오를 공개하지 않습니다. 모든 문항을 선택한 뒤 최종 채점에서 점수와 문항별 해설을 확인합니다."));
        Button goQuiz = primaryButton("승급 퀴즈로 이동");
        goQuiz.setOnClickListener(v -> showApp(2));
        ruleCard.addView(goQuiz, new LinearLayout.LayoutParams(-1, dp(46)));
        content.addView(ruleCard);

        content.addView(sectionTitle("추천 교육 일정"));
        List<ProgramItem> programs = RecommendationEngine.recommendedPrograms(p);
        for (int i = 0; i < Math.min(2, programs.size()); i++) addProgramCard(programs.get(i));
    }

    private void renderLearning() {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("난도별 해양 영상 라이브러리"));
        content.addView(body("첨부 스프레드시트의 28개 영상을 모두 반영했습니다. 하 10개, 중 10개, 상 8개로 분류되며 추천 점수 안에서는 개인 관심 분야가 우선됩니다."));

        List<ContentItem> all = RecommendationEngine.recommendedContents(p, tier);
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
        String currentTier = store.getTier();
        content.addView(sectionTitle("LLM 승급 퀴즈"));
        content.addView(body("현재 통합 티어: " + PromotionRules.displayName(currentTier) + " · " + PromotionRules.quizRule(currentTier)));

        if (PromotionRules.questionCount(currentTier) == 0 && activeQuiz.isEmpty()) {
            LinearLayout card = card();
            card.addView(big("퀴즈 단독 승급 구간을 완료했습니다."));
            card.addView(body("플래티넘 이후에는 기존 XP 기준과 자격·교육·경력 인증 로드맵을 활용합니다. 우측 상단 ‘승급 기준’에서 전체 매뉴얼을 확인하세요."));
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
                    ? "설정된 LLM 모델이 영상 주제와 현재 프로필을 바탕으로 4지선다 문제를 구성합니다."
                    : "LLM 설정이 없어 검증된 해양 도메인 로컬 문제은행을 준비합니다."));
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
                    ? "LLM 연결됨: " + store.getLlmModel()
                    : "LLM 미설정: 마이페이지에서 endpoint/model을 설정할 수 있으며, 지금은 해양 특화 로컬 문제은행으로 동작합니다."));
            Button generate = primaryButton(llmClient.isConfigured() ? "LLM 퀴즈 생성" : "해양 퀴즈 시작");
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
                    source = "해양 특화 LLM · " + store.getLlmModel();
                } catch (Exception e) {
                    generated = RecommendationEngine.quizForTier(tier, store.getProfile().interest);
                    source = "검증된 해양 로컬 문제은행";
                    notice = "LLM 호출에 실패해 로컬 문제은행으로 전환했습니다: " + safeMessage(e);
                }
            } else {
                generated = RecommendationEngine.quizForTier(tier, store.getProfile().interest);
                source = "검증된 해양 로컬 문제은행";
                notice = "실제 LLM 생성을 사용하려면 마이페이지에서 OpenAI-compatible endpoint와 fine-tuned model ID를 설정하세요.";
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
                    quizNotice = "문제 수를 충족하지 못했습니다. 마이페이지 설정 또는 fallback_quizzes.json을 확인하세요.";
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
        for (int i = 0; i < activeQuiz.size(); i++) if (selectedAnswers[i] == activeQuiz.get(i).answerIndex) correct++;
        quizCorrect = correct;
        boolean passed = correct >= PromotionRules.passCount(quizAttemptTier);
        store.recordQuizAttempt(quizAttemptTier, correct, activeQuiz.size(), passed, quizSource);
        store.addXp(passed ? 300 : 80);
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
        result.addView(note(passed
                ? PromotionRules.displayName(quizAttemptTier) + "에서 " + PromotionRules.displayName(PromotionRules.nextTier(quizAttemptTier)) + "로 승급했습니다. 현재 통합 티어: " + PromotionRules.displayName(store.getTier())
                : "합격까지 " + Math.max(0, PromotionRules.passCount(quizAttemptTier) - quizCorrect) + "문제가 더 필요합니다.",
                passed ? SUCCESS : DANGER));
        result.addView(body("아래에서 각 문항의 내 답, 정답, 해설을 확인할 수 있습니다."));
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
    }

    private void renderSchedule() {
        UserProfile p = store.getProfile();
        content.addView(sectionTitle("교육 프로그램 스케줄"));
        content.addView(body("프로필 적합도 순으로 교육·체험 일정을 추천합니다. 제공 데이터의 운영 기간이 지난 항목은 참고용 아카이브로 표시됩니다."));
        for (ProgramItem item : RecommendationEngine.recommendedPrograms(p)) addProgramCard(item);
        content.addView(sectionTitle("이벤트·영화·공연 아카이브"));
        for (EventItem e : RecommendationEngine.recommendedEvents(p)) addEventCard(e);
    }

    private void renderCareer() {
        UserProfile p = store.getProfile();
        content.addView(sectionTitle("NCS 기반 커리어 항로"));
        content.addView(body("관심 분야, 현재 티어, 학습 목표를 바탕으로 직무·필요 역량·근무지·다음 학습을 연결합니다."));
        for (CareerItem c : RecommendationEngine.recommendedCareers(p)) addCareerCard(c);
    }

    private void renderAgent() {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("BluePath Marine AI Agent"));
        content.addView(body(llmClient.isConfigured()
                ? "해양 도메인 모델 ‘" + store.getLlmModel() + "’에 연결되어 있습니다. 프로필·승급 기준·추천 영상을 문맥으로 제공합니다."
                : "LLM endpoint가 설정되지 않아 현재는 로컬 해양 상담 엔진으로 응답합니다. 마이페이지에서 fine-tuned model endpoint를 연결할 수 있습니다."));

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
        answerCard.addView(label(llmClient.isConfigured() ? "LLM RESPONSE" : "LOCAL FALLBACK RESPONSE"));
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
                            RecommendationEngine.recommendedContents(profile, store.getTier()),
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
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("마이페이지"));

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
        profileTop.addView(profileText, new LinearLayout.LayoutParams(0, -2, 1));
        profileTop.addView(tierBadge(tier));
        profileCard.addView(profileTop);
        profileCard.addView(body("통합 티어 " + PromotionRules.displayName(tier) + " · XP 기준 " + PromotionRules.displayName(store.getXpTier()) + " · 퀴즈 기준 " + PromotionRules.displayName(store.getQuizTier())));
        content.addView(profileCard);

        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(store.getCompletedContentIds().size()), "완료"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getBookmarks().size()), "찜"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statCard(String.valueOf(store.getQuizAttempts()), "응시"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);

        content.addView(sectionTitle("승급·학습 리포트"));
        LinearLayout report = card();
        report.addView(big("XP " + p.xp + " · " + PromotionRules.quizRule(tier)));
        report.addView(body("최근 퀴즈: " + store.getLastQuizSummary()));
        report.addView(body(PromotionRules.displayName("브론즈") + " 최고 " + store.getBestQuizScore("브론즈") + "/10 · "
                + PromotionRules.displayName("실버") + " 최고 " + store.getBestQuizScore("실버") + "/12 · "
                + PromotionRules.displayName("골드") + " 최고 " + store.getBestQuizScore("골드") + "/15"));
        Button manual = outlineButton("승급 기준 전체 보기");
        manual.setOnClickListener(v -> showPromotionManual());
        report.addView(manual);
        content.addView(report);

        content.addView(sectionTitle("최근 학습 기록"));
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
            showApp(6);
        });
        editCard.addView(saveProfile, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(editCard);

        content.addView(sectionTitle("Marine LLM 연결 설정"));
        LinearLayout llmCard = card();
        llmCard.addView(body("API 키를 앱에 직접 넣는 방식은 프로토타입용입니다. 상용 배포에서는 서버 프록시와 사용자 인증을 사용하세요."));
        EditText endpoint = inputField("예: https://your-llm-gateway.example.com", store.getLlmEndpoint());
        EditText model = inputField("fine-tuned model ID", store.getLlmModel());
        EditText apiKey = inputField("API key (선택)", store.getLlmApiKey());
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        llmCard.addView(label("OpenAI-compatible endpoint")); llmCard.addView(endpoint);
        llmCard.addView(label("해양 파인튜닝 모델 ID")); llmCard.addView(model);
        llmCard.addView(label("Bearer API key")); llmCard.addView(apiKey);
        Button saveLlm = primaryButton("LLM 설정 저장");
        saveLlm.setOnClickListener(v -> {
            store.saveLlmConfig(endpoint.getText().toString(), model.getText().toString(), apiKey.getText().toString());
            toast(store.hasLlmConfig() ? "해양 LLM 연결 설정을 저장했습니다." : "endpoint가 비어 있어 로컬 fallback으로 동작합니다.");
            showApp(6);
        });
        llmCard.addView(saveLlm, new LinearLayout.LayoutParams(-1, dp(48)));
        llmCard.addView(body("파인튜닝 학습 자료: assets/marine_finetune_dataset.jsonl · 권장 모델명: bluepath-marine-ft-v1"));
        content.addView(llmCard);

        content.addView(sectionTitle("계정·앱 관리"));
        LinearLayout account = card();
        account.addView(body("BluePath prototype · 로컬 프로필 저장 · YouTube 외부 링크 사용"));
        Button reset = outlineButton("프로필과 학습 기록 초기화");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("모든 기록을 초기화할까요?")
                .setMessage("프로필, XP, 승급 티어, 퀴즈 기록, 찜, LLM 설정이 삭제됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (dialog, which) -> {
                    store.reset();
                    clearQuizSession();
                    showOnboarding();
                }).show());
        account.addView(reset);
        content.addView(account);
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
        int score = RecommendationEngine.scoreContent(item, p, tier);
        boolean completed = store.getCompletedContentIds().contains(item.id);
        LinearLayout card = card();
        card.addView(label(item.difficulty + " 난도 · " + PromotionRules.displayName(item.requiredTier) + " 권장 · 적합도 " + score));
        card.addView(big("▶ " + item.title));
        String duration = item.minutes > 0 ? " · " + item.minutes + "분" : "";
        card.addView(body("출처: " + item.source + duration));
        card.addView(body("분야: " + item.topic + " · 연결 진로: " + item.careerTag));
        if (completed) card.addView(note("학습 완료", SUCCESS));
        LinearLayout row = row();
        Button watch = primaryButton(compact ? "보기" : (completed ? "다시 보기" : "영상 보기"));
        watch.setOnClickListener(v -> {
            if (!store.getCompletedContentIds().contains(item.id)) {
                store.markCompleted(item.id);
                store.addXp(item.difficulty.equals("하") ? 80 : item.difficulty.equals("중") ? 120 : 180);
            }
            openUrl(item.url);
        });
        Button save = outlineButton(store.isBookmarked(item.id) ? "찜 해제" : "찜");
        save.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            toast(store.isBookmarked(item.id) ? "찜 목록에 저장했습니다." : "찜을 해제했습니다.");
            showApp(currentTab);
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(44), 1);
        left.setMargins(0, 0, dp(6), 0);
        row.addView(watch, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(44), 1);
        right.setMargins(dp(6), 0, 0, 0);
        row.addView(save, right);
        card.addView(row);
        content.addView(card);
    }

    private void addProgramCard(ProgramItem item) {
        UserProfile p = store.getProfile();
        int score = RecommendationEngine.scoreProgram(item, p);
        LinearLayout card = card();
        card.addView(label(item.topic + " · 추천 " + score + "점"));
        card.addView(big("📚 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate + " · " + item.target + " · " + item.method));
        card.addView(body(item.description));
        Button b = outlineButton(store.isBookmarked(item.id) ? "일정 찜 해제" : "일정 찜하기");
        b.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            toast("일정 찜 목록을 업데이트했습니다.");
            showApp(currentTab);
        });
        card.addView(b);
        content.addView(card);
    }

    private void addEventCard(EventItem item) {
        LinearLayout card = card();
        card.addView(label(item.category + " · " + item.target));
        card.addView(big("🎪 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate));
        card.addView(body(item.description));
        Button b = outlineButton(store.isBookmarked(item.id) ? "이벤트 찜 해제" : "이벤트 찜하기");
        b.setOnClickListener(v -> {
            store.toggleBookmark(item.id);
            toast("이벤트 찜 목록을 업데이트했습니다.");
            showApp(currentTab);
        });
        card.addView(b);
        content.addView(card);
    }

    private void addCareerCard(CareerItem item) {
        UserProfile p = store.getProfile();
        int score = RecommendationEngine.scoreCareer(item, p);
        LinearLayout card = card();
        card.addView(label(item.field + " · 권장 " + PromotionRules.displayName(item.recommendedTier) + " · 적합도 " + score));
        card.addView(big("🧭 " + item.title));
        card.addView(body(item.description));
        card.addView(label("필요 역량 / NCS"));
        card.addView(body(join(item.ncsUnits, " → ")));
        card.addView(label("관련 근무지"));
        card.addView(body(join(item.workplaces, ", ")));
        card.addView(body("추천 로드맵: 관련 영상 학습 → 승급 퀴즈 → 교육 일정 찜 → NCS 역량 강화 → 자격·현장 경험"));
        content.addView(card);
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
