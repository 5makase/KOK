# User/Auth Service

**회원가입 / 인증**
- 일반 유저 회원가입(`POST /api/v1/users/signup`), 사장님(OWNER) 회원가입(`POST /api/v1/owners/signup`) 분리 제공
- 로그인(`/api/v1/auth/login`), Access Token 재발급(`/api/v1/auth/refresh`)
- 비밀번호 BCrypt 암호화, username/email 중복 검증

**JWT 기반 인증/인가**
- Access Token 30분 / Refresh Token 7일, `tokenType` claim으로 access/refresh 구분
- 로그인·재발급 API는 자체 JWT 검증, 그 외 API는 Gateway가 전달하는 `X-User-Id`/`X-Role` 헤더 기반 인증(이중 구조)
- Refresh Token은 Redis에 SHA-256 다이제스트로 저장, 재발급 시 토큰 불일치 감지되면 탈취 의심으로 즉시 삭제(강제 로그아웃)

**회원 조회 / 권한**
- `USER / OWNER / MASTER` 3단계 권한 체계
- 내 정보 조회(`GET /me`), MASTER 전용 특정 유저 상세 조회

**사장님(OWNER) 승인 워크플로**
- OWNER 가입 시 자동으로 `PENDING` 승인 요청 생성
- MASTER가 승인/거절(사유 포함) 처리하는 관리 API 제공

**기술 특징**
- Stateless 세션 정책(JWT 기반, 세션 미사용), CSRF 비활성화
- 소셜로그인/이메일 인증은 미구현 (자체 인증 + JWT + Redis)
