# Giải thích: Cách tạo Vector Embedding để AI đọc phiếu nhập/xuất

## 📍 Tổng quan

Hệ thống sử dụng **Vector Embedding** để:
1. Chuyển đổi thông tin từ phiếu nhập/xuất thành vector (mảng số)
2. Lưu vector vào Milvus (vector database)
3. Tìm kiếm phiếu tương tự dựa trên vector similarity

## 🔄 Flow hoạt động

```
Ảnh phiếu → OCR (Gemini Vision) → Extract Text → Tạo Embedding → Vector Search → Điền form
```

## 📂 Các file quan trọng

### 1. **EmbeddingService.java** - Tạo Vector từ Text

**Vị trí:** `ai-service/src/main/java/com/example/aiservice/service/EmbeddingService.java`

**Chức năng:**
- Gọi Gemini Embedding API để chuyển text thành vector
- Model: `text-embedding-004` (768 dimensions)
- Input: Text (String)
- Output: List<Float> (768 số thực)

**Code chính:**
```java
public List<Float> generateEmbedding(String text) {
    // Gọi Gemini Embedding API
    // POST /v1beta/models/text-embedding-004:embedContent
    // Trả về: [0.123, 0.456, ..., 0.789] (768 số)
}
```

### 2. **ReceiptOCRService.java** - Xử lý OCR và Vector

**Vị trí:** `ai-service/src/main/java/com/example/aiservice/service/ReceiptOCRService.java`

**Các bước:**

#### Bước 1: OCR ảnh (dòng 42-48)
```java
// Đọc ảnh bằng Gemini Vision API
String extractedData = extractDataFromImage(imageData, receiptType);
// Trả về JSON text từ ảnh
```

#### Bước 2: Parse JSON (dòng 50-51)
```java
ReceiptOCRResponse response = parseGeminiResponse(extractedData, receiptType);
// Parse JSON thành object
```

#### Bước 3: Tạo text để search (dòng 54, 323-348)
```java
String searchText = buildSearchText(response);
// Tạo text từ thông tin đã OCR:
// "Nhà cung cấp: ABC Company Sản phẩm: Gạo Mã: SP001 ..."
```

**Hàm `buildSearchText()`:**
- Lấy supplier/customer name
- Lấy tên và mã sản phẩm
- Ghép thành một chuỗi text

#### Bước 4: Tạo Vector Embedding (dòng 55)
```java
List<Float> embedding = embeddingService.generateEmbedding(searchText);
// Chuyển text thành vector 768 chiều
// Ví dụ: [0.123, -0.456, 0.789, ..., 0.234]
```

#### Bước 5: Tìm kiếm Vector tương tự (dòng 59, 353-435)
```java
enrichWithVectorSearch(response, embedding, receiptType);
// Tìm trong Milvus các phiếu có vector tương tự
// Điền thông tin từ phiếu tương tự vào response
```

#### Bước 6: Lưu Vector vào Milvus (dòng 62, 437-493)
```java
saveToMilvus(response, embedding, extractedData);
// Lưu vector và metadata vào Milvus để dùng sau
```

### 3. **MilvusService.java** - Lưu và Tìm kiếm Vector

**Vị trí:** `ai-service/src/main/java/com/example/aiservice/service/MilvusService.java`

**Chức năng:**

#### Lưu Vector (dòng 129-175)
```java
public void saveEmbedding(...) {
    // Lưu vector vào Milvus collection
    // Kèm metadata: supplierName, customerName, products, etc.
}
```

#### Tìm kiếm Vector (dòng 180-250)
```java
public List<Map<String, Object>> searchSimilar(List<Float> queryEmbedding, int topK) {
    // Tìm top K phiếu có vector gần nhất
    // Sử dụng L2 distance (Euclidean distance)
    // Trả về metadata của các phiếu tương tự
}
```

## 🎯 Ví dụ cụ thể

### Input: Ảnh phiếu nhập
```
[Ảnh chụp phiếu nhập kho]
```

### Bước 1: OCR
```json
{
  "supplierName": "Công ty ABC",
  "products": [
    {"name": "Gạo ST25", "code": "SP001", "quantity": 100}
  ]
}
```

### Bước 2: Tạo Text
```
"Nhà cung cấp: Công ty ABC Sản phẩm: Gạo ST25 Mã: SP001"
```

### Bước 3: Tạo Vector
```
[0.123, -0.456, 0.789, 0.234, ..., -0.567]  // 768 số
```

### Bước 4: Tìm kiếm
- So sánh vector với các vector trong Milvus
- Tìm phiếu có vector gần nhất (L2 distance nhỏ nhất)
- Lấy metadata: supplier phone, address, productId, etc.

### Bước 5: Điền form
- Tự động điền supplier phone, address
- Mapping sản phẩm với productId từ phiếu tương tự

## 🔑 Điểm quan trọng

1. **Vector Embedding** = Cách biểu diễn text thành số để máy tính hiểu được
2. **Milvus** = Database chuyên lưu và tìm kiếm vector
3. **L2 Distance** = Cách tính độ tương tự giữa 2 vector (càng nhỏ = càng giống)
4. **Metadata** = Thông tin bổ sung lưu kèm vector (supplier, products, etc.)

## 📊 Cấu trúc Vector trong Milvus

```
Collection: receipt_embeddings
├── id (auto)
├── receipt_type (IMPORT/EXPORT)
├── supplier_name
├── customer_name
├── embedding (768 dimensions) ← Vector chính
└── metadata (JSON string) ← Thông tin chi tiết
```

## 💡 Tại sao dùng Vector?

- **Tìm kiếm ngữ nghĩa**: Tìm phiếu tương tự dù tên hơi khác
- **Tự động điền**: Dùng thông tin từ phiếu tương tự
- **Học từ lịch sử**: Càng nhiều phiếu → Càng chính xác
