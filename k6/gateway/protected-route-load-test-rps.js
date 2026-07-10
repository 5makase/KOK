import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';

// protected-route-load-test-vu.js와 동일한 대상(GET /api/v1/users/me)을
// SA 설계 목표(피크 150 RPS)로 고정 검증하기 위한 보조 테스트.
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
    const url = `${BASE_URL}/api/v1/users/me`;

    const params = {
        headers: {
            Authorization: `Bearer ${__ENV.TOKEN}`,
        },
    };

    const res = http.get(url, params);

    if (res.status !== 200) {
        console.log(`[ERROR] Status: ${res.status} | Body: ${res.body}`);
    }

    check(res, { 'status 200': (r) => r.status === 200 });
}