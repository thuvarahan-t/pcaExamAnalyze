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
    @Test void purchaseListPagesWithCursorWithoutDuplicatesOrRegistrationIds() throws Exception {
        Long id = fixture(111);
        for (int i = 1; i <= 5; i++) service.addPurchase(id, "PAGE-00" + i, "Paging Student");
        var first = service.purchasePage(null, null, 2);
        assertEquals(2, first.items().size()); assertTrue(first.hasMore());
        var last = first.items().get(1);
        var second = service.purchasePage(last.confirmedAt(), last.id(), 2);
        assertEquals(2, second.items().size());
        var seen = new HashSet<Long>(); first.items().forEach(r -> seen.add(r.id()));
        second.items().forEach(r -> assertTrue(seen.add(r.id()), "page overlap"));
        assertTrue(second.items().get(0).confirmedAt().compareTo(last.confirmedAt()) <= 0);
        assertEquals(1, service.purchasePage(null, null, 0).items().size()); // size is clamped to 1..50
        var student = client();
        var http = get(student, "/exam/packages/purchases?size=3");
        assertEquals(200, http.statusCode()); assertTrue(http.body().contains("\"hasMore\":true"));
        assertTrue(http.body().contains("P****g S*****t")); assertFalse(http.body().contains("PAGE-00"));
        assertFalse(http.body().contains("Paging Student"));
        assertEquals(200, get(student, "/exam/packages/purchases?size=3&beforeAt=" + enc(last.confirmedAt().toString()) + "&beforeId=" + last.id()).statusCode());
        assertEquals(400, get(student, "/exam/packages/purchases?beforeAt=yesterday&beforeId=1").statusCode());
        var page = Jsoup.parse(get(student, "/exam/packages").body());
        assertNotNull(page.selectFirst("#purchasesDialog[data-url]"));
        assertNotNull(page.selectFirst("button#buyFeedMore[aria-controls=purchasesDialog]"));
        assertNotNull(page.selectFirst("#buyFeedViewport[tabindex=0]"));
    }
    @Test void recentPurchaseNamesAreMaskedByLength() {
        assertEquals("K***a", ClassPackageService.maskName("Kavya"));
        assertEquals("T*****n", ClassPackageService.maskName("Thuvran"));
        assertEquals("Th****an", ClassPackageService.maskName("Thuvahan"));
        assertEquals("Th******an", ClassPackageService.maskName("Thuvarahan"));
        assertEquals("Th******an K***r", ClassPackageService.maskName("  Thuvarahan   Kumar "));
        assertEquals("K. A*i", ClassPackageService.maskName("K. Ani"));
    }
    @Test void publicCatalogShowsMaskedRecentPurchaseNamesAndKeepsRegistrationIdsPrivate() throws Exception {
        Long id = fixture(111); service.addPurchase(id, "PRIVATE-001", "Private Student");
        var student = client(); var page = get(student, "/exam/packages");
        assertEquals(200, page.statusCode()); assertTrue(page.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var doc = Jsoup.parse(page.body()); assertTrue(doc.select(".package-card").size() >= 4);
        assertNotNull(doc.selectFirst("#registrationDialog")); assertFalse(page.body().contains("PRIVATE-001"));
        assertFalse(page.body().contains("Private Student"));
        var seed = doc.selectFirst("#buyFeedSeed li[data-name=\"P*****e S*****t\"]"); assertNotNull(seed);
        assertEquals(service.get(id).getName(), seed.attr("data-pack")); assertFalse(doc.selectFirst("#buyFeed").hasAttr("hidden"));
        var status = get(student, "/exam/packages/status"); assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"recent\"")); assertTrue(status.body().contains("P*****e S*****t"));
        assertFalse(status.body().contains("Private Student"));
        assertFalse(status.body().contains("PRIVATE-001"));
        assertTrue(get(student, "/exam/admin/packages").statusCode() >= 300);
        var admin = admin();
        for (String path : List.of("/exam/admin/packages", "/exam/admin/packages/new", "/exam/admin/packages/" + id + "/edit", "/exam/admin/packages/" + id + "/purchases", "/exam/admin"))
            assertEquals(200, get(admin, path).statusCode(), path);
        assertTrue(get(admin, "/exam/admin/packages/" + id + "/purchases").body().contains("PRIVATE-001"));
        assertTrue(get(student, "/exam").body().contains("/exam/packages"));
    }
    private String contactForm(String firstName, String lastName, String number, String key) {
        return "firstName=" + enc(firstName) + "&lastName=" + enc(lastName)
            + "&phoneNumber=" + number + "&requestKey=" + key;
    }

    @Test void whatsappReferenceConfirmsTheExactEnquiryAndKeepsSharedPhonesSeparate() throws Exception {
        Long id = fixture(111);
        String path = "/exam/packages/" + id + "/enquiry";
        var student = client();
        String csrf = token(get(student, "/exam/packages"));
        String key = java.util.UUID.randomUUID().toString();
        String form = contactForm("அருண்", "குமார்", "0712345678", key);
        var result = post(student, path, csrf, form);
        assertEquals(200, result.statusCode());
        var first = service.interestedStudents(id).getFirst();
        assertTrue(result.body().contains("https://wa.me/94779179035?text="));
        assertTrue(result.body().contains("Enquiry%20Ref%3A*%20" + first.reference()));
        assertTrue(result.body().contains("0712345678"));
        assertEquals(200, post(student, path, csrf, form).statusCode());
        assertEquals(1, service.interestedStudents(id).size());
        assertTrue(service.purchaseList(id).isEmpty());
        var saved = Jsoup.parse(get(student, "/exam/packages").body());
        assertEquals("அருண்", saved.selectFirst("#packageName").val());
        assertEquals("குமார்", saved.selectFirst("#packageLastName").val());
        assertEquals("12345678", saved.selectFirst("#packagePhone").val());
        assertNull(saved.selectFirst("#enquiryForm [name=registrationId]"));
        assertTrue(result.headers().allValues("set-cookie").stream().anyMatch(v -> v.contains("HttpOnly") && v.contains("SameSite=Lax")));
        assertEquals(400, post(student, path, csrf, contactForm("Bad\nInjected", "Name", "0712345678", key)).statusCode());
        assertEquals(400, post(student, path, csrf, contactForm("Other", "Student", "0812345678", key)).statusCode());
        assertEquals(400, post(student, path, csrf, contactForm("Other", "Student", "071234567", key)).statusCode());
        assertEquals(200, post(student, path, csrf,
            contactForm("Sibling", "Kumar", "0712345678", java.util.UUID.randomUUID().toString())).statusCode());
        assertEquals(2, service.interestedStudents(id).size());
        var admin = admin();
        String purchasesPath = "/exam/admin/packages/" + id + "/purchases";
        var page = get(admin, purchasesPath);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains(first.reference()));
        assertTrue(page.body().contains("0712345678"));
        String adminCsrf = token(page);
        String enquiryPath = "/exam/admin/packages/" + id + "/enquiries/" + first.id();
        for (int i = 0; i < 2; i++)
            assertEquals(302, post(admin, enquiryPath + "/confirm", adminCsrf, "").statusCode());
        assertEquals(1, service.purchaseList(id).size());
        assertEquals(first.id(), service.purchaseList(id).getFirst().getEnquiryId());
        assertEquals(1, service.interestedStudents(id).stream().filter(row -> row.purchasedAt() != null).count());
        assertEquals(302, post(admin, enquiryPath + "/name", adminCsrf,
            "firstName=Corrected&lastName=Kumar").statusCode());
        assertEquals("Corrected Kumar", service.purchaseList(id).getFirst().getStudentName());
        assertEquals(first.id(), service.purchaseList(id).getFirst().getEnquiryId());
        String manual = "firstName=Manual&registrationId=reg-123&phoneSuffix=12345678";
        assertEquals(302, post(admin, purchasesPath, adminCsrf, manual).statusCode());
        assertEquals(1, service.purchaseList(id).size());
        assertEquals(302, post(admin, purchasesPath, adminCsrf, manual + "&sharedPhone=true").statusCode());
        assertEquals(302, post(admin, purchasesPath, adminCsrf, manual + "&sharedPhone=true").statusCode());
        assertEquals(2, service.purchaseList(id).size());
        var manualPurchase = service.purchaseList(id).stream().filter(p -> "Manual".equals(p.getStudentName()))
            .findFirst().orElseThrow();
        assertEquals("REG-123", manualPurchase.getRegistrationId());
        assertNull(manualPurchase.getLastName());
        assertEquals(302, post(admin, purchasesPath, adminCsrf,
            "firstName=Optional&registrationId=&phoneSuffix=12345679").statusCode());
        assertTrue(service.purchaseList(id).stream()
            .anyMatch(p -> "Optional".equals(p.getStudentName()) && p.getRegistrationId() == null));
        assertFalse(get(student, "/exam/packages/purchases").body().contains("0712345678"));
    }
    @Test void whatsappInterestPersistsDeduplicatesAndTracksPurchaseChanges() throws Exception {
        Long id = fixture(111);
        service.recordInterest(id, "Interested Student", "interest-101");
        var first = service.interestedStudents(id).getFirst();
        assertEquals("INTEREST-101", first.registrationId());
        assertNull(first.purchasedAt());
        service.recordInterest(id, "Updated Student", " INTEREST-101 ");
        assertEquals(1, service.interestedStudents(id).size());
        assertEquals(first.firstInterestedAt(), service.interestedStudents(id).getFirst().firstInterestedAt());
        service.addPurchase(id, "interest-101", "");
        assertEquals("Updated Student", service.purchaseList(id).getFirst().getStudentName());
        assertNotNull(service.interestedStudents(id).getFirst().purchasedAt());
        service.removePurchase(id, service.purchaseList(id).getFirst().getId());
        service.addPurchase(id, "interest-101", "Admin Student");
        service.recordInterest(id, "Conflicting Student", "interest-101");
        assertEquals("Admin Student", service.interestedStudents(id).getFirst().studentName());
        assertEquals("Admin Student", ctx.getBean(PackageInterestRepository.class)
            .findByClassPackageIdAndRegistrationId(id, "INTEREST-101").orElseThrow().getStudentName());
        var page = get(admin(), "/exam/admin/packages/" + id + "/purchases");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Purchased"));
        assertTrue(page.body().contains("Admin Student"));
        assertTrue(page.body().indexOf("id=\"confirmedStudents\"") < page.body().indexOf("id=\"interestedStudents\""));
        assertTrue(page.body().contains("data-student-pagination=\"purchases\""));
        assertTrue(page.body().contains("data-student-pagination=\"interested\""));
        Long other = fixture(111);
        service.recordInterest(other, "Other Student", "INTEREST-101");
        assertNull(service.interestedStudents(other).getFirst().purchasedAt());
        service.removePurchase(id, service.purchaseList(id).getFirst().getId());
        assertNull(service.interestedStudents(id).getFirst().purchasedAt());
        assertEquals(1, service.interestedStudents(id).size());
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
        assertTrue(post(c, "/exam/packages/" + id + "/enquiry", "bad-token", contactForm("Some", "Student", "0712345678", java.util.UUID.randomUUID().toString())).statusCode() >= 300);
        assertTrue(post(c, "/exam/admin/packages/" + id + "/purchases", token(page), "registrationId=HACK-01&name=Some+Student").statusCode() >= 300);
        assertEquals(0, service.purchaseList(id).size());
        var admin = admin(); String csrf = token(get(admin, "/exam/admin/packages"));
        assertEquals(302, post(admin, "/exam/admin/packages/" + id + "/purchases", csrf, "registrationId=HTTP-01&name=Http+Student").statusCode());
        assertEquals(1, service.purchaseList(id).size());
        assertEquals(302, post(admin, "/exam/admin/packages/" + id + "/purchases", csrf,
            "registrationId=HTTP-02&name=Kavya+Student&school=Jaffna+Hindu+College&district=jaffna").statusCode());
        var placed = service.purchaseList(id).stream().filter(p -> p.getRegistrationId().equals("HTTP-02")).findFirst().orElseThrow();
        assertEquals("Jaffna Hindu College", placed.getSchool()); assertEquals("Jaffna", placed.getDistrict());
        var feed = get(c, "/exam/packages/status").body();
        assertTrue(feed.contains("Jaffna Hindu College")); assertTrue(feed.contains("K***a S*****t"));
        var purchasesPage = get(admin, "/exam/admin/packages/" + id + "/purchases").body();
        assertTrue(purchasesPage.contains("Jaffna Hindu College")); assertTrue(purchasesPage.contains("id=\"district-select\"")); assertTrue(purchasesPage.contains("id=\"map\""));
        post(admin, "/exam/admin/packages/" + id + "/purchases", csrf, "registrationId=HTTP-03&name=Other+Student&district=Atlantis");
        assertEquals(2, service.purchaseList(id).size());
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Theory Study Pack", "Self Study Pack", "Physics Practical Study Pack"})
    void bulkPurchaseTemplateAndUploadAcceptBlankOptionalCellsAndRollBackInvalidRows(String packageName) throws Exception {
        var pack = service.cards(true).stream().filter(card -> packageName.equals(card.name())).findFirst().orElseThrow();
        Long id = pack.id();
        var admin = admin();
        String path = "/exam/admin/packages/" + id + "/purchases";
        var adminPage = get(admin, path);
        assertNotNull(Jsoup.parse(adminPage.body()).selectFirst("#purchaseWorkbook"));
        var download = admin.send(HttpRequest.newBuilder(URI.create(base + path + "/template")).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        byte[] filled;
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(download.body()));
             var output = new java.io.ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            assertEquals(1, sheet.getPhysicalNumberOfRows());
            assertEquals(5, sheet.getRow(0).getPhysicalNumberOfCells());
            sheet.createRow(1).createCell(0).setCellValue("Bulk Student");
            sheet.createRow(2);
            var row = sheet.createRow(3);
            row.createCell(0).setCellValue("Second Student");
            row.createCell(2).setCellValue(771234568);
            book.write(output);
            filled = output.toByteArray();
        }
        int before = service.purchaseList(id).size();
        assertEquals(302, upload(admin, path, token(adminPage), filled));
        var saved = service.purchaseList(id);
        assertEquals(before + 2, saved.size());
        var student = saved.stream().filter(p -> p.getStudentName().equals("Bulk Student")).findFirst().orElseThrow();
        assertNull(student.getRegistrationId());
        assertNull(student.getPhoneNumber());
        assertNull(student.getSchool());
        assertNull(student.getDistrict());
        assertTrue(saved.stream().anyMatch(p -> "0771234568".equals(p.getPhoneNumber())));

        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(filled));
             var output = new java.io.ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(1).getCell(0).setCellValue("Would Rollback");
            book.write(output);
            assertEquals(302, upload(admin, path, token(get(admin, path)), output.toByteArray()));
        }
        assertEquals(before + 2, service.purchaseList(id).size());
        assertTrue(get(admin, path).body().contains("Row 4:"));
    }

    @Test void offerFeedbackUsesDistrictPickerAndRequiresModerationBeforePublicDisplay() throws Exception {
        String offer = "/exam/packages";
        String manage = "/exam/admin/packages";
        var student = client();
        var teacher = admin();
        var quote = "Clear explanations helped me revise.";
        String fields = "name=Feedback+Student&school=Test+School&comment=" + enc(quote);
        var invalid = submitFeedbackForm(student, fields + "&district=Invalid", false);
        assertEquals(302, invalid.statusCode());
        assertTrue(invalid.headers().firstValue("Location").orElseThrow().endsWith(offer + "#student-feedback"));
        var rejected = Jsoup.parse(get(student, offer).body());
        assertTrue(rejected.selectFirst(".share-feedback").hasAttr("open"));
        assertEquals("Feedback Student", rejected.selectFirst(".share-feedback input[name=name]").val());
        assertNotNull(rejected.selectFirst(".share-feedback .district-picker #map"));
        assertNull(rejected.selectFirst(".share-feedback [name=consent], .share-feedback [name=packageId]"));
        assertFalse(get(teacher, manage).body().contains(quote));
        var submission = submitFeedbackForm(student, fields + "&district=Jaffna", false);
        assertEquals(302, submission.statusCode());
        assertTrue(submission.headers().firstValue("Location").orElseThrow().endsWith(offer + "#student-feedback"));
        assertNotNull(Jsoup.parse(get(student, offer).body()).selectFirst(".showcase-message"));
        assertFalse(get(student, offer).body().contains(quote));
        var adminPage = get(teacher, manage);
        var adminDoc = Jsoup.parse(adminPage.body());
        assertNotNull(adminDoc.selectFirst("#tab-feedback .offer-pending-count"));
        var pending = adminDoc.selectFirst(".offer-review-queue article:contains(" + quote + ")");
        assertNotNull(pending);
        assertEquals("Approve & publish", pending.selectFirst("button[value=PUBLISHED]").text());
        String statusPath = pending.selectFirst("form").attr("action");
        var approval = post(teacher, statusPath, token(adminPage), "status=PUBLISHED");
        assertEquals(302, approval.statusCode());
        assertTrue(approval.headers().firstValue("Location").orElseThrow().endsWith("#student-feedback"));
        var publicQuote = Jsoup.parse(get(student, offer).body()).selectFirst(".student-quote:contains(" + quote + ")");
        assertNotNull(publicQuote);
        assertTrue(publicQuote.text().contains("Feedback Student Test School · Jaffna"));
        assertNull(publicQuote.selectFirst(".quote-package"));
        var reviewed = get(teacher, manage);
        var reviewDoc = Jsoup.parse(reviewed.body());
        assertNull(reviewDoc.selectFirst(".offer-review-queue article:contains(" + quote + ")"));
        assertNotNull(reviewDoc.selectFirst("details:has(summary:contains(Published feedback)) article:contains(" + quote + ")"));
        assertEquals(302, post(teacher, statusPath, token(reviewed), "status=HIDDEN").statusCode());
        assertFalse(get(student, offer).body().contains(quote));
    }

    @Test void staleFeedbackFormReturnsToOfferForAnonymousAndSignedInVisitors() throws Exception {
        String fields = "name=Feedback+Student&school=Test+School&district=Jaffna&comment=Feedback+with+expired+token";
        for (var browser : List.of(client(), admin())) {
            var expired = submitFeedbackForm(browser, fields, true);
            assertEquals(302, expired.statusCode());
            assertTrue(expired.headers().firstValue("Location").orElseThrow().endsWith("/exam/packages#student-feedback"));
            var offer = Jsoup.parse(get(browser, "/exam/packages").body());
            assertNotNull(offer.selectFirst(".share-feedback[open]"));
            assertEquals("Your feedback form expired. Please fill it in again and resend.",
                offer.selectFirst(".feedback-form [role=alert]").text());
        }
        assertFalse(get(admin(), "/exam/admin/packages").body().contains("Feedback with expired token"));
    }

    private HttpResponse<String> submitFeedbackForm(HttpClient browser, String fields, boolean expired) throws Exception {
        var form = Jsoup.parse(get(browser, "/exam/packages").body()).selectFirst(".feedback-form");
        assertNotNull(form);
        var token = form.selectFirst("input[name=_csrf]");
        assertNotNull(token);
        String body = fields + "&_csrf=" + enc(expired ? "expired-token" : token.val());
        return browser.send(HttpRequest.newBuilder(URI.create(base + form.attr("action")))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void offerAdminCanPublishEventPhotosAndConfigureVideo() throws Exception {
        String offer = "/exam/packages";
        String manage = "/exam/admin/packages";
        var teacher = admin();
        var student = client();
        var eventFields = Map.of("title", "Physics seminar",
            "venue", "Jaffna", "description", "A revision session and practice questions.");
        byte[] photo = new ClassPathResource("package-posters/theory.jpg").getContentAsByteArray();
        assertEquals(302, upload(teacher, manage + "/events", token(get(teacher, manage)),
            eventFields, "photos", "event.jpg", List.of(photo, photo, photo)));
        var eventPage = get(teacher, manage);
        var eventDoc = Jsoup.parse(eventPage.body());
        assertNull(eventDoc.selectFirst("#event-highlights [name=eventDate], #event-highlights input[name=published]"));
        var event = eventDoc.selectFirst("#event-highlights details:contains(Physics seminar)");
        assertNotNull(event);
        assertEquals(3, event.select(".offer-event-previews img").size());
        String privatePhoto = event.selectFirst("img").attr("src");
        String publicPhoto = privatePhoto.replace("/admin/", "/");
        var originalResponse = getPhoto(teacher, privatePhoto, null);
        var heroResponse = getPhoto(student, publicPhoto, null);
        var thumbResponse = getPhoto(student, publicPhoto + "?variant=thumb", null);
        assertEquals(200, originalResponse.statusCode());
        assertArrayEquals(photo, originalResponse.body());
        assertEquals(200, heroResponse.statusCode());
        assertEquals(200, thumbResponse.statusCode());
        assertEquals("image/jpeg", heroResponse.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("image/jpeg", thumbResponse.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(thumbResponse.body().length < heroResponse.body().length);
        assertTrue(heroResponse.body().length < photo.length);
        var heroImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(heroResponse.body()));
        var thumbImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(thumbResponse.body()));
        assertEquals(920, Math.max(heroImage.getWidth(), heroImage.getHeight()));
        assertEquals(144, Math.max(thumbImage.getWidth(), thumbImage.getHeight()));
        String photoEtag = heroResponse.headers().firstValue("ETag").orElseThrow();
        var notModified = getPhoto(student, publicPhoto, photoEtag);
        assertEquals(304, notModified.statusCode());
        assertEquals(0, notModified.body().length);
        String edit = event.selectFirst(".event-edit-form").attr("action");
        String visibility = event.selectFirst("form:has(button[name=published])").attr("action");
        String editFields = eventFields.entrySet().stream()
            .map(field -> field.getKey() + "=" + enc(field.getValue())).collect(java.util.stream.Collectors.joining("&"));
        assertEquals(302, post(teacher, edit, token(eventPage), editFields).statusCode());
        assertEquals(302, upload(teacher, edit, token(get(teacher, manage)),
            eventFields, "photos", "extra.jpg", List.of(photo, photo, photo)));
        var appendedPage = get(teacher, manage);
        var appendedEvent = Jsoup.parse(appendedPage.body()).selectFirst("#event-highlights details:contains(Physics seminar)");
        assertNotNull(appendedEvent);
        assertEquals(6, appendedEvent.select(".offer-event-previews img").size());
        assertEquals(privatePhoto, appendedEvent.selectFirst("img").attr("src"));
        var lastPreview = appendedEvent.select(".offer-event-previews > div").last();
        assertNotNull(lastPreview);
        String removedPhoto = lastPreview.selectFirst("img").attr("src").replace("/admin/", "/");
        assertEquals(200, get(student, removedPhoto).statusCode());
        assertEquals(302, post(teacher, lastPreview.selectFirst("form").attr("action"),
            token(appendedPage), "").statusCode());
        assertEquals(404, get(student, removedPhoto).statusCode());
        assertEquals(302, upload(teacher, edit, token(get(teacher, manage)),
            eventFields, "photos", "extra.jpg", Collections.nCopies(16, new byte[] {1})));
        assertTrue(get(teacher, manage).body().contains("An event can have up to 20 photos."));
        assertEquals(302, post(teacher, manage + "/video", token(get(teacher, manage)),
            "video=" + enc("https://youtu.be/AbCdEf12345?t=42")).statusCode());
        var page = Jsoup.parse(get(student, offer).body());
        assertEquals("Physics seminar", page.selectFirst("#event-highlights h3").text());
        assertEquals(5, page.select("#event-highlights [data-event-photo]").size());
        assertEquals(200, get(student, publicPhoto).statusCode());
        var promo = page.selectFirst("#student-feedback.offer-student-stories.with-promo > #offer-video iframe");
        assertNotNull(promo);
        assertEquals("https://www.youtube-nocookie.com/embed/AbCdEf12345", promo.attr("data-promo-video"));
        assertFalse(promo.hasAttr("src"));
        assertTrue(promo.attr("allow").contains("autoplay"));
        assertNotNull(page.selectFirst("#student-feedback > .student-voices"));
        assertNotNull(page.selectFirst("#student-feedback > .offer-story-response .share-feedback"));
        assertNull(page.selectFirst("#offer-video button"));
        assertEquals(List.of("event-highlights", "buyFeed", "student-feedback", "offer-video", "study-packs"),
            page.select("#event-highlights, #buyFeed, #offer-video, #study-packs, #student-feedback").eachAttr("id"));
        var teacherOffer = Jsoup.parse(get(teacher, offer).body());
        assertTrue(teacherOffer.select(".showcase a[href^=/exam/admin/packages]").isEmpty());
        assertEquals(302, post(teacher, visibility, token(get(teacher, manage)), "published=false").statusCode());
        assertEquals(302, post(teacher, edit, token(get(teacher, manage)), editFields).statusCode());
        assertEquals(404, get(student, publicPhoto).statusCode());
        assertEquals(404, getPhoto(student, publicPhoto, photoEtag).statusCode());
        assertEquals(302, post(teacher, manage + "/video", token(get(teacher, manage)), "video=").statusCode());
        assertNull(Jsoup.parse(get(student, offer).body()).selectFirst("#offer-video"));
        assertEquals(302, post(teacher, manage + "/events", token(get(teacher, manage)),
            "description=Text-only+highlight").statusCode());
        var textOnly = Jsoup.parse(get(student, offer).body()).selectFirst("#event-highlights .showcase-event");
        assertNotNull(textOnly);
        assertEquals("Text-only highlight", textOnly.selectFirst(".event-description").text());
        assertNull(textOnly.selectFirst(".event-gallery"));
    }

    private HttpResponse<byte[]> getPhoto(HttpClient browser, String path, String etag) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path));
        if (etag != null) request.header("If-None-Match", etag);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test void offerFeedbackAdminSupportsManualEntryAndAtomicWorkbookImport() throws Exception {
        String offer = "/exam/packages";
        String manage = "/exam/admin/packages";
        var teacher = admin();
        var student = client();
        String feedbackPath = manage + "/feedback";
        var adminPage = get(teacher, manage);
        var adminDoc = Jsoup.parse(adminPage.body());
        assertEquals(4, adminDoc.select("[data-offer-tabs] a").size());
        assertNotNull(adminDoc.selectFirst("form[action=\"/exam/admin/packages/feedback\"] .district-picker #map"));
        assertNull(adminDoc.selectFirst("form[action=\"/exam/admin/packages/feedback\"] [name=packageId], "
            + "form[action=\"/exam/admin/packages/feedback\"] [name=consent], form[action=\"/exam/admin/packages/feedback\"] [name=published]"));
        assertEquals(302, post(teacher, feedbackPath, token(adminPage),
            "name=Manual+Student&school=Test+School&district=Jaffna&comment=Manual+feedback").statusCode());
        assertTrue(get(student, offer).body().contains("Manual feedback"));
        var download = teacher.send(HttpRequest.newBuilder(URI.create(base + feedbackPath + "/template")).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(download.body()))) {
            var sheet = book.getSheetAt(0);
            var values = List.of("Workbook Student", "Test School", "Jaffna", "Workbook feedback");
            var row = sheet.createRow(1);
            for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
            sheet.createRow(2).createCell(0).setCellValue("Invalid row");
            var options = Map.of("consent", "true", "published", "true");
            for (boolean valid : List.of(false, true)) {
                if (valid) sheet.removeRow(sheet.getRow(2));
                var output = new java.io.ByteArrayOutputStream();
                book.write(output);
                assertEquals(302, upload(teacher, feedbackPath + "/bulk", token(get(teacher, manage)),
                    options, "workbook", "feedback.xlsx", output.toByteArray()));
                assertEquals(valid, get(student, offer).body().contains("Workbook feedback"));
            }
        }
    }

    private int upload(HttpClient admin, String path, String csrf, byte[] file) throws Exception {
        return upload(admin, path + "/bulk", csrf, Map.of(), "workbook", "purchases.xlsx", file);
    }

    private int upload(HttpClient admin, String path, String csrf, Map<String, String> fields,
            String fileField, String filename, byte[] file) throws Exception {
        return upload(admin, path, csrf, fields, fileField, filename, List.of(file));
    }

    private int upload(HttpClient admin, String path, String csrf, Map<String, String> fields,
            String fileField, String filename, List<byte[]> files) throws Exception {
        String boundary = "offer-upload-test";
        var parts = new StringBuilder("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf + "\r\n");
        fields.forEach((name, value) -> parts.append("--").append(boundary)
            .append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n").append(value).append("\r\n"));
        var publishers = new ArrayList<HttpRequest.BodyPublisher>();
        publishers.add(HttpRequest.BodyPublishers.ofString(parts.toString()));
        for (byte[] file : files) {
            publishers.add(HttpRequest.BodyPublishers.ofString("--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                + filename + "\"\r\n\r\n"));
            publishers.add(HttpRequest.BodyPublishers.ofByteArray(file));
            publishers.add(HttpRequest.BodyPublishers.ofString("\r\n"));
        }
        publishers.add(HttpRequest.BodyPublishers.ofString("--" + boundary + "--\r\n"));
        var body = HttpRequest.BodyPublishers.concat(publishers.toArray(HttpRequest.BodyPublisher[]::new));
        return admin.send(HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(body).build(),
            HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
