package com.example.pcaExamAnalyze;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamPublicationState;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.domain.McqSubmissionStatus;
import com.example.pcaExamAnalyze.report.ExamReport;
import com.example.pcaExamAnalyze.report.ExamReportService;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.service.McqAdminService;
import com.example.pcaExamAnalyze.service.McqStudentExamService;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database-level checks for the queries that used to paginate over a collection fetch (HHH90003004) or load every
 * submission with its answers. Runs the real app on H2 with fail_on_pagination_over_collection_fetch enabled and
 * inspects the generated SQL, so in-memory pagination would fail the test instead of only logging a warning.
 */
class SubmissionQueryRegressionTest {

    private static final ZoneId ZONE = McqAdminService.PCA_ZONE;
    private static final Pattern LIMITED = Pattern.compile("(?i)\\b(limit|offset|fetch\\s+first)\\b");
    private static final String NIC = "200000000009";
    private static final String TEACHER = "regression-teacher";
    private static final String TEACHER_PASSWORD = "regression-pass-1";

    private static ConfigurableApplicationContext ctx;
    private static TransactionTemplate tx;
    private static McqExam examA;
    private static McqExam examB;
    private static final List<Row> rows = new ArrayList<>();
    private static McqSubmission latestTie;

    /** The fields the old in-memory pager filtered and sorted on. */
    private record Row(long id, long examId, String name, String registrationId, Instant createdAt, Instant submittedAt) {}

    @BeforeAll
    static void start() {
        ctx = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run("--spring.profiles.active=dev",
                "--server.port=0", "--spring.datasource.url=jdbc:h2:mem:submissionqueries;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--logging.level.root=ERROR", "--pca.teacher.username=" + TEACHER, "--pca.teacher.password=" + TEACHER_PASSWORD,
                "--spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
                "--spring.jpa.properties.hibernate.session_factory.statement_inspector=" + SqlCapture.class.getName());
        tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
        tx.executeWithoutResult(status -> seed());
    }

    @AfterAll
    static void stop() {
        if (ctx != null) ctx.close();
    }

