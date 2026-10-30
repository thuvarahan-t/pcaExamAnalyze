// k6: 1,300 isolated student sessions, each saving and finalizing a 50-MCQ paper.
// STAGING ONLY. Every run creates submissions in the connected database.
// Usage: ALLOW_DESTRUCTIVE_LOAD_TEST=yes BASE_URL=https://STAGING EXAM_SLUG=staging-50-mcq
//        BATCH='2028 A/L' k6 run docs/load-testing/1300-student-submit.js
import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';

export const options = {
  scenarios: {
    simultaneous_exam: {
      executor: 'per-vu-iterations',
      vus: 1300,
      iterations: 1,
      maxDuration: '15m',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    'http_req_failed': ['rate<0.01'],
    'final_submissions': ['rate>0.999'],
  },
};
const BASE = (__ENV.BASE_URL || '').replace(/\/$/, '');
const SLUG = __ENV.EXAM_SLUG || '';
const BATCH = __ENV.BATCH || '';
const finalSubmissions = new Rate('final_submissions');

function csrf(html) {
  const tag = (html.match(/<input[^>]*name=["']_csrf["'][^>]*>/i) || [])[0] ||
    (html.match(/<input[^>]*value=["'][^"']+["'][^>]*name=["']_csrf["'][^>]*>/i) || [])[0];
  if (!tag) return '';
  return (tag.match(/value=["']([^"']+)["']/) || [])[1] || '';
}
function post(path, data) {
  return http.post(BASE + path, data, { redirects: 5, timeout: '45s' });
}
export default function () {
  if (__ENV.ALLOW_DESTRUCTIVE_LOAD_TEST !== 'yes' || !BASE.startsWith('https://') || !SLUG || !BATCH) {
    fail('Only run on a fully isolated staging database with explicit ALLOW_DESTRUCTIVE_LOAD_TEST=yes, BASE_URL, EXAM_SLUG and BATCH.');
  }
  const id = String(__VU).padStart(4, '0');
  const registrationId = 'LOAD' + id;
  const nic = '20000000' + String(__VU).padStart(4, '0');
  const detailsPage = http.get(BASE + '/exam/details?returnTo=/exam/p/' + encodeURIComponent(SLUG));
  const token = csrf(detailsPage.body);
  if (!check(detailsPage, { 'details page loaded': r => r.status === 200 }) || !token) {
    finalSubmissions.add(false);
    return;
  }
  const details = post('/exam/details', {
    _csrf: token,
    returnTo: '/exam/p/' + SLUG,
    fullName: 'Load Test Student',
    registrationId,
    nic,
    email: registrationId.toLowerCase() + '@example.test',
    batch: BATCH,
    school: 'Load Test School',
    stream: 'Physical Science',
    district: 'Jaffna',
  });
  if (!check(details, { 'details persisted': r => r.status === 200 && !r.url.includes('/exam/details') })) {
    finalSubmissions.add(false);
    return;
  }
  const start = post('/exam/p/' + encodeURIComponent(SLUG) + '/start', { _csrf: token });
  const match = (start.url.match(/\/exam\/session\/(\d+)/) || start.body.match(/data-submission=["'](\d+)["']/) || []);
  const submissionId = match[1];
  if (!check(start, { 'exam session created': r => !!submissionId && r.status === 200 })) {
    finalSubmissions.add(false);
    return;
  }
  let allSaved = true;
  for (let question = 1; question <= 50; question++) {
    const r = post('/exam/session/' + submissionId + '/answer', {
      _csrf: token, question: String(question), option: '1',
    });
    if (r.status !== 200) allSaved = false;
  }
  const answers = { _csrf: token, expected: '50' };
  for (let question = 1; question <= 50; question++) answers['q' + question] = '1';
  const finished = post('/exam/session/' + submissionId + '/finish', answers);
  let payload = {};
  try { payload = finished.json(); } catch (_) {}
  const durable = finished.status === 200 && payload.status === 'SUBMITTED' && payload.saved === 50;
  check(finished, {
    'all 50 answers durable at submit': () => durable,
    'individual autosaves accepted': () => allSaved,
  });
  finalSubmissions.add(durable);
}
