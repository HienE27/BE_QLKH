package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopStockRepository extends JpaRepository<ShopStock, Long> {

    /**
     * Tìm tồn kho của 1 sản phẩm tại 1 kho
     */
    Optional<ShopStock> findByProductIdAndStoreId(Long productId, Long storeId);

    /**
     * Lấy tất cả tồn kho của 1 kho
     */
    List<ShopStock> findByStoreId(Long storeId);

    /**
     * Lấy tất cả tồn kho của 1 sản phẩm (tại tất cả các kho)
     */
    List<ShopStock> findByProductId(Long productId);

    /**
     * Tăng số lượng tồn kho
     */
    @Modifying
    @Query("UPDATE ShopStock s SET s.quantity = s.quantity + :amount WHERE s.productId = :productId AND s.storeId = :storeId")
    int increaseQuantity(@Param("productId") Long productId, @Param("storeId") Long storeId, @Param("amount") Integer amount);

    /**
     * Giảm số lượng tồn kho
     */
    @Modifying
    @Query("UPDATE ShopStock s SET s.quantity = s.quantity - :amount WHERE s.productId = :productId AND s.storeId = :storeId")
    int decreaseQuantity(@Param("productId") Long productId, @Param("storeId") Long storeId, @Param("amount") Integer amount);
}

