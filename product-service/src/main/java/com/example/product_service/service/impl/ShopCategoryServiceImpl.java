package com.example.product_service.service.impl;

import com.example.product_service.dto.CategoryDto;
import com.example.product_service.dto.CategoryRequest;
import com.example.product_service.entity.ShopCategory;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopCategoryRepository;
import com.example.product_service.service.ShopCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopCategoryServiceImpl implements ShopCategoryService {

    private final ShopCategoryRepository repo;

    @Override
    public List<CategoryDto> getAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public CategoryDto getById(Long id) {
        ShopCategory cat = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
        return toDto(cat);
    }

    @Override
    public CategoryDto create(CategoryRequest req) {
        ShopCategory c = new ShopCategory();
        // Tự động tạo mã nếu không có trong request
        String codeToUse = (req.getCode() != null && !req.getCode().isBlank()) 
            ? req.getCode() 
            : generateCategoryCode();
        req.setCode(codeToUse);
        apply(req, c);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return toDto(repo.save(c));
    }

    /**
     * Tự động tạo mã danh mục: DM + 5 số (ví dụ: DM00001)
     */
    private String generateCategoryCode() {
        String prefix = "DM";
        List<ShopCategory> existing = repo.findByCodeStartingWith(prefix);
        long maxNumber = 0;
        
        for (ShopCategory c : existing) {
            if (c.getCode() != null && c.getCode().length() >= 3) {
                try {
                    String numberPart = c.getCode().substring(2);
                    long num = Long.parseLong(numberPart);
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu không parse được số
                }
            }
        }
        
        long nextNumber = maxNumber + 1;
        return prefix + String.format("%05d", nextNumber);
    }

    @Override
    public CategoryDto update(Long id, CategoryRequest req) {
        ShopCategory c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        apply(req, c);
        c.setUpdatedAt(LocalDateTime.now());
        return toDto(repo.save(c));
    }

    @Override
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Category not found: " + id);
        }
        repo.deleteById(id);
    }

    @Override
    public Page<CategoryDto> search(String code, String name, Pageable pageable) {
        Specification<ShopCategory> spec = Specification.where(
                (root, query, cb) -> cb.conjunction() // always-true base to avoid null chaining
        );

        if (code != null && !code.isBlank()) {
            String like = "%" + code.toLowerCase().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("code")), like));
        }

        if (name != null && !name.isBlank()) {
            String like = "%" + name.toLowerCase().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), like));
        }

        return repo.findAll(spec, pageable)
                .map(this::toDto);
    }

    private void apply(CategoryRequest req, ShopCategory c) {
        c.setCode(req.getCode());
        c.setName(req.getName());
        c.setImage(req.getImage());
        c.setDescription(req.getDescription());
    }

    private CategoryDto toDto(ShopCategory c) {
        CategoryDto dto = new CategoryDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setName(c.getName());
        dto.setImage(c.getImage());
        dto.setDescription(c.getDescription());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }
}
