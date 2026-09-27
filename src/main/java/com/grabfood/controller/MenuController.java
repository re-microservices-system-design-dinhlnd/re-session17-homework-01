package com.grabfood.controller;

import com.grabfood.model.MenuItem;
import com.grabfood.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller cung cấp API truy xuất thực đơn và kiểm tra Distributed Cache
 */
@RestController
@RequestMapping("/api/v1/restaurants")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * API 1: Lấy danh sách món ăn theo nhà hàng (Cache Key: 'restaurant_menu::<restaurantId>')
     */
    @GetMapping("/{restaurantId}/menu")
    public ResponseEntity<Map<String, Object>> getRestaurantMenu(@PathVariable String restaurantId) {
        long start = System.currentTimeMillis();
        List<MenuItem> items = menuService.getMenuByRestaurant(restaurantId);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("restaurantId", restaurantId);
        response.put("totalItems", items.size());
        response.put("responseTimeMs", duration);
        response.put("dataSource", duration > 400 ? "DATABASE (Cache Miss - Query simulated)" : "REDIS DISTRIBUTED CACHE (Cache Hit)");
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    /**
     * API 2: Lấy chi tiết món ăn theo ID (Cache Key: 'dish::<id>')
     */
    @GetMapping("/dishes/{id}")
    public ResponseEntity<Map<String, Object>> getDishById(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        MenuItem dish = menuService.getMenuItemById(id);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dishId", id);
        response.put("responseTimeMs", duration);
        response.put("dataSource", duration > 400 ? "DATABASE (Cache Miss - Query simulated)" : "REDIS DISTRIBUTED CACHE (Cache Hit)");
        response.put("dish", dish);

        return ResponseEntity.ok(response);
    }

    /**
     * API 3: Xóa cache thực đơn nhà hàng (Cache Invalidation)
     */
    @DeleteMapping("/{restaurantId}/menu/cache")
    public ResponseEntity<Map<String, String>> evictMenuCache(@PathVariable String restaurantId) {
        menuService.evictMenuCache(restaurantId);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Đã xóa cache cho restaurantId: " + restaurantId);
        return ResponseEntity.ok(response);
    }

    /**
     * API 4: Xóa cache món ăn cụ thể
     */
    @DeleteMapping("/dishes/{id}/cache")
    public ResponseEntity<Map<String, String>> evictDishCache(@PathVariable Long id) {
        menuService.evictDishCache(id);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Đã xóa cache cho dish id: " + id);
        return ResponseEntity.ok(response);
    }
}
