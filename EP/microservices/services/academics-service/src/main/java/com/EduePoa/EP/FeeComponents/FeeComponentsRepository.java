package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeeComponentsRepository extends JpaRepository< FeeComponents,Long> {
    Optional<FeeComponents> findByName(String name);


}
