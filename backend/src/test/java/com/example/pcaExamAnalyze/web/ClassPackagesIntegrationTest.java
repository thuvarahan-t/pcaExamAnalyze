package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.PcaExamAnalyzeApplication;
import com.example.pcaExamAnalyze.repo.*;
import com.example.pcaExamAnalyze.service.ClassPackageService;
import com.example.pcaExamAnalyze.web.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.*;
import org.jsoup.Jsoup;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassPackagesIntegrationTest {
    static ConfigurableApplicationContext ctx;
    static ClassPackageService service;
    static String base;
    @BeforeAll static void boot() {
        ctx = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run(
            "--spring.profiles.active=dev", "--server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:packagesverify;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "--pca.teacher.username=packages-admin", "--pca.teacher.password=PackagesTest123!", "--logging.level.root=ERROR", "--debug=false");
        service = ctx.getBean(ClassPackageService.class);
        base = "http://localhost:" + ((ServletWebServerApplicationContext)ctx).getWebServer().getPort();
    }
    @AfterAll static void close() { if (ctx != null) ctx.close(); }
    @BeforeEach void campaignOpen() {
        var f = service.campaignForm(); f.setEndsAt(LocalDateTime.now(ClassPackageService.ZONE).plusDays(5)); service.saveCampaign(f);
    }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    HttpResponse<String> get(HttpClient c, String path) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    String token(HttpResponse<String> r) {
        var doc = Jsoup.parse(r.body()); var meta = doc.selectFirst("meta[name=_csrf]");
        return meta != null ? meta.attr("content") : doc.selectFirst("input[name=_csrf]").attr("value");
    }
    HttpResponse<String> post(HttpClient c, String path, String csrf, String body) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-CSRF-TOKEN", csrf).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    Long fixture(int capacity) throws Exception {
        var f = new ClassPackageForm(); f.setName("Test study pack " + UUID.randomUUID()); f.setCategory("Theory"); f.setBatch("2027 A/L");
        f.setDescription("Recorded physics lessons"); f.setOriginalPrice(new BigDecimal("4000")); f.setOfferPrice(new BigDecimal("1000"));
        f.setCapacity(capacity); f.setPublished(true);
        return service.save(null, f, new MockMultipartFile("poster", "poster.jpg", "image/jpeg",
            new ClassPathResource("package-posters/theory.jpg").getContentAsByteArray()));
    }
    HttpClient admin() throws Exception {
        var c = client(); var login = get(c, "/exam/admin-login");
        assertEquals(302, post(c, "/login", token(login), "username=packages-admin&password=" + enc("PackagesTest123!")).statusCode()); return c;
    }
    @Test void publicCatalogAndAdminTemplatesRenderAndKeepPurchasersPrivate() throws Exception {
        Long id = fixture(111); service.addPurchase(id, "PRIVATE-001", "Private Student");
        var student = client(); var page = get(student, "/exam/packages");
        assertEquals(200, page.statusCode()); assertTrue(page.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var doc = Jsoup.parse(page.body()); assertTrue(doc.select(".package-card").size() >= 4);
        assertNotNull(doc.selectFirst("#registrationDialog")); assertFalse(doc.text().contains("PRIVATE-001"));
        var status = get(student, "/exam/packages/status"); assertEquals(200, status.statusCode());
        assertFalse(status.body().contains("Private Student")); assertFalse(status.body().contains("PRIVATE-001"));
        assertTrue(get(student, "/exam/admin/packages").statusCode() >= 300);
        var admin = admin();
        for (String path : List.of("/exam/admin/packages", "/exam/admin/packages/new", "/exam/admin/packages/" + id + "/edit", "/exam/admin/packages/" + id + "/purchases", "/exam/admin"))
            assertEquals(200, get(admin, path).statusCode(), path);
        assertTrue(get(admin, "/exam/admin/packages/" + id + "/purchases").body().contains("PRIVATE-001"));
        assertTrue(get(student, "/exam").body().contains("/exam/packages"));
    }
    @Test void whatsappIsEncodedAndSavedWithoutCountingAsAPurchase() throws Exception {
        Long id = fixture(111); var c = client(); String csrf = token(get(c, "/exam/packages"));
        String name = "அருண் குமார்";
        var result = post(c, "/exam/packages/" + id + "/enquiry", csrf, "name=" + enc(name) + "&registrationId=reg-101");
        assertEquals(200, result.statusCode()); assertTrue(result.body().contains("https://wa.me/94779179035?text="));
        assertTrue(result.body().contains("Hi%2C%20my%20name%20is%20"));
        assertTrue(result.body().contains(enc("I'd like to purchase the " + service.get(id).getName() + ".").replace("+", "%20")));
        assertFalse(result.body().contains("purchase%20this%20package"));
        assertTrue(result.body().contains("Registration%20No%3A*%20REG-101")); assertTrue(result.body().contains("*Package%3A*%20"));
        assertEquals(0, service.purchaseList(id).size());
        var saved = Jsoup.parse(get(c, "/exam/packages").body());
        assertEquals(name, saved.selectFirst("#packageName").val()); assertEquals("REG-101", saved.selectFirst("#packageRegistration").val());
        assertTrue(result.headers().allValues("set-cookie").stream().anyMatch(v -> v.contains("HttpOnly") && v.contains("SameSite=Lax")));
        assertEquals(400, post(c, "/exam/packages/" + id + "/enquiry", csrf, "name=Bad%0AInjected%3AValue&registrationId=reg-101").statusCode());
    }
    @Test void existingExamCookiePrefillsAndPackageEditsDoNotChangeExamIdentity() throws Exception {
        var form = new ExamStudentDetailsForm(); form.setEmail("student@example.test"); form.setRegistrationId("EXAM-101");
        form.setNic("200000000001"); form.setFullName("Original Student"); form.setBatch("2027 A/L"); form.setSchool("Example School");
        form.setStream("Physical Science"); form.setDistrict("Jaffna");
        var res = new MockHttpServletResponse(); ExamDetailsCookie.write(new MockHttpServletRequest(), res, form);
        var cookie = res.getCookies()[0];
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL); var httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
        httpCookie.setPath("/exam"); httpCookie.setVersion(0); cookies.getCookieStore().add(URI.create(base), httpCookie);
        var c = HttpClient.newBuilder().cookieHandler(cookies).build(); var first = get(c, "/exam/packages");
        assertEquals("EXAM-101", Jsoup.parse(first.body()).selectFirst("#packageRegistration").val());
        var id = fixture(111); assertEquals(200, post(c, "/exam/packages/" + id + "/enquiry", token(first), "name=Edited+Student&registrationId=PACKAGE-102").statusCode());
        assertEquals("PACKAGE-102", Jsoup.parse(get(c, "/exam/packages").body()).selectFirst("#packageRegistration").val());
        assertFalse(cookies.getCookieStore().getCookies().stream().filter(v -> v.getName().equals(cookie.getName())).anyMatch(v -> !v.getValue().equals(cookie.getValue())));
    }
    @Test void confirmedPurchasesNormalizeDeduplicateAndEnforceCapacity() throws Exception {
        Long id = fixture(1); service.addPurchase(id, " reg-001 ", "First Student");
        assertEquals("REG-001", service.purchaseList(id).getFirst().getRegistrationId());
        assertThrows(IllegalArgumentException.class, () -> service.addPurchase(id, "REG-001", "First Student"));
        assertThrows(IllegalArgumentException.class, () -> service.addPurchase(id, "REG-002", "Other Student"));
        assertThrows(IllegalArgumentException.class, () -> service.assertEnquiryAllowed(id));
        var f = service.form(id); f.setCapacity(0); assertThrows(IllegalArgumentException.class, () -> service.save(id, f, null));
        service.removePurchase(id, service.purchaseList(id).getFirst().getId()); assertEquals(0, service.purchaseList(id).size());
        service.assertEnquiryAllowed(id);
    }
    @Test void concurrentPurchasesCannotOversellLastSeat() throws Exception {
        Long id = fixture(1); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Boolean>>();
            for (String reg : List.of("RACE-001", "RACE-002")) tasks.add(executor.submit(() -> {
                start.await(); try { service.addPurchase(id, reg, "Race Student"); return true; } catch (IllegalArgumentException e) { return false; }
            }));
            start.countDown(); int successes = 0; for (var task : tasks) if (task.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes); assertEquals(1, service.purchaseList(id).size());
        }
    }
    @Test void hiddenAndExpiredPackagesCannotStartEnquiryAndSeedDoesNotOverwriteChanges() throws Exception {
        Long id = fixture(111); service.toggle(id);
        assertFalse(service.cards(false).stream().anyMatch(p -> p.id().equals(id)));
        assertEquals(404, get(client(), "/exam/packages/" + id + "/poster").statusCode());
        service.toggle(id); var campaign = service.campaignForm(); campaign.setTitle("Admin headline");
        campaign.setEndsAt(LocalDateTime.now(ClassPackageService.ZONE).minusMinutes(1)); service.saveCampaign(campaign);
        assertThrows(IllegalArgumentException.class, () -> service.assertEnquiryAllowed(id));
        service.addPurchase(id, "PAID-001", "Paid Student"); // Already-paid purchases may still be recorded.
        service.seed(); assertEquals("Admin headline", service.campaign().getTitle());
        assertTrue(service.campaign().getEndsAt().isBefore(Instant.now()));
    }
    @Test void imagesAreValidatedAndUpdatedDurably() throws Exception {
        Long id = fixture(111); var form = service.form(id);
        assertThrows(IllegalArgumentException.class, () -> service.save(id, form,
            new MockMultipartFile("poster", "fake.jpg", "image/jpeg", "<script>bad</script>".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> service.save(id, form,
            new MockMultipartFile("poster", "huge.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1])));
        long version = service.get(id).getPosterVersion();
        byte[] replacement = new ClassPathResource("package-posters/practical.jpg").getContentAsByteArray();
        service.save(id, form, new MockMultipartFile("poster", "replacement.jpg", "image/jpeg", replacement));
        assertEquals(version + 1, service.get(id).getPosterVersion()); assertArrayEquals(replacement, service.poster(id, true).getData());
    }
    @Test void adminHttpMutationsUseAuthenticationCsrfAndValidateInput() throws Exception {
        Long id = fixture(111); var c = client(); var page = get(c, "/exam/packages");
        assertTrue(post(c, "/exam/packages/" + id + "/enquiry", "bad-token", "name=Some+Student&registrationId=CSRF-01").statusCode() >= 300);
        assertTrue(post(c, "/exam/admin/packages/" + id + "/purchases", token(page), "registrationId=HACK-01&name=Some+Student").statusCode() >= 300);
        assertEquals(0, service.purchaseList(id).size());
        var admin = admin(); String csrf = token(get(admin, "/exam/admin/packages"));
        assertEquals(302, post(admin, "/exam/admin/packages/" + id + "/purchases", csrf, "registrationId=HTTP-01&name=Http+Student").statusCode());
        assertEquals(1, service.purchaseList(id).size());
        var invalid = post(admin, "/exam/admin/packages/campaign", csrf, "title=Test&endsAt=bad&whatsappNumber=abc");
        assertEquals(200, invalid.statusCode()); assertTrue(invalid.body().contains("Use international digits"));
        assertEquals(302, post(admin, "/exam/admin/packages/" + id + "/toggle", csrf, "").statusCode());
        assertFalse(service.get(id).isPublished());
    }
    @Test void adminCanUploadCreateEditAndRemovePurchasesThroughRealForms() throws Exception {
        var admin = admin(); String csrf = token(get(admin, "/exam/admin/packages"));
        String boundary = "PackageTestBoundary";
        var out = new java.io.ByteArrayOutputStream();
        var fields = new LinkedHashMap<String, String>();
        fields.put("_csrf", csrf); fields.put("name", "HTTP Uploaded Pack"); fields.put("category", "Practical");
        fields.put("originalPrice", "4000"); fields.put("offerPrice", "1000"); fields.put("capacity", "111");
        fields.put("published", "true");
        for (var field : fields.entrySet()) out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
            + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"poster\"; filename=\"poster.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(new ClassPathResource("package-posters/practical.jpg").getContentAsByteArray());
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var response = admin.send(HttpRequest.newBuilder(URI.create(base + "/exam/admin/packages/new"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary).header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        var pack = service.cards(true).stream().filter(p -> p.name().equals("HTTP Uploaded Pack")).findFirst().orElseThrow();
        assertTrue(pack.published()); assertEquals("Practical", pack.category());
        int originalOrder = service.get(pack.id()).getDisplayOrder();
        var editPage = Jsoup.parse(get(admin, "/exam/admin/packages/" + pack.id() + "/edit").body());
        assertTrue(editPage.select("#batch, #description, #displayOrder").isEmpty());
        assertEquals(302, post(admin, "/exam/admin/packages/" + pack.id() + "/edit", csrf,
            "name=Edited+HTTP+Pack&category=Practical&originalPrice=5000&offerPrice=1500&capacity=111&published=true").statusCode());
        assertEquals("Edited HTTP Pack", service.get(pack.id()).getName());
        assertEquals(originalOrder, service.get(pack.id()).getDisplayOrder());
        assertTrue(service.poster(pack.id(), false).getData().length > 0);
        service.addPurchase(pack.id(), "REMOVE-01", "Remove Student");
        Long purchaseId = service.purchaseList(pack.id()).getFirst().getId();
        assertEquals(302, post(admin, "/exam/admin/packages/" + pack.id() + "/purchases/" + purchaseId + "/remove", csrf, "").statusCode());
        assertTrue(service.purchaseList(pack.id()).isEmpty());
        var afterRemoval = Jsoup.parse(get(admin, "/exam/admin/packages/" + pack.id() + "/purchases").body());
        assertTrue(afterRemoval.select(".flash.success").isEmpty());
    }
    @Test void confirmedStudentSearchAndRemovalPopupStayInsideThePurchaseList() throws Exception {
        Long id = fixture(111);
        service.addPurchase(id, "CONFIRMED-101", "Thuvrahan Kumar");
        service.addPurchase(id, "CONFIRMED-102", "Arun Student");
        var response = get(admin(), "/exam/admin/packages/" + id + "/purchases");
        var document = Jsoup.parse(response.body());
        assertEquals(200, response.statusCode());
        assertNotNull(document.selectFirst("#purchaseFilter"));
        assertTrue(document.select("#offerStudentSearch").isEmpty());
        assertEquals(2, document.select("tr[data-purchase-row]").size());
        assertEquals(2, document.select("form[data-confirm][data-confirm-action=Remove]").size());
        assertNotNull(document.selectFirst("script[src*='exam-dialog.js']"));
        assertEquals("Offer", document.select(".admin-nav-link").last().text());
        assertEquals("Offer", document.selectFirst(".admin-nav-link.active").text());
    }
}
