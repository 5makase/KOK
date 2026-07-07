import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.1.0/index.js';

// SA 설계 목표(피크 RPS 150)를 그대로 검증하기 위한 보조 테스트.
// search-load-test-vu.js(VU 기반 closed-model)는 응답이 빨라질수록 실제 RPS도 같이 늘어나
// "150 RPS에서 통과"라는 목표치와 숫자가 정확히 대응하지 않는다.
// constant-arrival-rate는 응답시간과 무관하게 초당 요청 수를 150으로 고정한다.
const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';
const keywords = ['한식', '양식', '중식', '일식', '카페', '디저트', '고기', '파스타', '피자', '치킨'];

export const options = {
  scenarios: {
    default: {
      executor: 'constant-arrival-rate',
      rate: 150,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 30,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const randomKeyword = keywords[randomIntBetween(0, keywords.length - 1)];
  const url = `${BASE_URL}/api/v1/stores?keyword=${encodeURIComponent(randomKeyword)}&page=0&size=20`;

  const params = {
    headers: {
      'Authorization': `Bearer ${__ENV.TOKEN}`,
      'Content-Type': 'application/json',
    },
  };

  const res = http.get(url, params);

  if (res.status !== 200) {
    console.log(`[ERROR] Status: ${res.status} | Keyword: ${randomKeyword} | Body: ${res.body}`);
  }

  check(res, {
    'status 200': (r) => r.status === 200,
    'content field present': (r) => {
      try {
        return Array.isArray(JSON.parse(r.body).data.content);
      } catch (e) {
        return false;
      }
    },
  });
}
