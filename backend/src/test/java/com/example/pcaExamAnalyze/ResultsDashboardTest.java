package com.example.pcaExamAnalyze;
import com.example.pcaExamAnalyze.PcaExamAnalyzeApplication;
import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import com.example.pcaExamAnalyze.service.McqStudentExamService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.*;import java.net.http.*;import java.time.Instant;
import org.jsoup.Jsoup;
class ResultsDashboardTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 @org.junit.jupiter.api.Test void rendersStudentResultStates() throws Exception {
  try(var ctx=new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run("--spring.profiles.active=dev","--server.port=0","--spring.datasource.url=jdbc:h2:mem:resultsverify;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","--logging.level.root=ERROR","--debug=false")){
   var tx=new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
   tx.execute(s->{for(int cohort:new int[]{1,2,8,9}){int n=cohort==9?1:cohort;for(int i=0;i<n;i++){
    var e=new McqExam();e.setName("Long physics exam "+cohort+"-"+i+" - \u0BAA\u0BCC\u0BA4\u0BBF\u0B95\u0BB5\u0BBF\u0BAF\u0BB2\u0BCD - Motion and waves with an extended title");e.setSlug("fixture-"+cohort+"-"+i);e.setExamYear(2028);e.setExamMonth(i%2==0?"June":"August");e.setPaperDriveUrl("");e.setOpenAt(Instant.now());e.setCloseAt(Instant.now());e.setResultReleaseAt(Instant.now());e.setTotalQuestions(4);e.setResultsPublished(cohort!=9&&!(cohort==8&&i==7));e.setPublicationState(McqExamPublicationState.PUBLISHED);ctx.getBean(McqExamRepository.class).save(e);
    var a=new McqSubmission();a.setExam(e);a.setReceiptNumber("FIXTURE-"+cohort+"-"+i);a.setStudentName("Fixture Student");a.setRegistrationId("TEST"+cohort);a.setNic("200000000001");a.setEmail("test@example.test");a.setBatch(i%2==0?"2026 A/L":"");a.setSchool("");a.setStream("Physical Science");a.setDistrict("Jaffna");a.setStartedAt(Instant.now().minusSeconds(100000-i*3600));a.setSubmittedAt(Instant.now().minusSeconds(99000-i*3600));a.setStatus(McqSubmissionStatus.SUBMITTED);a.setScore(i%5);a.setPercentage(i%5*25.0);ctx.getBean(McqSubmissionRepository.class).save(a);
   }}return null;});
   String base="http://localhost:"+((ServletWebServerApplicationContext)ctx).getWebServer().getPort();
   var service=ctx.getBean(McqStudentExamService.class);
   for(int cohort:new int[]{0,1,2,8,9}){
    var model=service.resultsDashboard("TEST"+cohort,"200000000001");int n=cohort==9?1:cohort;
    check(model.totalExams()==n,"Total count");check(model.graph().size()==Math.min(5,model.releasedResults()),"Graph limit");
    if(cohort==2)check(model.averagePercentage()==12.5,"Unweighted average changed");
    if(cohort==8){check(model.graph().get(0).label().contains("8-2"),"Chart not chronological");check(model.graph().get(4).label().contains("8-6"),"Latest graph point");check(model.results().get(0).examName().contains("8-7"),"History not newest first");check(model.results().get(0).score()==null,"Pending marks leaked");}
    if(cohort==1)check(model.graph().get(0).height()==0,"Zero score inflated");
    var c=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    var lookup=c.send(HttpRequest.newBuilder(URI.create(base+"/exam/result")).GET().build(),HttpResponse.BodyHandlers.ofString());String token=Jsoup.parse(lookup.body()).selectFirst("input[name=_csrf]").attr("value");
    c.send(HttpRequest.newBuilder(URI.create(base+"/exam/result")).header("Content-Type","application/x-www-form-urlencoded").header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofString("registrationId=TEST"+cohort+"&nic=200000000001")).build(),HttpResponse.BodyHandlers.ofString());
    var response=c.send(HttpRequest.newBuilder(URI.create(base+"/exam/results")).GET().build(),HttpResponse.BodyHandlers.ofString());check(response.statusCode()==200,"Template failed cohort "+cohort);var doc=Jsoup.parse(response.body());
    check(doc.select(".rd-result-row").size()==n,"History markup count");check(!doc.text().contains("200000000001"),"NIC exposed");
    check(doc.select("[data-ui=recentAverage]").size()==(cohort==8?1:0),"Redundant recent average");
    if(cohort==0)check(doc.select("[data-ui=noExams]").size()==1&&doc.select(".rd-trend").isEmpty(),"Empty state");
    if(cohort==9)check(doc.select(".rd-latest-score").isEmpty()&&doc.select(".rd-waiting").size()==1,"Withheld result state");
    if(cohort==8){check(doc.select(".rd-latest h2").text().contains("8-6"),"Latest eligible result");check(doc.select(".rd-result-row").first().select("[data-ui=withheld]").size()==2,"Withheld markup");}
    if (cohort == 1) {
     long id = model.results().getFirst().submissionId();
     String csrf = doc.selectFirst("[data-csrf-token]").attr("data-csrf-token");
     var pdfResponse = c.send(HttpRequest.newBuilder(URI.create(base + "/exam/results/" + id + "/report.pdf")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
     check(pdfResponse.statusCode() == 200, "Individual PDF download");
     try (var pdf = org.apache.pdfbox.pdmodel.PDDocument.load(pdfResponse.body())) {
      var text = new org.apache.pdfbox.text.PDFTextStripper();
      text.setStartPage(1); text.setEndPage(1);
      String cover = text.getText(pdf);
      check(cover.contains("0.0%") && cover.contains("HIGHEST SCORE"), "Percentage cover metrics");
      check(!cover.contains("SUBMITTED PAPERS"), "Removed cover statistic");
      text.setStartPage(2); text.setEndPage(2);
      check(text.getText(pdf).contains("Corrected MCQ answer sheet"), "Corrected sheet must start on page two");
     }
     var start = c.send(HttpRequest.newBuilder(URI.create(base + "/exam/results/analysis/start"))
       .header("Content-Type", "application/x-www-form-urlencoded").header("X-CSRF-TOKEN", csrf)
       .POST(HttpRequest.BodyPublishers.ofString("ids=" + id)).build(), HttpResponse.BodyHandlers.ofString());
     check(start.statusCode() == 200, "Analysis job start");
     var match = java.util.regex.Pattern.compile("\"job\":\"([^\"]+)\"").matcher(start.body());
     check(match.find(), "Job identifier");
     String path = base + "/exam/results/analysis/" + match.group(1);
     var outsider = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(path + "/status")).GET().build(), HttpResponse.BodyHandlers.ofString());
     check(outsider.statusCode() == 404, "Job belongs to its browser session");
     boolean done = false;
     for (int attempt = 0; attempt < 300; attempt++) {
      var status = c.send(HttpRequest.newBuilder(URI.create(path + "/status")).GET().build(), HttpResponse.BodyHandlers.ofString());
      check(status.statusCode() == 200 && !status.body().contains("FAILED"), "Analysis status");
      if (status.body().contains("DONE")) { done = true; break; }
      Thread.sleep(100);
     }
     check(done, "Analysis job completed");
     var file = c.send(HttpRequest.newBuilder(URI.create(path + "/file")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
     check(file.statusCode() == 200 && file.body().length > 1000, "Complete analysis PDF");
     tx.execute(s -> {
      var repo = ctx.getBean(McqExamRepository.class);
      var exam = repo.findById(ctx.getBean(McqSubmissionRepository.class).findById(id).orElseThrow().getExam().getId()).orElseThrow();
      exam.setResultsPublished(false); repo.save(exam); return null;
     });
     for (String suffix : new String[]{"/status", "/file"}) {
      var denied = c.send(HttpRequest.newBuilder(URI.create(path + suffix)).GET().build(), HttpResponse.BodyHandlers.ofString());
      check(denied.statusCode() == 403, "Withdrawn results block analysis " + suffix);
     }
     var denied = c.send(HttpRequest.newBuilder(URI.create(base + "/exam/results/" + id + "/report.pdf")).GET().build(), HttpResponse.BodyHandlers.ofString());
     check(denied.statusCode() == 403, "Withdrawn individual report");
    }
   }
   System.out.println("PASS: 0/1/2/many/pending states, rendered routes, unweighted average, zero-score scale, latest eligibility, ordering, NIC privacy, withheld scores");
  }
 }
}

