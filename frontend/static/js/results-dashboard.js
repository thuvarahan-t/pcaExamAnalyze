(function () {
  'use strict';
  var tamil = window.PcaLang && window.PcaLang.current === 'ta';
  var words = {
    skip: ['Skip to results', 'முடிவுகளுக்குச் செல்க'], navigation: ['Main navigation', 'முதன்மை வழிசெலுத்தல்'],
    portal: ['Student portal', 'மாணவர் தளம்'], allExams: ['All Exams', 'அனைத்து தேர்வுகள்'],
    myResults: ['My Results', 'எனது முடிவுகள்'], hello: ['Welcome back,', 'வணக்கம்,'], greeting: ['. Here is your exam record.', '. உங்கள் தேர்வு விவரங்கள் இங்கே.'],
    registration: ['Registration ID', 'பதிவு எண்'], reopen: ['Reopen My Results', 'எனது முடிவுகளை மீண்டும் திறக்க'],
    latestResult: ['Latest available result', 'அண்மையில் கிடைத்த முடிவு'], latestSubmission: ['Latest submission', 'அண்மையில் சமர்ப்பித்த தேர்வு'],
    released: ['Released', 'வெளியிடப்பட்டது'], awaiting: ['Awaiting release', 'வெளியீட்டுக்குக் காத்திருக்கிறது'],
    paperPeriod: ['Paper period', 'வினாத்தாள் காலம்'], attempted: ['Attempted', 'தேர்வு எழுதிய நாள்'], review: ['Review answers', 'விடைகளைப் பார்க்க'],
    score: ['Score', 'சதவீதம்'], marks: ['marks', 'மதிப்பெண்கள்'], marksHeading: ['Marks', 'மதிப்பெண்கள்'],
    releaseNote: ['Your marks and answers will appear when the teacher releases the result.', 'ஆசிரியர் முடிவை வெளியிட்டதும் உங்கள் மதிப்பெண்களையும் விடைகளையும் பார்க்கலாம்.'],
    submission: ['View submission', 'சமர்ப்பிப்பைப் பார்க்க'], overview: ['Performance overview', 'செயல்திறன் கண்ணோட்டம்'],
    completed: ['Exams completed', 'நிறைவுசெய்த தேர்வுகள்'], average: ['Average score', 'சராசரி சதவீதம்'], recentAverage: ['Last 5 average', 'கடைசி 5 தேர்வுகளின் சராசரி'],
    summaryScope: ['Average across all released results.', 'வெளியிடப்பட்ட அனைத்து முடிவுகளின் சராசரி.'],
    trend: ['Recent scores', 'அண்மைய மதிப்பெண்கள்'], scorePercent: ['Score (%)', 'மதிப்பெண் (%)'],
    noReleased: ['Your scores will appear here after results are released.', 'முடிவுகள் வெளியிடப்பட்டதும் உங்கள் மதிப்பெண்கள் இங்கே தோன்றும்.'],
    chartLabel: ['Recent scores, oldest to newest, on a 0 to 100 percent scale', 'அண்மைய மதிப்பெண்கள்: பழையதிலிருந்து புதியது வரை, 0 முதல் 100 சதவீத அளவில்'],
    exactScores: ['View exact scores and dates', 'துல்லியமான மதிப்பெண்களையும் தேதிகளையும் பார்க்க'],
    history: ['Exam history', 'தேர்வு வரலாறு'], historyNote: ['Completed papers, newest attempt first.', 'நிறைவுசெய்த தேர்வுகள்; அண்மையில் எழுதியது முதலில்.'],
    search: ['Search exams', 'தேர்வுகளைத் தேடுக'], searchPlaceholder: ['Search by exam name', 'தேர்வின் பெயரைத் தேடுக'],
    batch: ['Batch', 'பிரிவு'], allBatches: ['All batches', 'அனைத்துப் பிரிவுகள்'],
    paperMonth: ['Paper month', 'வினாத்தாள் மாதம்'], allMonths: ['All months', 'அனைத்து மாதங்கள்'],
    paperYear: ['Paper year', 'வினாத்தாள் ஆண்டு'], allYears: ['All years', 'அனைத்து ஆண்டுகள்'],
    clear: ['Clear filters', 'வடிகட்டிகளை நீக்குக'], exam: ['Exam', 'தேர்வு'], status: ['Status', 'நிலை'], action: ['Action', 'செயல்'], latest: ['Latest', 'அண்மையது'],
    withheld: ['Available after release', 'வெளியீட்டிற்குப் பின் கிடைக்கும்'], noMatch: ['No matching results', 'பொருத்தமான முடிவுகள் இல்லை'],
    noMatchNote: ['Try a different exam name or clear your filters.', 'வேறு தேர்வின் பெயரைத் தேடவும் அல்லது வடிகட்டிகளை நீக்கவும்.'],
    noExams: ['No completed exams yet', 'இன்னும் தேர்வுகள் நிறைவுசெய்யப்படவில்லை'],
    noExamsNote: ['Your submitted exams will appear here. Released results will include marks and answer review.', 'நீங்கள் சமர்ப்பித்த தேர்வுகள் இங்கே தோன்றும். முடிவுகள் வெளியிடப்பட்டதும் மதிப்பெண்களையும் விடைகளையும் பார்க்கலாம்.'],
    browse: ['Browse exams', 'தேர்வுகளைப் பார்க்க'],
    reportHeading: ['Subject analysis report', 'பாட ரீதியான பகுப்பாய்வு அறிக்கை'],
    reportNote: ['Pick the exams you want (for example your last 5) and download a PDF that shows which subjects are weak, which need practice and which are strong.', 'தேவையான தேர்வுகளைத் தெரிவுசெய்யுங்கள் (உதாரணமாக கடைசி 5). எந்தப் பாடங்களில் பலவீனம், எவற்றில் மேலும் பயிற்சி தேவை, எவற்றில் பலம் உள்ளது என்பதைக் காட்டும் PDF அறிக்கையைப் பதிவிறக்குங்கள்.'],
    reportOpen: ['Create report', 'அறிக்கையை உருவாக்குக'], reportPick: ['Choose the released exams to include.', 'அறிக்கையில் சேர்க்க வேண்டிய வெளியிடப்பட்ட தேர்வுகளைத் தெரிவுசெய்யுங்கள்.'],
    close: ['Close', 'மூடுக'], quickPick: ['Quick selection', 'விரைவுத் தெரிவு'], last5: ['Last 5', 'கடைசி 5'], last10: ['Last 10', 'கடைசி 10'],
    allReleased: ['All released', 'வெளியிடப்பட்ட அனைத்தும்'], clearPick: ['Clear', 'நீக்குக'], cancel: ['Cancel', 'இரத்துசெய்க'], reportDownload: ['Download PDF', 'PDF பதிவிறக்குக'], footer: ['Your exam record, in one place.', 'உங்கள் தேர்வு விவரங்கள் அனைத்தும் ஒரே இடத்தில்.']
  };
  function t(key) { return words[key][tamil ? 1 : 0]; }
  document.querySelectorAll('[data-ui]').forEach(function (el) { if (words[el.dataset.ui]) el.textContent = t(el.dataset.ui); });
  document.querySelectorAll('[data-ui-aria]').forEach(function (el) { el.setAttribute('aria-label', t(el.dataset.uiAria)); });
  document.querySelectorAll('[data-ui-placeholder]').forEach(function (el) { el.placeholder = t(el.dataset.uiPlaceholder); });
  document.title = t('myResults') + ' · PCA MCQ';
  var months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  var taMonths = ['ஜனவரி', 'பெப்ரவரி', 'மார்ச்', 'ஏப்ரல்', 'மே', 'ஜூன்', 'ஜூலை', 'ஆகஸ்ட்', 'செப்டம்பர்', 'ஒக்டோபர்', 'நவம்பர்', 'டிசம்பர்'];
  function dateLabel(text) {
    text = text || '—';
    if (!tamil) return text;
    months.forEach(function (month, index) { text = text.replace(new RegExp('\\b' + month + '\\b|\\b' + month.slice(0, 3) + '\\b', 'g'), taMonths[index]); });
    return text.replace(/\bAM\b/g, 'மு.ப.').replace(/\bPM\b/g, 'பி.ப.');
  }
  document.querySelectorAll('[data-date], [data-period], [data-month-name]').forEach(function (el) { el.textContent = dateLabel(el.textContent); });
  document.querySelectorAll('[data-action-exam]').forEach(function (link) { link.setAttribute('aria-label', t(link.dataset.actionKind) + ': ' + link.dataset.actionExam); });
  document.querySelectorAll('[data-title][data-value]').forEach(function (point) {
    var label = point.dataset.title + ' — ' + point.dataset.value + '%; ' + t('attempted') + ': ' + dateLabel(point.dataset.attempt);
    point.title = label; point.setAttribute('aria-label', label);
  });
  var trend = document.querySelector('.rd-trend');
  if (trend) {
    var n = Number(trend.dataset.points), description = trend.querySelector('.rd-trend-description');
    description.textContent = n === 0 ? (tamil ? 'வெளியிடப்பட்ட முடிவுகள் இன்னும் இல்லை.' : 'No released results yet.') :
      n === 1 ? (tamil ? 'ஒரு தேர்வு முடிவு கிடைக்கிறது. மேலும் முடிவுகள் வந்ததும் ஒப்பிடலாம்.' : 'One result available. More results will give you a comparison.') :
      tamil ? 'அண்மைய ' + n + ' தேர்வுகள் · எழுதிய நாளின்படி பழையதிலிருந்து புதியது வரை.' : 'Recent ' + n + ' exams · oldest to newest by attempt date.';
  }
  document.querySelectorAll('[data-released]').forEach(function (el) {
    el.textContent = tamil ? el.dataset.total + ' தேர்வுகளில் ' + el.dataset.released + ' முடிவுகள் வெளியிடப்பட்டுள்ளன.' : el.dataset.released + ' of ' + el.dataset.total + ' results released.';
  });
  var picker = document.getElementById('reportPicker');
  if (picker && picker.showModal) {
    var boxes = Array.from(picker.querySelectorAll('input[name="ids"]')), max = Number(picker.querySelector('[data-max]').dataset.max) || 50;
    var pickCount = document.getElementById('reportPickCount'), download = document.getElementById('reportDownload');
    var quick = Array.from(picker.querySelectorAll('[data-pick]'));
    if (tamil) {
      download.dataset.pdfTitle = 'உங்கள் பாட அறிக்கை தயாராகிறது';
      download.dataset.pdfIntro = 'தெரிவுசெய்த தேர்வுகளில் உங்கள் விடைகள் பாட வாரியாகப் பகுப்பாய்வு செய்யப்படுகின்றன.';
      download.dataset.pdfReady = 'உங்கள் பாட அறிக்கை தயார்';
    }
    function refresh(active) {
      var n = boxes.filter(function (box) { return box.checked; }).length;
      boxes.forEach(function (box) { box.disabled = !box.checked && n >= max; });
      pickCount.textContent = n === 0 ? (tamil ? 'குறைந்தது ஒரு தேர்வைத் தெரிவுசெய்யுங்கள்.' : 'Select at least one exam.') :
        (tamil ? n + ' தேர்வுகள் தெரிவுசெய்யப்பட்டன' : n + (n === 1 ? ' exam selected' : ' exams selected')) + (n >= max ? (tamil ? ' (அதிகபட்சம்)' : ' (maximum)') : '');
      download.disabled = n === 0;
      quick.forEach(function (button) { button.setAttribute('aria-pressed', button === active ? 'true' : 'false'); });
    }
    quick.forEach(function (button) {
      button.addEventListener('click', function () {
        var limit = button.dataset.pick === 'all' ? max : Math.min(max, Number(button.dataset.pick));
        boxes.forEach(function (box, index) { box.checked = index < limit; });
        refresh(limit ? button : null);
      });
    });
    boxes.forEach(function (box) { box.addEventListener('change', function () { refresh(null); }); });
    refresh(quick[0]);
    document.getElementById('openReportPicker').addEventListener('click', function () { picker.showModal(); });
    picker.querySelectorAll('[data-close-picker]').forEach(function (button) { button.addEventListener('click', function () { picker.close(); }); });
    picker.addEventListener('click', function (event) { if (event.target === picker) picker.close(); });
  }
  var rows = Array.from(document.querySelectorAll('.rd-result-row'));
  var count = document.getElementById('resultCardCount');
  var search = document.getElementById('resultSearch'), batch = document.getElementById('resultBatch'), month = document.getElementById('resultMonth'), year = document.getElementById('resultYear');
  function showCount(n) { count.textContent = tamil ? rows.length + ' தேர்வுகளில் ' + n + ' காட்டப்படுகின்றன' : 'Showing ' + n + ' of ' + rows.length + (rows.length === 1 ? ' paper' : ' papers'); }
  showCount(rows.length);
  if (!search) return;
  var clear = document.getElementById('clearResultFilters'), empty = document.getElementById('resultsNoMatch'), table = document.querySelector('.rd-table-wrap');
  function filter() {
    var query = search.value.trim().toLocaleLowerCase(), visible = 0;
    rows.forEach(function (row) {
      var show = (!query || row.dataset.name.toLocaleLowerCase().includes(query)) && (!batch.value || row.dataset.batch === batch.value) && (!month.value || row.dataset.month === month.value) && (!year.value || row.dataset.year === year.value);
      row.hidden = !show; if (show) visible++;
    });
    showCount(visible); clear.hidden = !(query || batch.value || month.value || year.value);
    empty.hidden = visible !== 0; table.hidden = visible === 0;
  }
  function reset() { search.value = ''; batch.value = ''; month.value = ''; year.value = ''; filter(); search.focus(); }
  [search, batch, month, year].forEach(function (control) { control.addEventListener(control === search ? 'input' : 'change', filter); });
  clear.addEventListener('click', reset);
  document.getElementById('clearEmptyFilters').addEventListener('click', reset);
  document.getElementById('resultsFilters').addEventListener('submit', function (event) { event.preventDefault(); filter(); });
})();
