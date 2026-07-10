import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8000';

// Gateway는 자체 비즈니스 로직이 없어 "Gateway만" 따로 테스트할 API가 없다.
// 대신 인증이 필요한 가장 가벼운 엔드포인트(GET /api/v1/users/me, PK 단건 조회)를 타겟으로 잡아서
// Gateway가 매 요청마다 수행하는 JWT 파싱/검증 + 라우팅 오버헤드를 최대한 순수하게 측정한다.
// (백엔드 쿼리 자체는 매우 가벼워서, 여기서 나오는 지연시간 대부분은 Gateway 구간으로 볼 수 있다)
// Gateway는 SA 설계상 "전체 진입점"으로 Auto Scaling Max=4까지 잡혀있어, store-service와 동일하게 150 VU 기준을 사용한다.
export const options = {
    stages: [
        { duration: '30s', target: 150 },
        { duration: '1m', target: 150 },
        { duration: '30s', target: 0 },
    ],
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

    sleep(0.1);
}