import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';

// user-service는 SA 설계상 저트래픽 서비스로 분류되어 있고(Auto Scaling Min=1/Max=1),
// 로그인은 BCryptPasswordEncoder(기본 strength=10) 검증이 있어 CPU 바운드 작업이라
// store-service 검색/랭킹(150 VU)과 동일한 기준을 그대로 적용하지 않는다.
// 여기서는 "로그인이 몰리는 순간(예: 오픈 직후)"을 가정한 완만한 램프업으로 설정했다.
export const options = {
    stages: [
        { duration: '20s', target: 30 },
        { duration: '40s', target: 30 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        // bcrypt 검증 비용 때문에 store-service 기준(300ms)보다 여유 있게 설정
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

    sleep(0.5);
}