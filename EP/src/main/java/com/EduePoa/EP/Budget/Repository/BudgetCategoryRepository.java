package com.EduePoa.EP.Budget.Repository;

import com.EduePoa.EP.Budget.Entity.BudgetCategory;
import com.EduePoa.EP.Budget.Enum.CategoryStatus;
import com.EduePoa.EP.Budget.Enum.CategoryType;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetCategoryRepository extends TenantAwareRepository<BudgetCategory, Long> {

    Optional<BudgetCategory> findByNameIgnoreCaseAndStatus(String name, CategoryStatus status);

    boolean existsByNameIgnoreCaseAndStatus(String name, CategoryStatus status);

    List<BudgetCategory> findByStatus(CategoryStatus status);

    Optional<BudgetCategory> findByMappingKeyAndTypeAndStatus(String mappingKey, CategoryType type, CategoryStatus status);

    boolean existsByMappingKeyAndTypeAndStatus(String mappingKey, CategoryType type, CategoryStatus status);

    @Query("SELECT c FROM BudgetCategory c WHERE " +
            "(:type IS NULL OR c.type = :type) " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BudgetCategory> findByFilters(@Param("type") CategoryType type,
                                       @Param("status") CategoryStatus status,
                                       @Param("search") String search,
                                       Pageable pageable);
}
