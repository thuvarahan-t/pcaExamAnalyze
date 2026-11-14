package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import com.example.pcaExamAnalyze.web.dto.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ClassPackageService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private final ClassPackageRepository packages;
    private final PackageCampaignRepository campaigns;
    private final PackagePosterRepository posters;
    private final PackagePurchaseRepository purchases;
    private final McqSubmissionRepository submissions;
    private final PackageInterestRepository interests;

    public ClassPackageService(ClassPackageRepository packages, PackageCampaignRepository campaigns,
            PackagePosterRepository posters, PackagePurchaseRepository purchases, McqSubmissionRepository submissions, PackageInterestRepository interests) {
        this.packages = packages; this.campaigns = campaigns; this.posters = posters;
        this.purchases = purchases; this.submissions = submissions; this.interests = interests;
    }

    public PackageCampaign campaign() { return campaigns.findById(1L).orElseThrow(); }
    public ClassPackage get(Long id) {
        return packages.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public ClassPackage publicPackage(Long id) {
        ClassPackage p = get(id);
        if (!p.isPublished()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return p;
    }
    public record Card(Long id, String name, String category, String batch, String description,
                       BigDecimal originalPrice, BigDecimal offerPrice, int capacity, long purchased,
                       int displayOrder, boolean published, long posterVersion) {
        public int discount() { return originalPrice.signum() == 0 ? 0 :
            100 - offerPrice.multiply(BigDecimal.valueOf(100)).divide(originalPrice, 0, java.math.RoundingMode.HALF_UP).intValue(); }
        public long remaining() { return Math.max(0, capacity - purchased); }
        public int progress() { return (int) Math.min(100, purchased * 100 / capacity); }
    }
    public List<Card> cards(boolean admin) {
        Map<Long, Long> counts = new HashMap<>();
        purchases.counts().forEach(c -> counts.put(c.getPackageId(), c.getTotal()));
        return (admin ? packages.findAllByOrderByDisplayOrderAscIdAsc() : packages.findByPublishedTrueOrderByDisplayOrderAscIdAsc())
            .stream().map(p -> new Card(p.getId(), p.getName(), p.getCategory(), p.getBatch(), p.getDescription(),
                p.getOriginalPrice(), p.getOfferPrice(), p.getCapacity(), counts.getOrDefault(p.getId(), 0L),
                p.getDisplayOrder(), p.isPublished(), p.getPosterVersion())).toList();
    }
    /** Public purchase feed: newest first, name and package only (registration IDs stay private). */
    public record RecentPurchase(Long id, String name, String school, String district, String packageName, Instant confirmedAt) {}
    public static final int RECENT_LIMIT = 30;
    public List<RecentPurchase> recentPurchases() {
        return purchases.recent(org.springframework.data.domain.PageRequest.of(0, RECENT_LIMIT)).stream()
            .map(ClassPackageService::toRecent).toList();
    }
    private static RecentPurchase toRecent(PackagePurchaseRepository.RecentPurchase r) {
        return new RecentPurchase(r.getId(), maskName(r.getStudentName()), nz(r.getSchool()), nz(r.getDistrict()),
            r.getPackageName(), r.getConfirmedAt());
    }
    /** One page of the public "Recent purchases" popup; pass the last item's time + id to get the next page. */
    public record PurchasePage(List<RecentPurchase> items, boolean hasMore) {}
    public static final int PAGE_SIZE = 20;
    public PurchasePage purchasePage(Instant beforeAt, Long beforeId, int size) {
        int n = Math.max(1, Math.min(50, size));
        var page = org.springframework.data.domain.PageRequest.of(0, n + 1);
        var rows = beforeAt == null || beforeId == null ? purchases.recent(page) : purchases.recentBefore(beforeAt, beforeId, page);
        return new PurchasePage(rows.stream().limit(n).map(ClassPackageService::toRecent).toList(), rows.size() > n);
    }
    /**
     * Hides most of each word of a public name: up to 7 letters keeps the first and last letter
     * ("Kavya" -> "K***a"); longer words keep the first two and last two ("Thuvarahan" -> "Th******an").
     */
    public static String maskName(String name) {
        if (name == null) return "";
        StringBuilder out = new StringBuilder();
        for (String word : name.strip().split("\s+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            int[] cp = word.codePoints().toArray();
            int keep = cp.length > 7 ? 2 : 1;
            if (cp.length <= 2) { out.append(word); continue; }
            out.appendCodePoint(cp[0]); if (keep == 2) out.appendCodePoint(cp[1]);
            out.append("*".repeat(cp.length - 2 * keep));
            if (keep == 2) out.appendCodePoint(cp[cp.length - 2]);
            out.appendCodePoint(cp[cp.length - 1]);
        }
        return out.toString();
    }
    public List<PackagePurchase> purchaseList(Long id) { get(id); return purchases.findByClassPackageIdOrderByConfirmedAtDescIdDesc(id); }
    public PackagePoster poster(Long id, boolean admin) {
        if (admin) get(id); else publicPackage(id);
        return posters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public void assertEnquiryAllowed(Long id) {
        ClassPackage p = publicPackage(id);
        if (!Instant.now().isBefore(campaign().getEndsAt())) throw new IllegalArgumentException("This offer has ended.");
        if (purchases.countByClassPackageId(id) >= p.getCapacity()) throw new IllegalArgumentException("This package is fully booked.");
    }
    @Transactional
    public void recordInterest(Long id, String name, String registration) {
        ClassPackage pack = locked(id);
        assertEnquiryAllowed(id);
        String reg = registrationId(registration);
        String validName = studentName(name);
        var interest = interests.findByClassPackageIdAndRegistrationId(id, reg).orElseGet(PackageInterest::new);
        interest.setClassPackage(pack);
        interest.setRegistrationId(reg);
        interest.setStudentName(purchases.findByClassPackageIdAndRegistrationId(id, reg)
            .map(PackagePurchase::getStudentName).orElse(validName));
        interest.setLastInterestedAt(Instant.now());
        interests.saveAndFlush(interest);
    }
    public record InterestedStudent(String registrationId, String studentName, Instant firstInterestedAt,
                                    Instant lastInterestedAt, Instant purchasedAt) {}
    public List<InterestedStudent> interestedStudents(Long id) {
        get(id);
        Map<String, PackagePurchase> confirmed = new HashMap<>();
        purchaseList(id).forEach(p -> confirmed.put(registrationId(p.getRegistrationId()), p));
        return interests.findByClassPackageIdOrderByLastInterestedAtDescIdDesc(id).stream()
            .map(i -> {
                var purchase = confirmed.get(i.getRegistrationId());
                return new InterestedStudent(i.getRegistrationId(),
                    purchase == null ? i.getStudentName() : purchase.getStudentName(), i.getFirstInterestedAt(),
                    i.getLastInterestedAt(), purchase == null ? null : purchase.getConfirmedAt());
            }).toList();
    }
    public ClassPackageForm form(Long id) {
        ClassPackage p = get(id);
        ClassPackageForm f = new ClassPackageForm();
        f.setName(p.getName()); f.setCategory(p.getCategory()); f.setBatch(p.getBatch()); f.setDescription(p.getDescription());
        f.setOriginalPrice(p.getOriginalPrice()); f.setOfferPrice(p.getOfferPrice()); f.setCapacity(p.getCapacity());
        f.setDisplayOrder(p.getDisplayOrder()); f.setPublished(p.isPublished()); return f;
    }
    public PackageCampaignForm campaignForm() {
        var c = campaign(); var f = new PackageCampaignForm();
        f.setTitle(Set.of("Your next step starts here.", "Offer ends in").contains(c.getTitle()) ? "PCA Mega Offer Packages" : c.getTitle());
        f.setEndsAt(LocalDateTime.ofInstant(c.getEndsAt(), ZONE).withSecond(0)); f.setWhatsappNumber(c.getWhatsappNumber()); return f;
    }
    @Transactional
    public void saveCampaign(PackageCampaignForm f) {
        var c = campaign(); c.setTitle(f.getTitle().strip());
        c.setEndsAt(f.getEndsAt().atZone(ZONE).toInstant()); c.setWhatsappNumber(f.getWhatsappNumber()); campaigns.save(c);
    }
    @Transactional
    public Long save(Long id, ClassPackageForm f, MultipartFile upload) {
        ClassPackage p = id == null ? new ClassPackage() : locked(id);
        if (f.getOfferPrice().compareTo(f.getOriginalPrice()) > 0) throw new IllegalArgumentException("Offer price cannot exceed original price.");
        if (id != null && f.getCapacity() < purchases.countByClassPackageId(id))
            throw new IllegalArgumentException("Capacity cannot be lower than confirmed purchases.");
        PackagePoster image = null;
        if (upload != null && !upload.isEmpty()) image = validatePoster(upload);
        if (id == null && image == null) throw new IllegalArgumentException("Upload a poster for this package.");
        p.setName(f.getName().strip()); p.setCategory(f.getCategory());
        if (f.getBatch() != null) p.setBatch(f.getBatch().strip()); else if (id == null) p.setBatch("");
        if (f.getDescription() != null) p.setDescription(f.getDescription().strip()); else if (id == null) p.setDescription("");
        p.setOriginalPrice(f.getOriginalPrice()); p.setOfferPrice(f.getOfferPrice());
        p.setCapacity(f.getCapacity()); p.setPublished(f.isPublished());
        if (f.getDisplayOrder() != null) p.setDisplayOrder(f.getDisplayOrder());
        else if (id == null) p.setDisplayOrder(packages.findFirstByOrderByDisplayOrderDescIdDesc().map(last -> last.getDisplayOrder() + 1).orElse(0));
        if (image != null && id != null) p.setPosterVersion(p.getPosterVersion() + 1);
        packages.saveAndFlush(p);
        if (image != null) { image.setId(p.getId()); posters.save(image); }
        return p.getId();
    }
    private ClassPackage locked(Long id) {
        return packages.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @Transactional
    public void toggle(Long id) { var p = locked(id); p.setPublished(!p.isPublished()); }
    @Transactional
    public void addPurchase(Long id, String registrationId, String name) { addPurchase(id, registrationId, name, "", ""); }
    @Transactional
    public void addPurchase(Long id, String registrationId, String name, String school, String district) {
        ClassPackage p = locked(id);
        String reg = registrationId(registrationId);
        if (purchases.existsByClassPackageIdAndRegistrationId(id, reg)) throw new IllegalArgumentException("This registration ID is already confirmed for this package.");
        if (purchases.countByClassPackageId(id) >= p.getCapacity()) throw new IllegalArgumentException("Package capacity reached. Increase capacity before adding another purchase.");
        // Blank fields are filled from the student's latest exam submission when one exists.
        var exam = (isBlank(name) || isBlank(school) || isBlank(district))
            ? submissions.findFirstByRegistrationIdIgnoreCaseOrderByCreatedAtDesc(reg) : Optional.<McqSubmission>empty();
        if (isBlank(name)) name = exam.map(McqSubmission::getStudentName).orElse("");
        String schoolValue = isBlank(school) ? lenient(exam.map(McqSubmission::getSchool).orElse(""), ClassPackageService::school) : school(school);
        String districtValue = isBlank(district) ? lenient(exam.map(McqSubmission::getDistrict).orElse(""), ClassPackageService::district) : district(district);
        var purchase = new PackagePurchase(); purchase.setClassPackage(p); purchase.setRegistrationId(reg);
        purchase.setStudentName(studentName(name)); purchase.setSchool(schoolValue); purchase.setDistrict(districtValue);
        purchases.saveAndFlush(purchase);
        interests.findByClassPackageIdAndRegistrationId(id, reg)
            .ifPresent(interest -> interest.setStudentName(purchase.getStudentName()));
    }
    @Transactional
    public void removePurchase(Long packageId, Long purchaseId) {
        locked(packageId);
        var purchase = purchases.findByIdAndClassPackageId(purchaseId, packageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        purchases.delete(purchase);
    }
    /** Auto-filled values from old exam records are skipped instead of blocking the purchase. */
    private static String lenient(String value, java.util.function.UnaryOperator<String> check) {
        try { return check.apply(value); } catch (IllegalArgumentException e) { return null; }
    }
    private static boolean isBlank(String v) { return v == null || v.isBlank(); }
    private static String nz(String v) { return v == null ? "" : v; }
    /** Optional school name; null when blank. */
    public static String school(String value) {
        String school = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (school.isEmpty()) return null;
        if (school.length() > 150 || !school.matches("[\\p{L}\\p{M}\\p{N} .,’'()&/-]+"))
            throw new IllegalArgumentException("Enter a valid school name (up to 150 characters).");
        return school;
    }
    /** Optional district; must be one of Sri Lanka's 25 districts. */
    public static String district(String value) {
        String district = value == null ? "" : value.strip();
        if (district.isEmpty()) return null;
        return com.example.pcaExamAnalyze.report.ExamReportService.DISTRICTS.stream()
            .filter(known -> known.equalsIgnoreCase(district)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Choose a district from the list."));
    }
    public static String registrationId(String value) {
        String reg = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!reg.matches("[A-Z0-9-]{3,30}")) throw new IllegalArgumentException("Registration ID must contain 3–30 letters, numbers or hyphens.");
        return reg;
    }
    public static String studentName(String value) {
        String name = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 150 || !name.matches("[\\p{L}\\p{M} .’'-]+"))
            throw new IllegalArgumentException("Enter a valid full name (3–150 characters, letters and name punctuation only).");
        return name;
    }
    private PackagePoster validatePoster(MultipartFile file) {
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("Poster must be 5 MB or smaller.");
        try {
            byte[] bytes = file.getBytes();
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("Upload a valid JPEG or PNG poster.");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!Set.of("jpeg", "png").contains(format)) throw new IllegalArgumentException("Only JPEG and PNG posters are supported.");
                    reader.setInput(stream);
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels > 20_000_000 || pixels < 1) throw new IllegalArgumentException("Poster must contain at most 20 megapixels.");
                    reader.read(0); // Reject corrupt images, not just forged MIME headers.
                    var image = new PackagePoster(); image.setData(bytes); image.setContentType("image/" + format); return image;
                } finally { reader.dispose(); }
            }
        } catch (IOException e) { throw new IllegalArgumentException("Unable to read poster. Upload a valid JPEG or PNG."); }
    }
    @Transactional
    public void seed() throws IOException {
        if (campaigns.existsById(1L)) return;
        var campaign = new PackageCampaign(); campaign.setTitle("PCA Mega Offer Packages");
        campaign.setEndsAt(LocalDateTime.of(2026, 11, 22, 23, 59, 59).atZone(ZONE).toInstant());
        campaign.setWhatsappNumber("94779179035"); campaigns.save(campaign);
        seedPackage("Theory Study Pack", "Theory", "The complete A/L Physics syllabus, units 01–11. Watch recorded lessons and learn at your own pace.", "32000", "8000", "theory.jpg", 0);
        seedPackage("Self Study Pack", "Self Study", "15 years of past-paper explanations (2011–2025). Revisit recorded lessons and build confidence, one question at a time.", "36000", "9000", "self-study.jpg", 1);
        seedPackage("Physics Practical Study Pack", "Practical", "40 Physics practicals explained in 11 hours, with 6 months of activation on the Physics Cube App.", "4400", "1100", "practical.jpg", 2);
    }
    private void seedPackage(String name, String category, String description, String original, String offer, String file, int order) throws IOException {
        var p = new ClassPackage(); p.setName(name); p.setCategory(category); p.setBatch(category.equals("Practical") ? "A/L Physics" : "2027 Batch & Repeaters");
        p.setDescription(description); p.setOriginalPrice(new BigDecimal(original)); p.setOfferPrice(new BigDecimal(offer));
        p.setPublished(true); p.setDisplayOrder(order); packages.saveAndFlush(p);
        var image = new PackagePoster(); image.setId(p.getId()); image.setContentType("image/jpeg");
        image.setData(new ClassPathResource("package-posters/" + file).getContentAsByteArray()); posters.save(image);
    }
}
