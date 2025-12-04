package com.example.inventory_service.service.impl;

import com.example.inventory_service.client.ProductServiceClient;
import com.example.inventory_service.dto.StockDto;
import com.example.inventory_service.dto.StockByStoreDto;
import com.example.inventory_service.entity.ShopExportDetail;
import com.example.inventory_service.entity.ShopImportDetail;
import com.example.inventory_service.entity.ShopStock;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.ShopExportDetailRepository;
import com.example.inventory_service.repository.ShopImportDetailRepository;
import com.example.inventory_service.repository.ShopStockRepository;
import com.example.inventory_service.repository.ShopStoreRepository;
import com.example.inventory_service.service.StockService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StockServiceImpl implements StockService {

    private final ShopImportDetailRepository importDetailRepo;
    private final ShopExportDetailRepository exportDetailRepo;
    private final ProductServiceClient productClient;
    private final ShopStockRepository stockRepo;
    private final ShopStoreRepository storeRepo;

    public StockServiceImpl(ShopImportDetailRepository importDetailRepo,
            ShopExportDetailRepository exportDetailRepo,
            ProductServiceClient productClient,
            ShopStockRepository stockRepo,
            ShopStoreRepository storeRepo) {
        this.importDetailRepo = importDetailRepo;
        this.exportDetailRepo = exportDetailRepo;
        this.productClient = productClient;
        this.stockRepo = stockRepo;
        this.storeRepo = storeRepo;
    }

    // ========= TỒN KHO TỪ shop_stocks (MỚI) =========

    @Override
    public List<StockByStoreDto> getAllStockByStore() {
        List<ShopStock> stocks = stockRepo.findAll();
        List<StockByStoreDto> result = new ArrayList<>();
        
        for (ShopStock stock : stocks) {
            StockByStoreDto dto = new StockByStoreDto();
            dto.setProductId(stock.getProductId());
            dto.setStoreId(stock.getStoreId());
            dto.setQuantity(stock.getQuantity());
            dto.setMinStock(stock.getMinStock());
            dto.setMaxStock(stock.getMaxStock());
            
            // Lấy thông tin kho
            storeRepo.findById(stock.getStoreId()).ifPresent(store -> {
                dto.setStoreName(store.getName());
                dto.setStoreCode(store.getCode());
            });
            
            result.add(dto);
        }
        
        return result;
    }

    @Override
    public List<StockByStoreDto> getStockByProductId(Long productId) {
        List<ShopStock> stocks = stockRepo.findByProductId(productId);
        List<StockByStoreDto> result = new ArrayList<>();
        
        for (ShopStock stock : stocks) {
            StockByStoreDto dto = new StockByStoreDto();
            dto.setProductId(stock.getProductId());
            dto.setStoreId(stock.getStoreId());
            dto.setQuantity(stock.getQuantity());
            dto.setMinStock(stock.getMinStock());
            dto.setMaxStock(stock.getMaxStock());
            
            // Lấy thông tin kho
            storeRepo.findById(stock.getStoreId()).ifPresent(store -> {
                dto.setStoreName(store.getName());
                dto.setStoreCode(store.getCode());
            });
            
            result.add(dto);
        }
        
        return result;
    }

    @Override
    public StockByStoreDto getStockByProductAndStore(Long productId, Long storeId) {
        ShopStock stock = stockRepo.findByProductIdAndStoreId(productId, storeId)
            .orElseGet(() -> {
                // Nếu chưa có record, trả về 0
                ShopStock newStock = new ShopStock();
                newStock.setProductId(productId);
                newStock.setStoreId(storeId);
                newStock.setQuantity(0);
                newStock.setMinStock(0);
                newStock.setMaxStock(999999);
                return newStock;
            });
        
        StockByStoreDto dto = new StockByStoreDto();
        dto.setProductId(stock.getProductId());
        dto.setStoreId(stock.getStoreId());
        dto.setQuantity(stock.getQuantity());
        dto.setMinStock(stock.getMinStock());
        dto.setMaxStock(stock.getMaxStock());
        
        // Lấy thông tin kho
        storeRepo.findById(stock.getStoreId()).ifPresent(store -> {
            dto.setStoreName(store.getName());
            dto.setStoreCode(store.getCode());
        });
        
        return dto;
    }

    @Override
    public List<StockByStoreDto> getStockByStoreId(Long storeId) {
        List<ShopStock> stocks = stockRepo.findByStoreId(storeId);
        List<StockByStoreDto> result = new ArrayList<>();
        
        for (ShopStock stock : stocks) {
            StockByStoreDto dto = new StockByStoreDto();
            dto.setProductId(stock.getProductId());
            dto.setStoreId(stock.getStoreId());
            dto.setQuantity(stock.getQuantity());
            dto.setMinStock(stock.getMinStock());
            dto.setMaxStock(stock.getMaxStock());
            
            // Lấy thông tin kho
            storeRepo.findById(stock.getStoreId()).ifPresent(store -> {
                dto.setStoreName(store.getName());
                dto.setStoreCode(store.getCode());
            });
            
            result.add(dto);
        }
        
        return result;
    }

    @Override
    public StockByStoreDto createOrUpdateStock(com.example.inventory_service.dto.CreateStockRequest request) {
        ShopStock stock = stockRepo.findByProductIdAndStoreId(request.getProductId(), request.getStoreId())
            .orElseGet(() -> {
                ShopStock newStock = new ShopStock();
                newStock.setProductId(request.getProductId());
                newStock.setStoreId(request.getStoreId());
                newStock.setQuantity(0);
                newStock.setMinStock(0);
                newStock.setMaxStock(999999);
                return newStock;
            });

        // Cập nhật số lượng và min/max stock
        if (request.getQuantity() != null) {
            stock.setQuantity(request.getQuantity());
        }
        if (request.getMinStock() != null) {
            stock.setMinStock(request.getMinStock());
        }
        if (request.getMaxStock() != null) {
            stock.setMaxStock(request.getMaxStock());
        }

        stock = stockRepo.save(stock);

        StockByStoreDto dto = new StockByStoreDto();
        dto.setProductId(stock.getProductId());
        dto.setStoreId(stock.getStoreId());
        dto.setQuantity(stock.getQuantity());
        dto.setMinStock(stock.getMinStock());
        dto.setMaxStock(stock.getMaxStock());

        // Lấy thông tin kho
        storeRepo.findById(stock.getStoreId()).ifPresent(store -> {
            dto.setStoreName(store.getName());
            dto.setStoreCode(store.getCode());
        });

        return dto;
    }

    // ========= TỒN KHO TỪ LỊCH SỬ (CŨ - GIỮ LẠI ĐỂ TƯƠNG THÍCH) =========

    @Override
    public List<StockDto> getAllStock() {
        // CHỈ lấy các phiếu đã IMPORTED/EXPORTED (bỏ qua PENDING và CANCELLED)
        List<ShopImportDetail> imports = importDetailRepo.findAllImported();
        List<ShopExportDetail> exports = exportDetailRepo.findAllExported();

        // map productId -> [importQty, exportQty]
        Map<Long, StockDto> map = new HashMap<>();

        // cộng nhập
        for (ShopImportDetail imp : imports) {
            Long productId = imp.getProductId();
            StockDto dto = map.computeIfAbsent(productId, id -> {
                StockDto s = new StockDto();
                s.setProductId(id);
                s.setImportedQty(0);
                s.setExportedQty(0);
                s.setCurrentQty(0);
                return s;
            });
            int qty = imp.getQuantity() != null ? imp.getQuantity() : 0;
            dto.setImportedQty(dto.getImportedQty() + qty);
        }

        // cộng xuất
        for (ShopExportDetail ex : exports) {
            Long productId = ex.getProductId();
            StockDto dto = map.computeIfAbsent(productId, id -> {
                StockDto s = new StockDto();
                s.setProductId(id);
                s.setImportedQty(0);
                s.setExportedQty(0);
                s.setCurrentQty(0);
                return s;
            });
            int qty = ex.getQuantity() != null ? ex.getQuantity() : 0;
            dto.setExportedQty(dto.getExportedQty() + qty);
        }

        // tính tồn kho
        for (StockDto dto : map.values()) {
            dto.setCurrentQty(dto.getImportedQty() - dto.getExportedQty());
        }

        // trả về list
        return new ArrayList<>(map.values());
    }

    @Override
    public StockDto getStockByProduct(Long productId) {
        StockDto dto = new StockDto();
        dto.setProductId(productId);

        // CHỈ lấy các phiếu đã IMPORTED/EXPORTED
        List<ShopImportDetail> imports = importDetailRepo.findImportedByProductId(productId);
        List<ShopExportDetail> exports = exportDetailRepo.findExportedByProductId(productId);

        if (imports.isEmpty() && exports.isEmpty()) {
            Integer remoteQuantity = productClient.getProductQuantity(productId);
            if (remoteQuantity != null) {
                dto.setImportedQty(remoteQuantity);
                dto.setExportedQty(0);
                dto.setCurrentQty(remoteQuantity);
                return dto;
            }
            throw new NotFoundException("Không có dữ liệu tồn kho cho productId = " + productId);
        }

        int imported = imports.stream()
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0)
                .sum();
        int exported = exports.stream()
                .mapToInt(e -> e.getQuantity() != null ? e.getQuantity() : 0)
                .sum();

        dto.setImportedQty(imported);
        dto.setExportedQty(exported);
        dto.setCurrentQty(imported - exported);

        return dto;
    }

    @Override
    public boolean hasEnoughStock(Long productId, int quantity) {
        int current = getCurrentStock(productId);
        return current >= quantity;
    }

    @Override
    public int getCurrentStock(Long productId) {
        try {
            StockDto dto = getStockByProduct(productId);
            return dto.getCurrentQty();
        } catch (NotFoundException e) {
            // Chưa có dữ liệu tồn kho = 0
            return 0;
        }
    }
}