# BÀI TẬP 1: CẤU HÌNH HẠ TẦNG DISTRIBUTED CACHE CHUYÊN NGHIỆP

> **Khóa học:** Microservices System Design — Session 17: Distributed Caching Architecture  
> **Dự án:** Hệ thống GrabFood (Food Delivery Platform)  
> **Công nghệ áp dụng:** Java 21, Spring Boot 3.3.4, Spring Data Redis, Lettuce Driver (Non-blocking I/O), Jackson JSON Serializer (`GenericJackson2JsonRedisSerializer`), Docker Compose, Redis 7.2  

---

## 1. Bối Cảnh Nghiệp Vụ & Mục Tiêu Kỹ Thuật

### 1.1. Bối cảnh
Trước đây, hệ thống GrabFood sử dụng **Local Cache** (lưu trên JVM Heap của từng instance) để đệm danh sách món ăn và thực đơn của các nhà hàng nhằm giảm tải cho Database. Khi hệ thống mở rộng lên mô hình đa phiên bản (**Scale-Out Multiple Instances**):
- Dữ liệu giữa các node bị lệch pha (**Data Inconsistency / Stale Data**): Một nhà hàng cập nhật món ăn hoặc hết món trên Node 1, nhưng Node 2 và Node 3 vẫn trả về thực đơn cũ trong Heap.
- Người dùng tải lại trang (F5) liên tục thấy kết quả nhảy qua lại giữa giá cũ và giá mới tùy vào Load Balancer định tuyến tới instance nào.

### 1.2. Giải pháp chuyển đổi
- Thay thế hoàn toàn Local Cache bằng **Distributed Cache tập trung dùng Redis**.
- Sử dụng driver **Lettuce** (Non-blocking I/O dựa trên Netty) thay cho Jedis truyền thống (blocking, tiêu tốn thread).
- Chuyển đổi bộ tuần tự hóa (**Serialization**) từ Java Serializer mặc định (nhị phân, không tương thích đa ngôn ngữ, dễ lỗi bảo mật deserialize) sang **JSON Serializer** (`GenericJackson2JsonRedisSerializer`). Dữ liệu lưu trong Redis ở dạng JSON chuẩn, con người đọc được (human-readable) và có thể chia sẻ cho các service viết bằng Node.js, Go hay Python.

---

## 2. Mã Nguồn Nộp Bài

### 2.1. File cấu hình `application.yml`
Đường dẫn: `src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: grabfood-cache-service
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10

logging:
  level:
    com.grabfood: DEBUG
    org.springframework.data.redis: DEBUG
    org.springframework.cache: DEBUG
```

---

### 2.2. Toàn bộ mã nguồn `CacheConfig.java`
Đường dẫn: `src/main/java/com/grabfood/config/CacheConfig.java`

```java
package com.grabfood.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cấu hình hạ tầng Distributed Cache cho hệ thống GrabFood với Redis và Lettuce driver.
 * - Driver: Lettuce (Non-blocking I/O)
 * - Serializer: JSON Serializer (GenericJackson2JsonRedisSerializer) thay thế Java Serializer
 * - Không hard-code các thông số kết nối: đọc trực tiếp từ application.yml
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration redisTimeout;

    @Value("${spring.data.redis.lettuce.pool.max-active:10}")
    private int maxActivePool;

    /**
     * Khởi tạo Bean RedisConnectionFactory sử dụng Lettuce Driver kết nối tới Redis
     * Áp dụng các thông số host, port, timeout, lettuce connection pool từ application.yml
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("=================================================================");
        log.info("Initializing RedisConnectionFactory with Lettuce Driver:");
        log.info("  -> Host: {}", redisHost);
        log.info("  -> Port: {}", redisPort);
        log.info("  -> Timeout: {}", redisTimeout);
        log.info("  -> Lettuce Pool Max-Active: {}", maxActivePool);
        log.info("=================================================================");

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(redisHost);
        serverConfig.setPort(redisPort);

        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxActivePool);

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(redisTimeout)
                .poolConfig(poolConfig)
                .build();

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    /**
     * Cấu hình ObjectMapper hỗ trợ JavaTimeModule và Polymorphic Type Handling
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        return objectMapper;
    }

    /**
     * Khởi tạo Bean RedisCacheManager sử dụng GenericJackson2JsonRedisSerializer
     * Lưu trữ dữ liệu dạng JSON có thể đọc được (human-readable) thay vì nhị phân.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        log.info("Configuring RedisCacheManager with GenericJackson2JsonRedisSerializer (JSON format)");

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }

    /**
     * Khởi tạo RedisTemplate cho các thao tác trực tiếp với Redis dưới dạng JSON
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

---

## 3. Khởi Động Redis Server (`docker-compose.yml`)

File triển khai container Redis chuyên nghiệp:
```yaml
version: '3.8'

