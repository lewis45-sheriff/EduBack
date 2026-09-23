package com.EduePoa.EP.Budget.Service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCategoryCreateRequest;
import com.EduePoa.EP.Budget.DTO.Request.BudgetCategoryUpdateRequest;
import com.EduePoa.EP.Budget.DTO.Response.BudgetCategoryResponse;
import com.EduePoa.EP.Budget.Entity.BudgetCategory;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Budget.Repository.BudgetCategoryRepository;
import com.EduePoa.EP.Budget.Repository.BudgetLineRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BudgetCategoryServiceImpl implements BudgetCategoryService {

    private final BudgetCategoryRepository categoryRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final UserRepository userRepository;

    @Override
    @Audit(module = "BUDGET_CATEGORY", action = "CREATE")
    @Transactional
    public CustomResponse<?> create(BudgetCategoryCreateRequest request) {
        CustomResponse<BudgetCategoryResponse> response = new CustomResponse<>();
        try {
            if (categoryRepository.existsByNameIgnoreCaseAndStatus(request.getName().trim(), CategoryStatus.ACTIVE)) {
                return conflict("An active budget category with this name already exists");
            }
            // A referenceType may map to at most one active category per side (type).
            if (StringUtils.hasText(request.getMappingKey())
                    && categoryRepository.existsByMappingKeyAndTypeAndStatus(
                    request.getMappingKey().trim(), request.getType(), CategoryStatus.ACTIVE)) {
                return conflict("Another active " + request.getType()
                        + " category is already mapped to '" + request.getMappingKey().trim() + "'");
            }

            User currentUser = getCurrentUser();
            BudgetCategory category = BudgetCategory.builder()
                    .name(request.getName().trim())
                    .type(request.getType())
                    .description(request.getDescription())
                    .status(CategoryStatus.ACTIVE)
                    .mappingKey(StringUtils.hasText(request.getMappingKey()) ? request.getMappingKey().trim() : null)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .build();

            BudgetCategory saved = categoryRepository.save(category);

            response.setMessage("Budget category created successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.CREATED.value());
        } catch (Exception e) {
            return fail(e);
        }
        return response;
    }

    @Override
    @Audit(module = "BUDGET_CATEGORY", action = "UPDATE")
    @Transactional
    public CustomResponse<?> update(Long id, BudgetCategoryUpdateRequest request) {
        CustomResponse<BudgetCategoryResponse> response = new CustomResponse<>();
        try {
            BudgetCategory existing = categoryRepository.findById(id).orElse(null);
            if (existing == null) {
                return notFound("Budget category not found with id: " + id);
            }

            String newName = request.getName().trim();
            categoryRepository.findByNameIgnoreCaseAndStatus(newName, CategoryStatus.ACTIVE)
                    .filter(c -> !c.getId().equals(id))
                    .ifPresent(c -> {
                        throw new IllegalStateException("An active budget category with this name already exists");
                    });

            if (StringUtils.hasText(request.getMappingKey())) {
                categoryRepository.findByMappingKeyAndTypeAndStatus(
                                request.getMappingKey().trim(), request.getType(), CategoryStatus.ACTIVE)
                        .filter(c -> !c.getId().equals(id))
                        .ifPresent(c -> {
                            throw new IllegalStateException("Another active " + request.getType()
                                    + " category is already mapped to '" + request.getMappingKey().trim() + "'");
                        });
            }

            existing.setName(newName);
            existing.setType(request.getType());
            existing.setDescription(request.getDescription());
            existing.setMappingKey(StringUtils.hasText(request.getMappingKey()) ? request.getMappingKey().trim() : null);
            existing.setUpdatedAt(LocalDateTime.now());

            BudgetCategory saved = categoryRepository.save(existing);

            response.setMessage("Budget category updated successfully");
            response.setEntity(toResponse(saved));
            response.setStatusCode(HttpStatus.OK.value());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        } catch (Exception e) {
            return fail(e);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getById(Long id) {
        CustomResponse<BudgetCategoryResponse> response = new CustomResponse<>();
        try {
            BudgetCategory category = categoryRepository.findById(id).orElse(null);
            if (category == null) {
                return notFound("Budget category not found with id: " + id);
            }
            response.setMessage("Budget category fetched successfully");
            response.setEntity(toResponse(category));
            response.setStatusCode(HttpStatus.OK.value());
        } catch (Exception e) {
            return fail(e);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomResponse<?> getAll(int page, int size, String sortBy, String sortDir,
                                    CategoryType type, CategoryStatus status, String search) {
        CustomResponse<Page<BudgetCategoryResponse>> response = new CustomResponse<>();
        try {
            Sort sort = Sort.by(
                    "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC,
                    StringUtils.hasText(sortBy) ? sortBy : "createdAt");
            Pageable pageable = PageRequest.of(page, size, sort);

            String searchTerm = StringUtils.hasText(search) ? search : null;
            Page<BudgetCategoryResponse> result = categoryRepository
                    .findByFilters(type, status, searchTerm, pageable)
                    .map(this::toResponse);

            response.setMessage("Budget categories fetched successfully");
            response.setEntity(result);
            response.setStatusCode(HttpStatus.OK.value());
        } catch (Exception e) {
            return fail(e);
        }
        return response;
    }

    @Override
    @Audit(module = "BUDGET_CATEGORY", action = "DELETE")
    @Transactional
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            BudgetCategory category = categoryRepository.findById(id).orElse(null);
            if (category == null) {
                return notFound("Budget category not found with id: " + id);
            }

            long usage = budgetLineRepository.countByCategoryId(id);
            if (usage > 0) {
                // Preserve history: deactivate instead of physical delete.
                category.setStatus(CategoryStatus.INACTIVE);
                category.setUpdatedAt(LocalDateTime.now());
                categoryRepository.save(category);
                response.setMessage("Budget category has been used and was deactivated (not deleted) to preserve history");
            } else {
                categoryRepository.deleteById(id);
                response.setMessage("Budget category deleted successfully");
            }
            response.setEntity(null);
            response.setStatusCode(HttpStatus.OK.value());
        } catch (Exception e) {
            return fail(e);
        }
        return response;
    }

    // ---- helpers ----

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private BudgetCategoryResponse toResponse(BudgetCategory c) {
        String createdByName = null;
        if (c.getCreatedBy() != null) {
            User u = c.getCreatedBy();
            createdByName = ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                    + (u.getLastName() != null ? u.getLastName() : "")).trim();
            if (createdByName.isEmpty()) {
                createdByName = u.getEmail();
            }
        }
        return BudgetCategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .type(c.getType())
                .description(c.getDescription())
                .status(c.getStatus())
                .mappingKey(c.getMappingKey())
                .createdByName(createdByName)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private <T> CustomResponse<T> conflict(String message) {
        CustomResponse<T> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(null);
        r.setStatusCode(HttpStatus.CONFLICT.value());
        return r;
    }

    private <T> CustomResponse<T> notFound(String message) {
        CustomResponse<T> r = new CustomResponse<>();
        r.setMessage(message);
        r.setEntity(null);
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        return r;
    }

    private <T> CustomResponse<T> fail(Exception e) {
        CustomResponse<T> r = new CustomResponse<>();
        if (e instanceof OptimisticLockException || e instanceof ObjectOptimisticLockingFailureException) {
            r.setMessage("The budget category was modified by another user. Please reload and try again.");
            r.setStatusCode(HttpStatus.CONFLICT.value());
        } else {
            r.setMessage(e.getMessage());
            r.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        r.setEntity(null);
        return r;
    }
}
