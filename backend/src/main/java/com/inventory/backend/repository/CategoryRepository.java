package com.inventory.backend.repository;

import com.inventory.backend.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNull();
    boolean existsByName(String name);
}
