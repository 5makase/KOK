import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';

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
  const url = `${BASE_URL}/api/v1/stores/ranking?size=10`;

  const params = {
    headers: {
      'Authorization': `Bearer ${__ENV.TOKEN}`,
      'Content-Type': 'application/json',
    },
  };

  const res = http.get(url, params);

  if (res.status !== 200) {
    console.log(`[ERROR] Status: ${res.status} | Body: ${res.body}`);
  }

  check(res, { 'status 200': (r) => r.status === 200 });

  sleep(0.1);
}
