package com.grabfood.service;

import com.grabfood.model.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service xử lý nghiệp vụ thực đơn món ăn cho hệ thống GrabFood.
 * Sử dụng annotation @Cacheable để đệm dữ liệu vào Redis phân tán theo restaurantId hoặc itemId.
 */
@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    /**
     * Lấy danh sách món ăn theo restaurantId.
     * - Khi chưa có cache (Cache Miss): Thực hiện truy vấn DB giả lập (sleep 1000ms),
     *   sau đó lưu kết quả vào Redis Cache với key 'restaurant_menu::<restaurantId>'.
     * - Khi đã có cache (Cache Hit): Lấy trực tiếp từ Redis trong vài mili-giây, bỏ qua method.
     */
    @Cacheable(value = "restaurant_menu", key = "#restaurantId")
    public List<MenuItem> getMenuByRestaurant(String restaurantId) {
        log.info(">>> [DATABASE QUERY] Đang truy vấn CSDL lấy danh sách món ăn cho nhà hàng: {}", restaurantId);

        try {
            // Giả lập độ trễ kết nối và truy vấn CSDL 1 giây
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<MenuItem> menu = new ArrayList<>();
        menu.add(MenuItem.builder()
                .id(1L)
                .name("Phở")
                .price(new BigDecimal("65000"))
                .description("Phở bò tái nạm gầu gân kèm quẩy giòn và trứng chần")
                .category("Món nước")
                .restaurantId(restaurantId)
                .available(true)
                .build());

        menu.add(MenuItem.builder()
                .id(2L)
                .name("Cơm Tấm Sườn Bì Chả")
                .price(new BigDecimal("55000"))
                .description("Cơm tấm hạt tấm thơm, sườn nướng mật ong đậm đà")
                .category("Cơm")
                .restaurantId(restaurantId)
                .available(true)
                .build());

        menu.add(MenuItem.builder()
                .id(3L)
                .name("Bánh Mì Pate Chả Lụa")
                .price(new BigDecimal("30000"))
                .description("Bánh mì giòn rụm kẹp pate béo ngậy, sốt bơ trứng truyền thống")
                .category("Món nhẹ")
                .restaurantId(restaurantId)
                .available(true)
                .build());

        menu.add(MenuItem.builder()
                .id(4L)
                .name("Trà Sữa Trân Châu Đường Đen")
                .price(new BigDecimal("45000"))
                .description("Trà sữa sữa tươi trân châu đường đen Đài Loan thơm ngon")
                .category("Đồ uống")
                .restaurantId(restaurantId)
                .available(true)
                .build());

        log.info(">>> [DATABASE RESULT] Đã tải thành công {} món ăn cho nhà hàng: {}", menu.size(), restaurantId);
        return menu;
    }

    /**
     * Lấy chi tiết một món ăn theo ID.
     * Sử dụng @Cacheable: kết quả lưu vào Redis với key 'dish::<id>'
     * Giá trị lưu trong Redis là chuỗi JSON: {"@class":"...","id":1,"name":"Phở",...}
     */
    @Cacheable(value = "dish", key = "#id")
    public MenuItem getMenuItemById(Long id) {
        log.info(">>> [DATABASE QUERY] Đang truy vấn CSDL lấy chi tiết món ăn id: {}", id);

        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return MenuItem.builder()
                .id(id)
                .name("Phở")
                .price(new BigDecimal("65000"))
                .description("Phở bò Hà Nội gia truyền, nước dùng xương hầm 24 giờ")
                .category("Món nước")
                .restaurantId("RES-001")
                .available(true)
                .build();
    }

    /**
     * Xóa cache thực đơn của một nhà hàng
     */
    @CacheEvict(value = "restaurant_menu", key = "#restaurantId")
    public void evictMenuCache(String restaurantId) {
        log.info(">>> [CACHE EVICT] Đã xóa cache thực đơn của nhà hàng: {}", restaurantId);
    }

    /**
     * Xóa cache món ăn cụ thể
     */
    @CacheEvict(value = "dish", key = "#id")
    public void evictDishCache(Long id) {
        log.info(">>> [CACHE EVICT] Đã xóa cache món ăn id: {}", id);
    }
}
