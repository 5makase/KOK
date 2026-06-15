# KOK (임시 README)

MSA 기반 실시간 레스토랑 예약·웨이팅 플랫폼 KOK 백엔드 서비스

## Project Structure

```text
KOK
├── module
├── infrastructure
│   ├── eureka-server
│   └── api-gateway
├── services
│   ├── user-service
│   ├── store-service
│   ├── reservation-service
│   ├── waiting-service
│   └── notification-service
├── docker-compose.yml
└── README.md
```

## Tech Stack

- Java 17
- Spring Boot
- Spring Cloud Gateway
- Eureka
- PostgreSQL
- Redis
- Kafka
- Docker
- GitHub Actions