    private static void seed() {
        var examRepo = ctx.getBean(McqExamRepository.class);
        examA = examRepo.save(exam("regression-a"));
        examB = examRepo.save(exam("regression-b"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDate firstDay = LocalDate.now(ZONE).minusDays(10);
        Instant firstCreated = now.minus(5, ChronoUnit.DAYS);
        for (int i = 0; i < 45; i++) {
            // Every 7th paper is still in progress (no submitted time); every three papers share a created time.
            Instant submitted = i % 7 == 6 ? null
                    : firstDay.plusDays(i % 5).atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant();
            String name = i == 3 ? "100%_Pass Student" : i == 4 ? "Under_score" : "Student " + i;
            save(examA, name, "REG" + i, firstCreated.plusSeconds(60L * (i / 3)), submitted, Map.of(1, 1, 2, 2, 3, 3, 4, 1));
        }
        // Day boundaries in the PCA time zone: the last second of a day and the first second of the next.
        LocalDate boundary = firstDay.plusDays(2);
        save(examA, "Boundary Late", "EDGE1", firstCreated.plusSeconds(3600),
                boundary.atTime(LocalTime.of(23, 59, 59)).atZone(ZONE).toInstant(), Map.of(1, 1));
        save(examA, "Boundary Early", "EDGE2", firstCreated.plusSeconds(3600),
                boundary.plusDays(1).atStartOfDay(ZONE).toInstant(), Map.of(1, 1));
        // Recent papers for the last-24-hours dashboard counts (and one just outside the window).
        save(examA, "Recent A", "RECENT1", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), Map.of(1, 1));
        save(examA, "Old A", "RECENT2", now.minus(26, ChronoUnit.HOURS), now.minus(25, ChronoUnit.HOURS), Map.of(1, 1));
        // One student with three papers for exam A; the two newest share a created time (id breaks the tie).
        Instant tieTime = now.minus(3, ChronoUnit.HOURS);
        save(examA, "Tie Student", "TIE1", tieTime.minus(1, ChronoUnit.HOURS), tieTime, Map.of(1, 2));
        save(examA, "Tie Student", "TIE1", tieTime, tieTime.plusSeconds(10), Map.of(1, 3));
        latestTie = save(examA, "Tie Student", "TIE1", tieTime, tieTime.plusSeconds(20), Map.of(1, 4, 2, 2));
        // Exam B: key is option 1 everywhere; correct counts 4, 3, 3, 1, 0 must rank 1, 2, 2, 4, 5.
        int[][] papers = {{1, 1, 1, 1}, {1, 1, 1, 2}, {2, 1, 1, 1}, {1, 2, 2, 2}, {2, 2, 2, 2}};
        for (int i = 0; i < papers.length; i++) {
            var answers = new java.util.LinkedHashMap<Integer, Integer>();
            for (int q = 0; q < 4; q++) answers.put(q + 1, papers[i][q]);
            save(examB, "Ranked " + i, "RANK" + i, now.minus(30 - i, ChronoUnit.MINUTES),
                    now.minus(20 - i, ChronoUnit.MINUTES), answers);
        }
    }

    private static McqExam exam(String slug) {
        var exam = new McqExam();
        exam.setName("Regression " + slug);
        exam.setSlug(slug);
        exam.setExamYear(2028);
        exam.setExamMonth("June");
        exam.setPaperDriveUrl("");
        exam.setOpenAt(Instant.now().minus(30, ChronoUnit.DAYS));
        exam.setCloseAt(Instant.now().plus(30, ChronoUnit.DAYS));
        exam.setResultReleaseAt(Instant.now());
        exam.setTotalQuestions(4);
        exam.setResultsPublished(true);
        exam.setPublicationState(McqExamPublicationState.PUBLISHED);
        for (int q = 1; q <= 4; q++) exam.getAnswerKey().put(q, 1);
        return exam;
    }

    private static McqSubmission save(McqExam exam, String name, String registrationId, Instant created,
                                      Instant submitted, Map<Integer, Integer> answers) {
        var s = new McqSubmission();
        s.setExam(exam);
        s.setReceiptNumber("RG-" + registrationId + "-" + rows.size());
        s.setStudentName(name);
        s.setRegistrationId(registrationId);
        s.setNic(NIC);
        s.setEmail("regression@example.test");
        s.setBatch("2028 A/L");
        s.setSchool("");
        s.setStream("Physical Science");
        s.setDistrict("Jaffna");
        s.setCreatedAt(created);
        s.setStartedAt(created);
        s.setSubmittedAt(submitted);
        s.setStatus(submitted == null ? McqSubmissionStatus.IN_PROGRESS : McqSubmissionStatus.SUBMITTED);
        s.getAnswers().putAll(answers);
        s = ctx.getBean(McqSubmissionRepository.class).save(s);
        rows.add(new Row(s.getId(), exam.getId(), name, registrationId, created, submitted));
        return s;
    }

    // ------------------------------------------------------------------ admin submission page

    @Test
    void submissionPageMatchesTheInMemoryPagerForEveryFilterAndBoundary() {
        var admin = ctx.getBean(McqAdminService.class);
        LocalDate firstDay = LocalDate.now(ZONE).minusDays(10);
        LocalDate[][] ranges = {{null, null}, {firstDay.plusDays(1), null}, {null, firstDay.plusDays(2)},
                {firstDay.plusDays(2), firstDay.plusDays(2)}, {firstDay.plusDays(3), firstDay.plusDays(3)},
                {firstDay.plusDays(4), firstDay.plusDays(1)}};
        int checked = 0;
        for (Long examId : new Long[]{null, examA.getId(), examB.getId()}) {
            for (String search : new String[]{"", "  stud ", "REG1", "%", "_", "zzz", "100%_"}) {
                for (LocalDate[] range : ranges) {
                    for (int page : new int[]{-1, 0, 1, 2, 99}) {
                        var actual = admin.submissionPage(examId, search, range[0], range[1], page);
                        var expected = legacyPage(examId, search, range[0], range[1], page);
                        String where = "exam=" + examId + " search='" + search + "' range=" + Arrays.toString(range) + " page=" + page;
                        assertEquals(expected.ids(), actual.items().stream().map(McqAdminService.SubmissionRow::id).toList(), where);
                        assertEquals(expected.page(), actual.page(), "page " + where);
                        assertEquals(expected.totalPages(), actual.totalPages(), "totalPages " + where);
                        assertEquals(expected.total(), actual.totalItems(), "total " + where);
                        assertEquals(expected.page() > 0, actual.hasPrevious(), "hasPrevious " + where);
                        assertEquals(expected.page() + 1 < expected.totalPages(), actual.hasNext(), "hasNext " + where);
                        assertEquals(20, actual.pageSize());
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 500);
    }

    @Test
    void emptySubmissionPage() {
        var page = ctx.getBean(McqAdminService.class).submissionPage(null, "no such student", null, null, 3);
        assertTrue(page.items().isEmpty());
        assertEquals(0, page.page());
        assertEquals(0, page.totalPages());
        assertEquals(0, page.totalItems());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void submissionPageIsLimitedInTheDatabaseWithoutAnswerRows() {
        SqlCapture.STATEMENTS.clear();
        var page = ctx.getBean(McqAdminService.class).submissionPage(null, "", null, null, 1);
        assertEquals(20, page.items().size());
        List<String> sql = List.copyOf(SqlCapture.STATEMENTS);
        String select = sql.stream().filter(s -> s.contains("mcq_submissions") && s.contains("student_name")).findFirst()
                .orElseThrow(() -> new AssertionError("No submission select in " + sql));
        assertTrue(LIMITED.matcher(select).find(), "page select must carry LIMIT/OFFSET: " + select);
        assertTrue(select.contains("join mcq_exams"), "exam must be fetched in the same select (no N+1): " + select);
        assertTrue(sql.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains("count(")), "count query expected");
        assertTrue(sql.stream().noneMatch(s -> s.contains("mcq_submission_answers")), "answers must not be loaded: " + sql);
        assertTrue(sql.size() <= 3, "expected page + count queries only, got " + sql.size() + ": " + sql);
    }

    private record LegacyPage(List<Long> ids, int page, int totalPages, long total) {}

    /** The pre-fix algorithm: filter in Java, newest first (id breaks created-time ties), clamp the page, slice. */
    private static LegacyPage legacyPage(Long examId, String search, LocalDate from, LocalDate to, int requestedPage) {
        String needle = search == null ? "" : search.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        List<Row> filtered = rows.stream()
                .filter(r -> examId == null || r.examId() == examId)
                .filter(r -> needle.isBlank() || r.name().toLowerCase(Locale.ROOT).contains(needle)
                        || r.registrationId().toLowerCase(Locale.ROOT).contains(needle))
                .filter(r -> legacyDateMatches(r.submittedAt(), from, to))
                .sorted(Comparator.comparing(Row::createdAt).thenComparing(Row::id).reversed())
                .toList();
        int size = 20;
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size;
        int page = totalPages == 0 ? 0 : Math.max(0, Math.min(requestedPage, totalPages - 1));
        int start = Math.min(page * size, filtered.size());
        int end = Math.min(start + size, filtered.size());
        return new LegacyPage(filtered.subList(start, end).stream().map(Row::id).toList(), page, totalPages, filtered.size());
    }

    private static boolean legacyDateMatches(Instant instant, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        if (instant == null) return false;
        LocalDate date = instant.atZone(ZONE).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    // ------------------------------------------------------------------ latest paper of a student (was HHH90003004)

    @Test
    void latestStudentPaperUsesDatabaseLimitAndBreaksCreatedTimeTiesById() {
        var repo = ctx.getBean(McqSubmissionRepository.class);
        tx.executeWithoutResult(status -> {
            SqlCapture.STATEMENTS.clear();
            var found = repo.findFirstByExamIdAndRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDescIdDesc(
                    examA.getId(), "tie1", NIC).orElseThrow();
            String select = SqlCapture.STATEMENTS.getFirst();
            assertTrue(LIMITED.matcher(select).find(), "LIMIT must reach the database: " + select);
            assertFalse(select.contains("mcq_submission_answers"), "answers must not be joined: " + select);
            assertEquals(latestTie.getId(), found.getId());
            assertEquals(Map.of(1, 4, 2, 2), Map.copyOf(found.getAnswers()), "answers still load lazily");
        });
        assertTrue(repo.findFirstByExamIdAndRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDescIdDesc(
                examB.getId(), "TIE1", NIC).isEmpty());
    }

    @Test
    void studentResultAndResumeReturnTheLatestPaper() {
        var students = ctx.getBean(McqStudentExamService.class);
        var result = students.result(examA.getSlug(), "tie1", NIC);
        assertEquals(latestTie.getReceiptNumber(), result.receipt());
        // Latest paper answered 4 and 2 against a key of 1: both marked from that paper, not an older one.
        assertEquals(4, result.answers().get(0).selected());
        assertEquals(2, result.answers().get(1).selected());
        assertEquals("INCORRECT", result.answers().get(0).state());
        assertNull(result.answers().get(2).selected());

        var details = new ExamStudentDetailsForm();
        details.setRegistrationId("TIE1");
        details.setNic(NIC);
        var resumed = students.startOrResume(examA.getSlug(), details);
        assertEquals(latestTie.getId(), resumed.getId());
        assertEquals(McqSubmissionStatus.SUBMITTED, resumed.getStatus());
    }

    // ------------------------------------------------------------------ admin routes over HTTP

    @Test
    void adminDashboardSubmissionsCsvAndPdfRenderForTheTeacher() throws Exception {
        String base = "http://localhost:" + ((org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext) ctx)
                .getWebServer().getPort();
        var http = java.net.http.HttpClient.newBuilder()
                .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
        var login = http.send(get(base + "/exam/admin-login"), java.net.http.HttpResponse.BodyHandlers.ofString());
        String csrf = org.jsoup.Jsoup.parse(login.body()).selectFirst("input[name=_csrf]").attr("value");
        http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("username=" + TEACHER + "&password="
                        + TEACHER_PASSWORD + "&_csrf=" + csrf)).build(), java.net.http.HttpResponse.BodyHandlers.discarding());

        var dashboard = http.send(get(base + "/exam/admin"), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, dashboard.statusCode());

        var page = http.send(get(base + "/exam/admin/submissions?q=stud&page=1&examId=" + examA.getId()),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        var expected = legacyPage(examA.getId(), "stud", null, null, 1);
        String body = page.body();
        for (long id : expected.ids()) {
            String registrationId = rows.stream().filter(r -> r.id() == id).findFirst().orElseThrow().registrationId();
            assertTrue(body.contains(registrationId), "page 2 should list " + registrationId);
        }

        var csv = http.send(get(base + "/exam/admin/submissions/export.csv?examId=" + examA.getId()),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csv.statusCode());
        long examARows = rows.stream().filter(r -> r.examId() == examA.getId()).count();
        assertEquals(examARows + 1, csv.body().lines().count(), "header plus one line per paper");

        var pdf = http.send(get(base + "/exam/admin/exams/" + examB.getId() + "/report.pdf"),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pdf.statusCode());
        assertEquals("%PDF", new String(pdf.body(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static java.net.http.HttpRequest get(String url) {
        return java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build();
    }

    // ------------------------------------------------------------------ dashboard and ranking

    @Test
    void dashboardCountsLast24HoursInTheDatabase() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Map<Long, Long> expected = new java.util.HashMap<>();
        rows.stream().filter(r -> r.submittedAt() != null && !r.submittedAt().isBefore(since))
                .forEach(r -> expected.merge(r.examId(), 1L, Long::sum));
        SqlCapture.STATEMENTS.clear();
        var dashboard = ctx.getBean(McqAdminService.class).dashboard();
        assertEquals(expected.values().stream().mapToLong(Long::longValue).sum(), dashboard.last24Submissions());
        for (var count : dashboard.submissionCounts()) assertEquals(expected.get(count.examId()), count.count());
        assertEquals(expected.size(), dashboard.submissionCounts().size());
        assertTrue(SqlCapture.STATEMENTS.stream().noneMatch(s -> s.contains("mcq_submission_answers")),
                "dashboard must not load answers: " + SqlCapture.STATEMENTS);
    }

    @Test
    void examRankingKeepsCompetitionRanksForTies() {
        ExamReport report = ctx.getBean(ExamReportService.class).build(examB.getId(), "", "", (stage, pct) -> { });
        assertEquals(List.of(1, 2, 2, 4, 5), report.ranking().stream().map(ExamReport.StudentRow::rank).toList());
        assertEquals(List.of(4, 3, 3, 1, 0), report.ranking().stream().map(ExamReport.StudentRow::correct).toList());
    }
}
