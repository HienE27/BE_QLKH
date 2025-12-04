package com.example.inventory_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client để gọi API sang Product-service
 */
@Component
public class ProductServiceClient {

    private final RestTemplate restTemplate;

    @Value("${product.service.url:http://localhost:8081}")
    private String productServiceUrl;

    public ProductServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        System.out.println("🔧 ProductServiceClient initialized with URL: " + productServiceUrl);
    }

    /**
     * Cập nhật số lượng tồn kho của sản phẩm
     * 
     * @param productId ID sản phẩm
     * @param quantity  Số lượng mới
     */
    public void updateProductQuantity(Long productId, int quantity) {
        String url = productServiceUrl + "/api/products/" + productId + "/quantity";

        try {
            restTemplate.put(url, new UpdateQuantityRequest(quantity));
        } catch (Exception e) {
            // Log lỗi nhưng không throw để không ảnh hưởng luồng chính
            System.err.println("Failed to update product quantity: " + e.getMessage());
        }
    }

    /**
     * Tăng số lượng tồn kho
     */
    public void increaseQuantity(Long productId, int amount) {
        String url = productServiceUrl + "/api/products/" + productId + "/quantity/increase";

        try {
            System.out.println("🔵 Calling Product-service: " + url + " with amount: " + amount);
            restTemplate.postForObject(url, new QuantityChangeRequest(amount), Void.class);
            System.out.println("✅ Successfully increased quantity for product: " + productId);
        } catch (Exception e) {
            System.err.println("❌ Failed to increase product quantity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Giảm số lượng tồn kho
     */
    public void decreaseQuantity(Long productId, int amount) {
        String url = productServiceUrl + "/api/products/" + productId + "/quantity/decrease";

        try {
            System.out.println("🔵 Calling Product-service: " + url + " with amount: " + amount);
            restTemplate.postForObject(url, new QuantityChangeRequest(amount), Void.class);
            System.out.println("✅ Successfully decreased quantity for product: " + productId);
        } catch (Exception e) {
            System.err.println("❌ Failed to decrease product quantity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Lấy danh sách suppliers theo type
     */
    public java.util.List<SupplierDto> getSuppliersByType(String type) {
        String url = productServiceUrl + "/api/suppliers?type=" + type;

        try {
            System.out.println("🔵 Calling Product-service: " + url);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);
            System.out.println("📦 Response from Product-service: " + response);

            if (response != null && response.get("data") != null) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> dataList = (java.util.List<java.util.Map<String, Object>>) response
                        .get("data");

                java.util.List<SupplierDto> suppliers = new java.util.ArrayList<>();
                for (java.util.Map<String, Object> data : dataList) {
                    SupplierDto dto = new SupplierDto();
                    dto.setId(data.get("id") != null ? ((Number) data.get("id")).longValue() : null);
                    dto.setName((String) data.get("name"));
                    dto.setCode((String) data.get("code"));
                    dto.setPhone((String) data.get("phone"));
                    dto.setAddress((String) data.get("address"));
                    suppliers.add(dto);
                }

                System.out.println("✅ Loaded " + suppliers.size() + " suppliers with type=" + type);
                return suppliers;
            }
            System.err.println("❌ Response is null or data is missing");
            return new java.util.ArrayList<>();
        } catch (Exception e) {
            System.err.println("❌ Failed to get suppliers by type: " + e.getMessage());
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Lấy thông tin supplier theo ID
     */
    public SupplierDto getSupplier(Long supplierId) {
        String url = productServiceUrl + "/api/suppliers/" + supplierId;

        try {
            System.out.println("🔵 Calling Product-service: " + url);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);
            System.out.println("📦 Response from Product-service: " + response);

            if (response != null && response.get("data") != null) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
                System.out.println("📦 Data from response: " + data);

                SupplierDto dto = new SupplierDto();
                dto.setId(data.get("id") != null ? ((Number) data.get("id")).longValue() : null);
                dto.setName((String) data.get("name"));
                dto.setCode((String) data.get("code"));
                dto.setPhone((String) data.get("phone"));
                dto.setAddress((String) data.get("address"));
                dto.setType((String) data.get("type")); // Lấy supplier type

                System.out.println("✅ Supplier DTO created: id=" + dto.getId() + ", name=" + dto.getName() + ", type="
                        + dto.getType());
                return dto;
            }
            System.err.println("❌ Response is null or data is missing");
            return null;
        } catch (Exception e) {
            System.err.println("❌ Failed to get supplier info: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lấy danh sách sản phẩm theo kho
     */
    public java.util.List<ProductDto> getProductsByStoreId(Long storeId) {
        String url = productServiceUrl + "/api/products?storeId=" + storeId;
        try {
            System.out.println("🔵 Calling Product-service: " + url);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);
            if (response != null && response.get("data") != null) {
                Object dataObj = response.get("data");
                if (dataObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> dataList = (java.util.List<java.util.Map<String, Object>>) dataObj;
                    java.util.List<ProductDto> products = new java.util.ArrayList<>();
                    for (java.util.Map<String, Object> map : dataList) {
                        ProductDto dto = new ProductDto();
                        if (map.get("id") != null)
                            dto.setId(((Number) map.get("id")).longValue());
                        if (map.get("code") != null)
                            dto.setCode((String) map.get("code"));
                        if (map.get("name") != null)
                            dto.setName((String) map.get("name"));
                        if (map.get("quantity") != null)
                            dto.setQuantity(((Number) map.get("quantity")).intValue());
                        if (map.get("unitPrice") != null) {
                            dto.setUnitPrice(new java.math.BigDecimal(map.get("unitPrice").toString()));
                        }
                        if (map.get("storeId") != null)
                            dto.setStoreId(((Number) map.get("storeId")).longValue());
                        products.add(dto);
                    }
                    return products;
                }
            }
            return new java.util.ArrayList<>();
        } catch (Exception e) {
            System.err.println("❌ Failed to get products by storeId=" + storeId + ": " + e.getMessage());
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Lấy tồn kho hiện tại của sản phẩm trực tiếp từ Product-service.
     * Dùng làm fallback trong trường hợp chưa có lịch sử nhập/xuất.
     */
    public Integer getProductQuantity(Long productId) {
        String url = productServiceUrl + "/api/products/" + productId;

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);

            if (response != null && response.get("data") != null) {
                Object dataObj = response.get("data");
                if (dataObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> data = (java.util.Map<String, Object>) dataObj;
                    Object quantityObj = data.get("quantity");
                    if (quantityObj instanceof Number) {
                        return ((Number) quantityObj).intValue();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println(
                    "❌ Failed to fetch product quantity for productId=" + productId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Lấy thông tin store/cửa hàng theo ID
     */
    public StoreDto getStore(Long storeId) {
        String url = productServiceUrl + "/api/stores/" + storeId;

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);

            if (response != null && response.get("data") != null) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");

                StoreDto dto = new StoreDto();
                dto.setId(data.get("id") != null ? ((Number) data.get("id")).longValue() : null);
                dto.setName((String) data.get("name"));
                dto.setCode((String) data.get("code"));
                dto.setPhone((String) data.get("phone"));
                dto.setAddress((String) data.get("address"));

                return dto;
            }
            return null;
        } catch (Exception e) {
            System.err.println("❌ Failed to get store info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lấy tất cả sản phẩm (cho dashboard stats)
     */
    public java.util.List<ProductDto> getAllProducts() {
        String url = productServiceUrl + "/api/products";
        try {
            System.out.println("🔵 Calling Product-service: " + url);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);
            if (response != null && response.get("data") != null) {
                Object dataObj = response.get("data");
                if (dataObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> dataList = (java.util.List<java.util.Map<String, Object>>) dataObj;
                    java.util.List<ProductDto> products = new java.util.ArrayList<>();
                    for (java.util.Map<String, Object> map : dataList) {
                        ProductDto dto = new ProductDto();
                        if (map.get("id") != null)
                            dto.setId(((Number) map.get("id")).longValue());
                        if (map.get("code") != null)
                            dto.setCode((String) map.get("code"));
                        if (map.get("name") != null)
                            dto.setName((String) map.get("name"));
                        if (map.get("quantity") != null)
                            dto.setQuantity(((Number) map.get("quantity")).intValue());
                        if (map.get("unitPrice") != null) {
                            Object priceObj = map.get("unitPrice");
                            if (priceObj instanceof Number) {
                                dto.setUnitPrice(new java.math.BigDecimal(priceObj.toString()));
                            } else if (priceObj instanceof String) {
                                dto.setUnitPrice(new java.math.BigDecimal((String) priceObj));
                            }
                        }
                        if (map.get("storeId") != null)
                            dto.setStoreId(((Number) map.get("storeId")).longValue());
                        products.add(dto);
                    }
                    System.out.println("✅ Loaded " + products.size() + " products");
                    return products;
                }
            }
            return new java.util.ArrayList<>();
        } catch (Exception e) {
            System.err.println("❌ Failed to get all products: " + e.getMessage());
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    // DTO for Supplier
    public static class SupplierDto {
        private Long id;
        private String name;
        private String code;
        private String phone;
        private String address;
        private String type; // NCC, INTERNAL, STAFF, ...

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    // DTO for Store
    public static class StoreDto {
        private Long id;
        private String name;
        private String code;
        private String phone;
        private String address;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    // DTO classes
    @SuppressWarnings("unused")
    private static class UpdateQuantityRequest {
        public int quantity;

        public UpdateQuantityRequest(int quantity) {
            this.quantity = quantity;
        }
    }

    @SuppressWarnings("unused")
    private static class QuantityChangeRequest {
        public int amount;

        public QuantityChangeRequest(int amount) {
            this.amount = amount;
        }
    }

    // DTO for Product
    public static class ProductDto {
        private Long id;
        private String code;
        private String name;
        private Integer quantity;
        private java.math.BigDecimal unitPrice;
        private Long storeId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public java.math.BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(java.math.BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public Long getStoreId() {
            return storeId;
        }

        public void setStoreId(Long storeId) {
            this.storeId = storeId;
        }
    }
}
