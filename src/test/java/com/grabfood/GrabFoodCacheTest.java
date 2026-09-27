package com.grabfood;

import com.grabfood.model.MenuItem;
import com.grabfood.service.MenuService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest
class GrabFoodCacheTest {

    @Autowired
    private MenuService menuService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("Kiểm tra kết nối tới Redis thành công (PING - PONG)")
    void testRedisConnection() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pingResult = connection.ping();
            Assertions.assertEquals("PONG", pingResult);
            System.out.println(">>> [TEST PASSED] Redis Connection PING -> " + pingResult);
        }
    }

    @Test
    @DisplayName("Kiểm tra @Cacheable lưu danh sách món ăn theo restaurantId dưới dạng JSON")
    void testCacheableRestaurantMenu() {
        String restaurantId = "RES-TEST-888";

        // Xóa cache cũ nếu có
        menuService.evictMenuCache(restaurantId);

        // Lần 1: Cache Miss - gọi DB giả lập
        long start1 = System.currentTimeMillis();
        List<MenuItem> menu1 = menuService.getMenuByRestaurant(restaurantId);
        long duration1 = System.currentTimeMillis() - start1;

        Assertions.assertNotNull(menu1);
        Assertions.assertFalse(menu1.isEmpty());
        Assertions.assertTrue(duration1 >= 800, "Lần đầu phải tốn ít nhất 800ms do truy vấn CSDL giả lập");
        System.out.println(">>> [Lần 1 - Cache Miss] Thời gian: " + duration1 + "ms");

        // Lần 2: Cache Hit - lấy trực tiếp từ Redis
        long start2 = System.currentTimeMillis();
        List<MenuItem> menu2 = menuService.getMenuByRestaurant(restaurantId);
        long duration2 = System.currentTimeMillis() - start2;

        Assertions.assertNotNull(menu2);
        Assertions.assertEquals(menu1.size(), menu2.size());
        Assertions.assertTrue(duration2 < 100, "Lần hai phải lấy từ Redis trong dưới 100ms");
        System.out.println(">>> [Lần 2 - Cache Hit] Thời gian: " + duration2 + "ms");

        // Kiểm tra key trong Redis: restaurant_menu::RES-TEST-888
        String cacheKey = "restaurant_menu::" + restaurantId;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        Assertions.assertNotNull(cachedJson, "Dữ liệu phải tồn tại trong Redis với key: " + cacheKey);
        System.out.println(">>> [REDIS RAW DATA - restaurant_menu]:\n" + cachedJson);

        // Kiểm tra dữ liệu là chuỗi JSON đọc được, không phải mã nhị phân
        Assertions.assertTrue(cachedJson.contains("Phở"), "Dữ liệu JSON phải chứa chuỗi text có thể đọc được");
        Assertions.assertTrue(cachedJson.startsWith("[") || cachedJson.startsWith("{"), "Dữ liệu phải có định dạng JSON chuỗi");
        Assertions.assertFalse(cachedJson.startsWith("\u00ac\u00ed"), "Dữ liệu KHÔNG được là Java Serialization binary magic header");

        // Dọn dẹp cache
        menuService.evictMenuCache(restaurantId);
    }

    @Test
    @DisplayName("Kiểm tra @Cacheable lưu món ăn đơn lẻ theo dish id dưới dạng JSON")
    void testCacheableSingleDish() {
        Long dishId = 1L;

        // Xóa cache cũ nếu có
        menuService.evictDishCache(dishId);

        // Lần 1: Cache Miss
        long start1 = System.currentTimeMillis();
        MenuItem dish1 = menuService.getMenuItemById(dishId);
        long duration1 = System.currentTimeMillis() - start1;

        Assertions.assertNotNull(dish1);
        Assertions.assertEquals(dishId, dish1.getId());
        Assertions.assertTrue(duration1 >= 700);

        // Lần 2: Cache Hit
        long start2 = System.currentTimeMillis();
        MenuItem dish2 = menuService.getMenuItemById(dishId);
        long duration2 = System.currentTimeMillis() - start2;

        Assertions.assertNotNull(dish2);
        Assertions.assertEquals(dish1.getName(), dish2.getName());
        Assertions.assertTrue(duration2 < 100);

        // Kiểm tra Redis key: dish::1
        String cacheKey = "dish::" + dishId;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        Assertions.assertNotNull(cachedJson);
        System.out.println(">>> [REDIS RAW DATA - dish]:\n" + cachedJson);

        Assertions.assertTrue(cachedJson.contains("\"id\":1") || cachedJson.contains("\"id\": 1"));
        Assertions.assertTrue(cachedJson.contains("\"name\":\"Phở\""));
        Assertions.assertFalse(cachedJson.startsWith("\u00ac\u00ed"));

        // Dọn dẹp cache
        menuService.evictDishCache(dishId);
    }
}
