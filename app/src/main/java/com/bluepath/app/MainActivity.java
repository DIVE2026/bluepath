package com.bluepath.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.bluepath.app.util.RecommendationEngine;

import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private final int NAVY = Color.parseColor("#06223F");
    private final int CYAN = Color.parseColor("#18D6D2");
    private final int BG = Color.parseColor("#F3FAFA");
    private final int CARD = Color.WHITE;
    private final int TEXT = Color.parseColor("#17324D");
    private final int MUTED = Color.parseColor("#64748B");

    private UserStore store;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new UserStore(this);
        if (!store.hasProfile()) {
            showOnboarding();
        } else {
            showApp(0);
        }
    }

    private void showOnboarding() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(22));
        root.setBackgroundColor(BG);
        setContentView(root);

        TextView title = title("BluePath");
        title.setText("BluePath\n스마트 해도 AI");
        root.addView(title);
        root.addView(body("해양 교육기관과 현장에서 권장할 수 있는 전 연령 맞춤형 모바일 해양 인재 양성 플랫폼입니다. 먼저 관심과 수준을 진단합니다."));

        Spinner age = spinner(new String[]{"초등학생", "중학생", "고등학생", "대학생", "성인", "직장인", "학부모/가족"});
        Spinner interest = spinner(new String[]{"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"});
        Spinner goal = spinner(new String[]{"흥미", "체험", "진로탐색", "자격증", "직무역량 강화", "가족 교육"});
        Spinner level = spinner(new String[]{"입문", "기초", "중급", "심화", "실무"});

        root.addView(label("연령대")); root.addView(age);
        root.addView(label("관심 분야")); root.addView(interest);
        root.addView(label("학습 목적")); root.addView(goal);
        root.addView(label("현재 수준")); root.addView(level);

        Button start = primaryButton("나의 해양 인재 DNA 진단하기");
        start.setOnClickListener(v -> {
            String a = age.getSelectedItem().toString();
            String i = interest.getSelectedItem().toString();
            String g = goal.getSelectedItem().toString();
            String l = level.getSelectedItem().toString();
            String persona = RecommendationEngine.persona(a, g, i);
            store.saveProfile(new UserProfile(a, i, g, l, persona, 0));
            showApp(0);
        });
        root.addView(start);
    }

    private void showApp(int tab) {
        currentTab = tab;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(NAVY);
        header.setPadding(dp(18), dp(18), dp(18), dp(14));
        TextView h = new TextView(this);
        h.setText("⚓ BluePath");
        h.setTextColor(Color.WHITE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(h);
        TextView sub = new TextView(this);
        sub.setText("해양 학습 · 티어 성장 · 진로 로드맵");
        sub.setTextColor(Color.parseColor("#BFFBFA"));
        sub.setTextSize(13);
        sub.setPadding(0, dp(4), 0, 0);
        header.addView(sub);
        root.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));
        ScrollView scroll = new ScrollView(this);
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
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTextColor(tab == currentTab ? NAVY : MUTED);
        b.setBackgroundColor(tab == currentTab ? Color.parseColor("#DFFBFA") : Color.TRANSPARENT);
        b.setOnClickListener(v -> {
            currentTab = tab;
            renderTab(tab);
            showApp(tab);
        });
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
        int xp = p.xp;
        int base = UserStore.tierBaseXp(tier);
        int next = UserStore.nextTierXp(tier);
        int progress = tier.equals("다이아") ? 100 : Math.min(100, Math.max(0, (xp - base) * 100 / Math.max(1, next - base)));

        content.addView(sectionTitle("오늘의 스마트 해도"));
        LinearLayout card = card();
        card.addView(big("현재 유형: " + p.persona));
        card.addView(body("관심 분야: " + p.interest + " · 목적: " + p.goal + " · 수준: " + p.level));
        card.addView(body("현재 티어: " + tier + " · XP " + xp));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); bar.setProgress(progress);
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(16)));
        card.addView(body("다음 행동: " + RecommendationEngine.nextAction(p, tier)));
        content.addView(card);

        content.addView(sectionTitle("AI 추천 TOP 3"));
        List<ContentItem> items = RecommendationEngine.recommendedContents(p, tier);
        for (int i = 0; i < Math.min(3, items.size()); i++) addContentCard(items.get(i), true);

        content.addView(sectionTitle("곧 확인할 교육 일정"));
        List<ProgramItem> programs = RecommendationEngine.recommendedPrograms(p);
        for (int i = 0; i < Math.min(2, programs.size()); i++) addProgramCard(programs.get(i));
    }

    private void renderLearning() {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("난도별 해양 영상"));
        content.addView(body("공식·공공기관 채널 검색 결과 기반 seed 영상입니다. 하/중/상 난도와 티어 기준에 따라 추천됩니다."));
        for (ContentItem item : RecommendationEngine.recommendedContents(p, tier)) addContentCard(item, false);
    }

    private void renderQuiz() {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("승급 퀴즈"));
        content.addView(body("영상을 모두 보지 않아도 이해도를 증명하면 게이지가 빠르게 찹니다. 현재 티어: " + tier));
        List<QuizQuestion> quiz = RecommendationEngine.quizForTier(tier, p.interest);
        int count = Math.min(5, quiz.size());
        for (int i = 0; i < count; i++) addQuizCard(quiz.get(i));
    }

    private void renderSchedule() {
        UserProfile p = store.getProfile();
        content.addView(sectionTitle("교육 프로그램 스케줄"));
        for (ProgramItem item : RecommendationEngine.recommendedPrograms(p)) addProgramCard(item);
        content.addView(sectionTitle("이벤트·영화·공연"));
        for (EventItem e : RecommendationEngine.recommendedEvents(p)) addEventCard(e);
    }

    private void renderCareer() {
        UserProfile p = store.getProfile();
        content.addView(sectionTitle("NCS 기반 진로 찾기"));
        content.addView(body("관심 분야와 티어를 기준으로 직무, 필요한 역량, 관련 근무지, 연결 콘텐츠를 단계별로 보여줍니다."));
        for (CareerItem c : RecommendationEngine.recommendedCareers(p)) addCareerCard(c);
    }

    private void renderAgent() {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("BluePath AI Agent"));
        content.addView(body("교육 추천, 일정 확인, 티어 승급, 퀴즈, 진로 로드맵을 물어보세요. 현재 버전은 오프라인 룰 기반 Agent이며, FastAPI/LLM API로 교체할 수 있도록 README에 API 계약을 포함했습니다."));
        EditText input = new EditText(this);
        input.setHint("예: 항해사가 되려면 어떤 역량이 필요해?");
        input.setMinLines(3);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        TextView answer = body("질문을 입력하고 상담 받기를 누르세요.");
        Button ask = primaryButton("상담 받기");
        ask.setOnClickListener(v -> answer.setText(RecommendationEngine.answerAgent(input.getText().toString(), p, tier)));
        content.addView(ask);
        LinearLayout answerCard = card();
        answerCard.addView(answer);
        content.addView(answerCard);

        String[] chips = {"내 티어 올리려면?", "이번 달 교육 추천", "항해사 진로 알려줘", "퀴즈 풀고 싶어", "영상 추천해줘"};
        for (String chip : chips) {
            Button b = outlineButton(chip);
            b.setOnClickListener(v -> {
                input.setText(chip);
                answer.setText(RecommendationEngine.answerAgent(chip, p, tier));
            });
            content.addView(b);
        }
    }

    private void renderMyPage() {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        content.addView(sectionTitle("마이페이지"));
        LinearLayout card = card();
        card.addView(big("" + tier + " · " + p.persona));
        card.addView(body("연령대: " + p.ageGroup));
        card.addView(body("관심 분야: " + p.interest));
        card.addView(body("학습 목적: " + p.goal));
        card.addView(body("수준: " + p.level));
        card.addView(body("XP: " + p.xp));
        content.addView(card);

        Set<String> completed = store.getCompletedContentIds();
        Set<String> bookmarks = store.getBookmarks();
        content.addView(sectionTitle("활동 기록"));
        content.addView(body("수강 완료 콘텐츠: " + completed.size() + "개"));
        content.addView(body("찜한 교육·콘텐츠: " + bookmarks.size() + "개"));
        content.addView(body("추천 다음 행동: " + RecommendationEngine.nextAction(p, tier)));

        Button reset = outlineButton("프로필 초기화");
        reset.setOnClickListener(v -> { store.reset(); showOnboarding(); });
        content.addView(reset);
    }

    private void addContentCard(ContentItem item, boolean compact) {
        UserProfile p = store.getProfile();
        String tier = store.getTier();
        int score = RecommendationEngine.scoreContent(item, p, tier);
        LinearLayout card = card();
        card.addView(big("▶ " + item.title));
        card.addView(body("출처: " + item.source + " · 난도 " + item.difficulty + " · 권장 " + item.requiredTier + " · " + item.minutes + "분"));
        card.addView(body("분야: " + item.topic + " · 연결 진로: " + item.careerTag));
        card.addView(body("추천 점수: " + score + "점"));
        LinearLayout row = row();
        Button watch = primaryButton(compact ? "보기" : "영상 보기");
        watch.setOnClickListener(v -> {
            store.markCompleted(item.id);
            store.addXp(item.difficulty.equals("하") ? 80 : item.difficulty.equals("중") ? 120 : 180);
            openUrl(item.url);
        });
        Button save = outlineButton("찜");
        save.setOnClickListener(v -> { store.toggleBookmark(item.id); toast("찜 목록이 업데이트되었습니다."); });
        row.addView(watch, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        card.addView(row);
        content.addView(card);
    }

    private void addProgramCard(ProgramItem item) {
        UserProfile p = store.getProfile();
        int score = RecommendationEngine.scoreProgram(item, p);
        LinearLayout card = card();
        card.addView(big("📚 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate + " · " + item.target + " · " + item.method));
        card.addView(body("분야: " + item.topic + " · 추천 점수: " + score + "점"));
        card.addView(body(item.description));
        Button b = outlineButton("일정 찜하기");
        b.setOnClickListener(v -> { store.toggleBookmark(item.id); toast("일정이 찜 목록에 저장되었습니다."); });
        card.addView(b);
        content.addView(card);
    }

    private void addEventCard(EventItem item) {
        LinearLayout card = card();
        card.addView(big("🎪 " + item.title));
        card.addView(body(item.startDate + " ~ " + item.endDate + " · " + item.category + " · " + item.target));
        card.addView(body(item.description));
        Button b = outlineButton("이벤트 찜하기");
        b.setOnClickListener(v -> { store.toggleBookmark(item.id); toast("이벤트가 찜 목록에 저장되었습니다."); });
        card.addView(b);
        content.addView(card);
    }

    private void addCareerCard(CareerItem item) {
        UserProfile p = store.getProfile();
        int score = RecommendationEngine.scoreCareer(item, p);
        LinearLayout card = card();
        card.addView(big("🧭 " + item.title));
        card.addView(body(item.field + " · 권장 티어: " + item.recommendedTier + " · 적합도: " + score + "점"));
        card.addView(body(item.description));
        card.addView(label("필요 역량/NCS"));
        card.addView(body(join(item.ncsUnits, " → ")));
        card.addView(label("관련 근무지"));
        card.addView(body(join(item.workplaces, ", ")));
        card.addView(body("추천 로드맵: 기초 영상 → 승급 퀴즈 → 관련 교육 일정 찜 → NCS 역량 카드 확인 → 자격·현장 경험 인증"));
        content.addView(card);
    }

    private void addQuizCard(QuizQuestion q) {
        LinearLayout card = card();
        card.addView(big("Q. " + q.question));
        card.addView(body("티어: " + q.tier + " · 분야: " + q.topic));
        for (int i = 0; i < q.options.length; i++) {
            final int idx = i;
            Button opt = outlineButton((i + 1) + ". " + q.options[i]);
            opt.setOnClickListener(v -> {
                boolean ok = idx == q.answerIndex;
                int xp = ok ? (q.tier.equals("브론즈") ? 120 : q.tier.equals("실버") ? 160 : 220) : 25;
                store.addXp(xp);
                toast((ok ? "정답! " : "오답. ") + q.explanation + " +" + xp + "XP");
                showApp(2);
            });
            card.addView(opt);
        }
        content.addView(card);
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setPadding(0, dp(4), 0, dp(12));
        return s;
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(NAVY);
        v.setTextSize(20);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(10), 0, dp(8));
        return v;
    }

    private TextView title(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(NAVY);
        v.setTextSize(28);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(8), 0, dp(12));
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
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(10), 0, dp(4));
        return v;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        l.setBackgroundColor(CARD);
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
        b.setBackgroundColor(NAVY);
        return b;
    }

    private Button outlineButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(NAVY);
        b.setTextSize(14);
        b.setBackgroundColor(Color.parseColor("#E8F7F7"));
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

}