services:
  redis:
    image: redis:7.2-alpine
    container_name: grabfood-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  redis-data:
```

Lệnh khởi động:
```bash
docker compose up -d
```

---

## 4. Bằng Chứng Thực Nghiệm & Kết Quả Kiểm Tra (`redis-cli`)

### 4.1. Kiểm tra kết nối Redis (Tiêu chí 1)
```bash
$ redis-cli ping
PONG
```

### 4.2. Kiểm tra log khởi động nhận đủ cấu hình Lettuce & Connection Pool (Tiêu chí 4 & 5)
```log
2026-09-27T18:50:41.050+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig : =================================================================
2026-09-27T18:50:41.051+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig : Initializing RedisConnectionFactory with Lettuce Driver:
2026-09-27T18:50:41.051+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig :   -> Host: localhost
2026-09-27T18:50:41.051+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig :   -> Port: 6379
2026-09-27T18:50:41.051+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig :   -> Timeout: PT2S
2026-09-27T18:50:41.051+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig :   -> Lettuce Pool Max-Active: 10
2026-09-27T18:50:41.051+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig : =================================================================
2026-09-27T18:50:41.065+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.config.CacheConfig : Configuring RedisCacheManager with GenericJackson2JsonRedisSerializer (JSON format)
2026-09-27T18:50:41.455+07:00  INFO 25472 --- [grabfood-cache-service] [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path '/'
2026-09-27T18:50:41.461+07:00  INFO 25472 --- [grabfood-cache-service] [main] com.grabfood.GrabFoodApplication : Started GrabFoodApplication in 2.216 seconds
```

---

### 4.3. Kiểm thử hiệu năng Cache Hit / Cache Miss qua REST API (Tiêu chí 3)

#### Lần 1: Cache Miss (Truy vấn CSDL giả lập - Phản hồi ~1445ms)
```bash
$ curl -s http://localhost:8080/api/v1/restaurants/RES-001/menu
{
  "@class": "java.util.LinkedHashMap",
  "restaurantId": "RES-001",
  "totalItems": 4,
  "responseTimeMs": ["java.lang.Long", 1445],
  "dataSource": "DATABASE (Cache Miss - Query simulated)",
  "items": [
    "java.util.ArrayList",
    [
      {
        "@class": "com.grabfood.model.MenuItem",
        "id": 1,
        "name": "Phở",
        "price": ["java.math.BigDecimal", 65000],
        "description": "Phở bò tái nạm gầu gân kèm quẩy giòn và trứng chần",
        "category": "Món nước",
        "restaurantId": "RES-001",
        "available": true
      },
      ...
    ]
  ]
}
```

#### Lần 2: Cache Hit (Lấy trực tiếp từ Redis - Phản hồi ~33ms, tốc độ tăng gấp ~45 lần!)
```bash
$ curl -s http://localhost:8080/api/v1/restaurants/RES-001/menu
{
  "@class": "java.util.LinkedHashMap",
  "restaurantId": "RES-001",
  "totalItems": 4,
  "responseTimeMs": ["java.lang.Long", 33],
  "dataSource": "REDIS DISTRIBUTED CACHE (Cache Hit)",
  ...
}
```

---

### 4.4. Kiểm tra dữ liệu trong Redis bằng `redis-cli` (Tiêu chí 2 - Serializer JSON)

#### 1. Liệt kê các key đang tồn tại:
```bash
$ redis-cli keys "*"
restaurant_menu::RES-001
dish::1
```

#### 2. Kiểm tra giá trị món ăn đơn lẻ (`dish::1`):
```bash
$ redis-cli get "dish::1"
{"@class":"com.grabfood.model.MenuItem","id":1,"name":"Phở","price":["java.math.BigDecimal",65000],"description":"Phở bò Hà Nội gia truyền, nước dùng xương hầm 24 giờ","category":"Món nước","restaurantId":"RES-001","available":true}
```
> **Đánh giá:** Dữ liệu hoàn toàn là chuỗi JSON chuẩn (`{"id":1,"name":"Phở", ...}`), con người đọc được bình thường, không chứa các ký tự nhị phân như `\xac\xed\x00\x05` của Java Serialization.

#### 3. Kiểm tra danh sách thực đơn nhà hàng (`restaurant_menu::RES-001`):
```bash
$ redis-cli get "restaurant_menu::RES-001"
["java.util.ArrayList",[{"@class":"com.grabfood.model.MenuItem","id":1,"name":"Phở","price":["java.math.BigDecimal",65000],"description":"Phở bò tái nạm gầu gân kèm quẩy giòn và trứng chần","category":"Món nước","restaurantId":"RES-001","available":true},{"@class":"com.grabfood.model.MenuItem","id":2,"name":"Cơm Tấm Sườn Bì Chả","price":["java.math.BigDecimal",55000],"description":"Cơm tấm hạt tấm thơm, sườn nướng mật ong đậm đà","category":"Cơm","restaurantId":"RES-001","available":true},{"@class":"com.grabfood.model.MenuItem","id":3,"name":"Bánh Mì Pate Chả Lụa","price":["java.math.BigDecimal",30000],"description":"Bánh mì giòn rụm kẹp pate béo ngậy, sốt bơ trứng truyền thống","category":"Món nhẹ","restaurantId":"RES-001","available":true},{"@class":"com.grabfood.model.MenuItem","id":4,"name":"Trà Sữa Trân Châu Đường Đen","price":["java.math.BigDecimal",45000],"description":"Trà sữa sữa tươi trân châu đường đen Đài Loan thơm ngon","category":"Đồ uống","restaurantId":"RES-001","available":true}]]
```

#### 4. Kiểm tra thời gian sống (TTL):
```bash
$ redis-cli ttl "dish::1"
577
$ redis-cli ttl "restaurant_menu::RES-001"
567
```

---

## 5. Đối Chiếu Bảng Kiểm Đánh Giá

| Tiêu chí | Mức đạt yêu cầu | Kết quả thực nghiệm | Đánh giá |
| :--- | :--- | :--- | :---: |
| **Kết nối Redis** | Ứng dụng khởi động thành công, không báo lỗi kết nối. Kiểm tra bằng `redis-cli ping` nhận được `PONG`. | Đã chạy `redis-cli ping` -> Trả về `PONG`. Ứng dụng khởi động trong 2.216 giây không có lỗi. | **ĐẠT (100%)** |
| **Serializer JSON** | Dùng `redis-cli get "cacheKey"` thấy giá trị là chuỗi JSON (ví dụ: `{"id":1,"name":"Phở"}`) chứ không phải dạng nhị phân. | Kết quả trả về: `{"@class":"com.grabfood.model.MenuItem","id":1,"name":"Phở", ...}` hoàn toàn là chuỗi JSON đọc được. | **ĐẠT (100%)** |
| **Sử dụng annotation** | Đã thêm `@EnableCaching` và `@Cacheable` hoạt động đúng, dữ liệu được lưu vào Redis sau lần gọi service đầu tiên. | Đã cấu hình `@EnableCaching` trên `CacheConfig`, `@Cacheable` trên `MenuService`. Lần 1 gọi DB, lần 2 lấy từ Redis (thời gian giảm từ 1445ms xuống 33ms). | **ĐẠT (100%)** |
| **Cấu hình đúng thông số** | Các giá trị host, port, timeout, pool trong `application.yml` được áp dụng (kiểm tra qua log khởi động). | Log khởi động in rõ: Host `localhost`, Port `6379`, Timeout `PT2S`, Lettuce Pool Max-Active `10`. | **ĐẠT (100%)** |
| **Không hard-code** | Không nhập trực tiếp host, port, timeout trong code Java; tất cả lấy từ file cấu hình qua `@Value`. | Toàn bộ thông số được inject qua `@Value` từ `application.yml`. | **ĐẠT (100%)** |
