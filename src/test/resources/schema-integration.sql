-- ==================== WMS 메일 시스템 통합 스키마 (Oracle 호환) ====================

-- 기존 테이블 삭제
DROP TABLE IF EXISTS MAIL_SEND_LOG;
DROP TABLE IF EXISTS MAIL_QUEUE;
DROP TABLE IF EXISTS USER_INFO;
DROP TABLE IF EXISTS ORDERS;
DROP TABLE IF EXISTS INVENTORY;

-- 시퀀스 삭제
DROP SEQUENCE IF EXISTS SEQ_MAIL_SEND_LOG;
DROP SEQUENCE IF EXISTS SEQ_MAIL_QUEUE;


-- ==================== 시퀀스 생성 ====================
CREATE SEQUENCE SEQ_MAIL_SEND_LOG START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_MAIL_QUEUE START WITH 1 INCREMENT BY 1 NOCACHE;


-- ==================== 1. 통합 메일 발송 로그 ====================
CREATE TABLE MAIL_SEND_LOG (
                               LOG_ID          NUMBER          PRIMARY KEY,
                               MAIL_TYPE       VARCHAR2(20)    NOT NULL CHECK (MAIL_TYPE IN ('ALARM', 'DIRECT', 'REPORT', 'NOTICE')),
                               MAIL_SOURCE     VARCHAR2(100),
                               SUBJECT         VARCHAR2(500)   NOT NULL,
                               RECIPIENTS      CLOB            NOT NULL,
                               CC_RECIPIENTS   CLOB,
                               BODY_HTML       CLOB            NOT NULL,
                               SEND_STATUS     VARCHAR2(20)    NOT NULL CHECK (SEND_STATUS IN ('PENDING', 'SUCCESS', 'FAILURE')),
                               ERROR_MESSAGE   VARCHAR2(2000),
                               RETRY_COUNT     NUMBER          DEFAULT 0,
                               SEND_DATE       DATE,
                               SENDER_EMAIL    VARCHAR2(200),
                               IP_ADDRESS      VARCHAR2(50),
                               REG_DATE        DATE            DEFAULT SYSDATE,
                               UPD_DATE        DATE
);

CREATE INDEX IDX_MAIL_LOG_DATE ON MAIL_SEND_LOG(SEND_DATE DESC);
CREATE INDEX IDX_MAIL_LOG_TYPE ON MAIL_SEND_LOG(MAIL_TYPE, SEND_STATUS);
CREATE INDEX IDX_MAIL_LOG_SOURCE ON MAIL_SEND_LOG(MAIL_SOURCE, SEND_DATE DESC);

COMMENT ON TABLE MAIL_SEND_LOG IS '통합 메일 발송 이력';
COMMENT ON COLUMN MAIL_SEND_LOG.MAIL_TYPE IS 'ALARM: 알람, DIRECT: 직접 발송, REPORT: 보고서, NOTICE: 공지';
COMMENT ON COLUMN MAIL_SEND_LOG.RECIPIENTS IS '수신인 (콤마 구분: user1@a.com,user2@a.com)';


-- ==================== 3. 메일 알람 큐 ====================
CREATE TABLE MAIL_QUEUE (
                            QUEUE_ID         NUMBER          PRIMARY KEY,
                            MAIL_SOURCE      VARCHAR2(100)   NOT NULL,
                            ALARM_NAME       VARCHAR2(200)   NOT NULL,
                            SEVERITY         VARCHAR2(20)    NOT NULL CHECK (SEVERITY IN ('INFO', 'WARNING', 'CRITICAL')),
                            SQL_ID           VARCHAR2(200)   NOT NULL,
                            SECTION_TITLE    VARCHAR2(500),
                            SECTION_CONTENT  CLOB,
                            STATUS           VARCHAR2(20)    NOT NULL CHECK (STATUS IN ('PENDING', 'SUCCESS', 'FAILED')),
                            RETRY_COUNT      NUMBER          DEFAULT 0,
                            ERROR_MESSAGE    VARCHAR2(2000),
                            REG_DATE         DATE            DEFAULT SYSDATE,
                            UPD_DATE         DATE
);

CREATE INDEX IDX_MAIL_QUEUE_STATUS ON MAIL_QUEUE(STATUS, REG_DATE);

COMMENT ON TABLE MAIL_QUEUE IS '메일 알람 발송 큐 (Oracle Procedure가 INSERT)';
COMMENT ON COLUMN MAIL_QUEUE.MAIL_SOURCE IS '알람 타입 식별자 (OVERDUE_ORDERS, LOW_STOCK 등)';
COMMENT ON COLUMN MAIL_QUEUE.ALARM_NAME IS '메일 제목에 사용될 알람 이름 (예: 지연 주문 알림)';
COMMENT ON COLUMN MAIL_QUEUE.SEVERITY IS '심각도 (INFO/WARNING/CRITICAL) - 아이콘 및 제목 prefix 결정';
COMMENT ON COLUMN MAIL_QUEUE.SQL_ID IS 'Consumer가 호출할 MyBatis SQL ID (예: alarm.selectOverdueOrdersDetail)';
COMMENT ON COLUMN MAIL_QUEUE.SECTION_TITLE IS '메일 본문 섹션 소제목';
COMMENT ON COLUMN MAIL_QUEUE.SECTION_CONTENT IS '메일 본문 섹션 내용 (TEXT)';


-- ==================== 4. 사용자 정보 (테스트용) ====================
CREATE TABLE USER_INFO (
                           USER_ID         VARCHAR2(100)   PRIMARY KEY,
                           USER_NAME       VARCHAR2(200)   NOT NULL,
                           EMAIL           VARCHAR2(200)   NOT NULL,
                           USER_GROUP      VARCHAR2(100),
                           REG_DATE        DATE            DEFAULT SYSDATE
);

CREATE INDEX IDX_USER_GROUP ON USER_INFO(USER_GROUP);

COMMENT ON TABLE USER_INFO IS '사용자 정보';



-- ==================== 5. 알람 테스트용 더미 데이터 테이블 ====================
-- Consumer가 SQL_ID를 통해 조회할 테이블 (실제 특이사항 데이터)

CREATE TABLE ORDERS (
                        ORDER_ID        VARCHAR2(50)    PRIMARY KEY,
                        CUSTOMER_NAME   VARCHAR2(200),
                        ORDER_DATE      DATE,
                        STATUS          VARCHAR2(20),
                        DAYS_OVERDUE    NUMBER
);

COMMENT ON TABLE ORDERS IS '주문 정보 (SQL_ID 조회용 더미 데이터)';



CREATE TABLE INVENTORY (
                           WAREHOUSE_CODE  VARCHAR2(50),
                           PRODUCT_CODE    VARCHAR2(50),
                           PRODUCT_NAME    VARCHAR2(200),
                           STOCK_QTY       NUMBER,
                           MIN_STOCK_QTY   NUMBER,
                           PRIMARY KEY (WAREHOUSE_CODE, PRODUCT_CODE)
);

COMMENT ON TABLE INVENTORY IS '재고 정보 (SQL_ID 조회용 더미 데이터)';


-- ==================== 커밋 ====================
COMMIT;