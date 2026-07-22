package com.bluepath.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a shareable PDF from evidence already verified or recorded by BluePath.
 * The document never upgrades local evidence to institution approval; each status is shown as stored.
 */
public final class PortfolioPdfExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 42;

    private PortfolioPdfExporter() {}

    public static File export(Context context, PortfolioData data) throws IOException {
        File directory = new File(context.getCacheDir(), "portfolio");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("포트폴리오 임시 폴더를 만들 수 없습니다.");
        }
        File target = new File(directory, "bluepath_ocean_skill_portfolio.pdf");

        PdfDocument document = new PdfDocument();
        PdfWriter writer = new PdfWriter(document);
        try {
            writer.heading("BLUEPATH OCEAN SKILL PORTFOLIO");
            writer.title(data.nickname + "님의 해양 역량 포트폴리오");
            writer.paragraph("앱에서 검증된 학습 완료, 퀴즈 결과, 현장 미션과 제출 상태를 하나의 증거 기반 문서로 정리했습니다.");
            writer.infoRow("생성 시각", data.generatedAt);
            writer.infoRow(data.serverVerified ? "서버 자격 증명 ID" : "로컬 초안 코드", data.verificationCode);
            writer.infoRow("통합 티어", data.tier + " · XP " + data.xp);
            writer.infoRow("목표 진로", data.targetCareer + " · 준비도 " + data.careerReadiness + "점");
            writer.infoRow("학습자 프로필", data.ageGroup + " · " + data.interest + " · " + data.goal + " · " + data.level);
            writer.note(data.serverVerified
                    ? "이 문서는 BluePath 서버의 권위 있는 진척도 원장에서 발급됐습니다. 아래 QR 또는 검증 URL에서 서명과 취소 상태를 확인할 수 있습니다."
                    : "이 문서는 로그인하지 않은 로컬 초안이며 외부 검증 수단이 아닙니다. 서버 자격 증명 없이 기관 제출용으로 사용하지 마세요.");
            if (data.serverVerified) {
                writer.infoRow("발급 시각", data.credentialIssuedAt);
                writer.infoRow("서명", data.signature);
                writer.paragraph("검증 URL: " + data.verifyUrl);
                writer.qrCode(data.verifyUrl);
            }

            writer.section("역량 스코어맵");
            for (Map.Entry<String, Integer> entry : data.mastery.entrySet()) {
                String topic = entry.getKey();
                int evidence = data.evidence.getOrDefault(topic, 0);
                SkillProfileCatalog.SkillDescriptor descriptor = SkillProfileCatalog.descriptor(topic);
                writer.skillRow(topic, entry.getValue(), evidence, descriptor.ncsCompetencies, descriptor.career);
            }

            writer.section("검증된 학습 증거");
            writer.bullets(data.learningEvidence, "아직 완료 인증된 학습이 없습니다.");

            writer.section("현장 미션과 배지");
            writer.bullets(data.missionEvidence, "아직 검증된 현장 미션 배지가 없습니다.");

            writer.section("퀴즈 및 승급 증거");
            writer.bullets(data.quizEvidence, "아직 퀴즈 응시 기록이 없습니다.");

            writer.section("다이아 증빙 상태");
            writer.bullets(data.diamondEvidence, "증빙 제출 상태가 없습니다.");

            writer.section("강점과 다음 항로");
            writer.paragraph("강점 역량: " + join(data.strongestTopics));
            writer.paragraph("우선 보완 역량: " + join(data.weakestTopics));
            if (!data.weakestTopics.isEmpty()) {
                SkillProfileCatalog.SkillDescriptor descriptor = SkillProfileCatalog.descriptor(data.weakestTopics.get(0));
                writer.paragraph("추천 다음 활동: " + descriptor.nextAction);
            }
            writer.note(data.serverVerified
                    ? "검증 페이지가 revoked로 표시되면 이 문서는 더 이상 유효하지 않습니다. QR URL과 서버 HMAC 서명을 함께 확인하세요."
                    : "로컬 초안 코드는 데이터 구분용 해시일 뿐, 서버 서명이나 기관 승인이 아닙니다.");

            writer.finish();
            try (FileOutputStream stream = new FileOutputStream(target)) {
                document.writeTo(stream);
            }
        } finally {
            document.close();
        }
        return target;
    }

    public static String verificationCode(String canonicalEvidence) {
        String value = canonicalEvidence == null ? "" : canonicalEvidence;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("BP-");
            for (int i = 0; i < 6; i++) builder.append(String.format(Locale.US, "%02X", bytes[i]));
            return builder.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return "BP-UNAVAILABLE";
        }
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "진단 전";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(value);
        }
        return builder.toString();
    }

    public static final class PortfolioData {
        public final String nickname;
        public final String generatedAt;
        public final String verificationCode;
        public final String tier;
        public final int xp;
        public final String targetCareer;
        public final int careerReadiness;
        public final String ageGroup;
        public final String interest;
        public final String goal;
        public final String level;
        public final Map<String, Integer> mastery;
        public final Map<String, Integer> evidence;
        public final List<String> learningEvidence;
        public final List<String> missionEvidence;
        public final List<String> quizEvidence;
        public final List<String> diamondEvidence;
        public final List<String> strongestTopics;
        public final List<String> weakestTopics;
        public final boolean serverVerified;
        public final String verifyUrl;
        public final String signature;
        public final String credentialIssuedAt;

        public PortfolioData(
                String nickname,
                String generatedAt,
                String verificationCode,
                String tier,
                int xp,
                String targetCareer,
                int careerReadiness,
                String ageGroup,
                String interest,
                String goal,
                String level,
                Map<String, Integer> mastery,
                Map<String, Integer> evidence,
                List<String> learningEvidence,
                List<String> missionEvidence,
                List<String> quizEvidence,
                List<String> diamondEvidence,
                List<String> strongestTopics,
                List<String> weakestTopics,
                boolean serverVerified,
                String verifyUrl,
                String signature,
                String credentialIssuedAt
        ) {
            this.nickname = safe(nickname, "BluePath Learner");
            this.generatedAt = safe(generatedAt, "-");
            this.verificationCode = safe(verificationCode, "BP-UNAVAILABLE");
            this.tier = safe(tier, "브론즈");
            this.xp = Math.max(0, xp);
            this.targetCareer = safe(targetCareer, "해양 진로 탐색");
            this.careerReadiness = clamp(careerReadiness);
            this.ageGroup = safe(ageGroup, "-");
            this.interest = safe(interest, "-");
            this.goal = safe(goal, "-");
            this.level = safe(level, "-");
            this.mastery = new LinkedHashMap<>(mastery == null ? new LinkedHashMap<>() : mastery);
            this.evidence = new LinkedHashMap<>(evidence == null ? new LinkedHashMap<>() : evidence);
            this.learningEvidence = copy(learningEvidence);
            this.missionEvidence = copy(missionEvidence);
            this.quizEvidence = copy(quizEvidence);
            this.diamondEvidence = copy(diamondEvidence);
            this.strongestTopics = copy(strongestTopics);
            this.weakestTopics = copy(weakestTopics);
            this.serverVerified = serverVerified;
            this.verifyUrl = safe(verifyUrl, "");
            this.signature = safe(signature, "");
            this.credentialIssuedAt = safe(credentialIssuedAt, "-");
        }

        public PortfolioData withCredential(String credentialId, String verifyUrl, String signature, String issuedAt) {
            return new PortfolioData(nickname, generatedAt, credentialId, tier, xp, targetCareer, careerReadiness,
                    ageGroup, interest, goal, level, mastery, evidence, learningEvidence, missionEvidence, quizEvidence,
                    diamondEvidence, strongestTopics, weakestTopics, true, verifyUrl, signature, issuedAt);
        }

        private static String safe(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }

        private static List<String> copy(List<String> values) {
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }

    private static final class PdfWriter {
        private final PdfDocument document;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNumber = 0;
        private float y;

        PdfWriter(PdfDocument document) {
            this.document = document;
            linePaint.setColor(Color.rgb(205, 225, 230));
            linePaint.setStrokeWidth(1f);
            newPage();
        }

        void heading(String value) {
            ensure(44);
            paint.setColor(Color.rgb(14, 116, 144));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(11);
            canvas.drawText(value, MARGIN, y, paint);
            y += 20;
        }

        void title(String value) {
            ensure(70);
            paint.setColor(Color.rgb(6, 34, 63));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(24);
            drawWrapped(value, PAGE_WIDTH - MARGIN * 2, 31);
            y += 5;
        }

        void section(String value) {
            ensure(45);
            y += 9;
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint);
            y += 22;
            paint.setColor(Color.rgb(6, 34, 63));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(15);
            canvas.drawText(value, MARGIN, y, paint);
            y += 12;
        }

        void paragraph(String value) {
            if (value == null || value.trim().isEmpty()) return;
            paint.setColor(Color.rgb(50, 72, 91));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(10.5f);
            drawWrapped(value.trim(), PAGE_WIDTH - MARGIN * 2, 16);
            y += 5;
        }

        void note(String value) {
            ensure(52);
            paint.setColor(Color.rgb(91, 110, 126));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            paint.setTextSize(9.3f);
            drawWrapped(value, PAGE_WIDTH - MARGIN * 2 - 14, 14);
            y += 7;
        }

        void qrCode(String value) {
            if (value == null || value.trim().isEmpty()) return;
            ensure(138);
            try {
                BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 120, 120);
                Bitmap bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
                for (int x = 0; x < 120; x++) {
                    for (int yy = 0; yy < 120; yy++) bitmap.setPixel(x, yy, matrix.get(x, yy) ? Color.BLACK : Color.WHITE);
                }
                canvas.drawBitmap(bitmap, MARGIN, y, paint);
                y += 128;
                bitmap.recycle();
            } catch (Exception ignored) {
                paragraph("QR 코드를 만들 수 없어 검증 URL을 사용해 주세요.");
            }
        }

        void infoRow(String label, String value) {
            ensure(24);
            paint.setTextSize(10);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.rgb(23, 50, 77));
            canvas.drawText(label, MARGIN, y, paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setColor(Color.rgb(71, 90, 106));
            canvas.drawText(value == null ? "-" : value, MARGIN + 92, y, paint);
            y += 18;
        }

        void skillRow(String topic, int score, int evidence, String competencies, String career) {
            ensure(72);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(11);
            paint.setColor(Color.rgb(14, 116, 144));
            canvas.drawText(topic + "  " + score + "점", MARGIN, y, paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(9.2f);
            paint.setColor(Color.rgb(82, 100, 115));
            canvas.drawText("증거 " + evidence + "개", MARGIN + 170, y, paint);
            y += 15;
            drawWrapped("NCS 연계: " + competencies + " · 연결 진로: " + career,
                    PAGE_WIDTH - MARGIN * 2, 13);
            y += 5;
        }

        void bullets(List<String> values, String emptyMessage) {
            if (values == null || values.isEmpty()) {
                paragraph(emptyMessage);
                return;
            }
            for (String value : values) {
                paint.setColor(Color.rgb(50, 72, 91));
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setTextSize(10);
                ensure(24);
                canvas.drawText("•", MARGIN + 2, y, paint);
                float initialY = y;
                drawWrapped(value, PAGE_WIDTH - MARGIN * 2 - 18, 15, MARGIN + 16);
                if (y == initialY) y += 15;
                y += 3;
            }
        }

        void finish() {
            if (page != null) {
                drawFooter();
                document.finishPage(page);
                page = null;
            }
        }

        private void newPage() {
            if (page != null) {
                drawFooter();
                document.finishPage(page);
            }
            pageNumber++;
            page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
            canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            y = MARGIN;
        }

        private void ensure(float required) {
            if (y + required > PAGE_HEIGHT - MARGIN - 24) newPage();
        }

        private void drawWrapped(String text, float maxWidth, float lineHeight) {
            drawWrapped(text, maxWidth, lineHeight, MARGIN);
        }

        private void drawWrapped(String text, float maxWidth, float lineHeight, float x) {
            if (text == null) return;
            String remaining = text.trim();
            while (!remaining.isEmpty()) {
                ensure(lineHeight + 4);
                int count = paint.breakText(remaining, true, maxWidth, null);
                if (count <= 0) break;
                if (count < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', count - 1);
                    if (space > 0 && space > count / 2) count = space;
                }
                String line = remaining.substring(0, count).trim();
                canvas.drawText(line, x, y, paint);
                y += lineHeight;
                remaining = remaining.substring(count).trim();
            }
        }

        private void drawFooter() {
            paint.setColor(Color.rgb(130, 145, 158));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(8.5f);
            canvas.drawText("BluePath evidence portfolio · " + pageNumber, MARGIN, PAGE_HEIGHT - 24, paint);
        }
    }
}
