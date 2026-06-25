\c store_db;

-- [로컬] Docker 컨테이너 최초 생성 시 자동 실행됨
-- [RDS 등 외부 DB] 배포 시 아래 SQL을 직접 실행해야 함

-- p_store_images: 활성 이미지(deleted_at IS NULL) 기준 슬롯 유일성 보장
-- @UniqueConstraint 대신 partial index 사용 - soft delete된 행은 슬롯을 점유하지 않음
CREATE UNIQUE INDEX IF NOT EXISTS uix_store_images_active_slot
    ON p_store_images (store_id, display_order)
    WHERE deleted_at IS NULL;
