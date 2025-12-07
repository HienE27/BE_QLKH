# Hướng dẫn cập nhật Gemini API Key

## Vấn đề
Sau khi dán API key mới, vẫn gặp lỗi 429 (quota exceeded) vì container chưa load API key mới.

## Giải pháp

### Cách 1: Set Environment Variable trong PowerShell (Khuyến nghị)

1. **Mở PowerShell** và chạy lệnh sau để set API key:
```powershell
$env:GEMINI_API_KEY="YOUR_NEW_API_KEY_HERE"
```

2. **Restart container ai-service**:
```powershell
cd E:\DACN\DACN_QLKH\BE_QLKH
docker-compose restart ai-service
```

3. **Kiểm tra xem API key đã được load chưa**:
```powershell
docker exec ai-service printenv GEMINI_API_KEY
```

### Cách 2: Tạo file .env (Permanent)

1. **Tạo file `.env`** trong thư mục `E:\DACN\DACN_QLKH\BE_QLKH\`:
```
GEMINI_API_KEY=YOUR_NEW_API_KEY_HERE
```

2. **Restart container**:
```powershell
cd E:\DACN\DACN_QLKH\BE_QLKH
docker-compose down ai-service
docker-compose up -d ai-service
```

### Cách 3: Set trực tiếp trong docker-compose.yml (Không khuyến khích - lộ API key)

Chỉnh sửa file `docker-compose.yml`:
```yaml
ai-service:
  environment:
    GEMINI_API_KEY: "YOUR_NEW_API_KEY_HERE"  # Thay vì ${GEMINI_API_KEY:-}
```

Sau đó restart:
```powershell
docker-compose restart ai-service
```

## Lưu ý quan trọng

1. **API key mới phải có quota**: Kiểm tra tại https://ai.dev/usage?tab-rate-limit
2. **Phải restart container** sau khi set API key mới
3. **Kiểm tra logs** để đảm bảo API key đã được load:
```powershell
docker logs ai-service | Select-String "gemini"
```

## Kiểm tra API key có hoạt động

Sau khi restart, kiểm tra logs:
```powershell
docker logs ai-service --tail 50
```

Nếu vẫn lỗi 429, có thể:
- API key mới chưa có quota (cần enable billing hoặc chờ quota được cấp)
- API key chưa được set đúng
- Cần rebuild container: `docker-compose up -d --build ai-service`

