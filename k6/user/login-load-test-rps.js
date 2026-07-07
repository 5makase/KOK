import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';

// user-service는 SA 설계상 저트래픽 서비스(Auto Scaling Min=1/Max=1)로 분류되어 있어,
// store-service의 목표치(150 RPS)를 그대로 가져오지 않고 훨씬 보수적인 목표(10 RPS)로 검증한다.
// bcrypt 검증이 CPU 바운드라 인스턴스 1대 기준 처리량의 실질적인 한계를 이 테스트로 확인한다.
export const options = {
    scenarios: {
        default: {
            executor: 'constant-arrival-rate',
            rate: 10,
            timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 20,
            maxVUs: 100,
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const url = `${BASE_URL}/api/v1/auth/login`;

    const payload = JSON.stringify({
        username: __ENV.TEST_USERNAME || 'loadtest_user',
        password: __ENV.TEST_PASSWORD || 'loadtest_password!1',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);

    if (res.status !== 200) {
        console.log(`[ERROR] Status: ${res.status} | Body: ${res.body}`);
    }

    check(res, {
        'status 200': (r) => r.status === 200,
        'accessToken 발급됨': (r) => {
            try {
                return !!JSON.parse(r.body).data.accessToken;
            } catch (e) {
                return false;
            }
        },
    });
}