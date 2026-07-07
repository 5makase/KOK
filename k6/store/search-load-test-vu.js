import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';
const keywords = ['한식', '양식', '중식', '일식', '카페', '디저트', '고기', '파스타', '피자', '치킨'];

export const options = {
  stages: [
    { duration: '30s', target: 150 },
    { duration: '1m',  target: 150 },
    { duration: '30s', target: 0 },
  ],
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

  check(res, { 'status 200': (r) => r.status === 200 });

  sleep(0.1);
}
