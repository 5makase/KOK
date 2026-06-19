package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.CreateStoreCategoryCommand;
import com.omakase.kok.store.application.command.UpdateStoreCategoryCommand;
import com.omakase.kok.store.application.result.StoreCategoryResult;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreCategoryService {

    private final StoreCategoryRepository storeCategoryRepository;

    @Transactional
    public StoreCategoryResult createCategory(CreateStoreCategoryCommand command) {
        StoreCategory parent = null;

        if (command.getParentId() != null) {
            parent = storeCategoryRepository.findCategory(command.getParentId())
                    .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

            // 2뎁스까지만 허용 - 하위 카테고리의 하위 카테고리 생성 불가
            if (parent.isSubCategory()) {
                throw new BaseException(StoreErrorCode.INVALID_CATEGORY);
            }
        }

        StoreCategory category = StoreCategory.create(command.getName(), command.getSortOrder(), parent);
        return StoreCategoryResult.from(storeCategoryRepository.save(category));
    }

    @Transactional
    public StoreCategoryResult updateCategory(UpdateStoreCategoryCommand command) {
        StoreCategory category = storeCategoryRepository.findCategory(command.getCategoryId())
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        category.update(command.getName(), command.getSortOrder());
        return StoreCategoryResult.from(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId, String deletedBy) {
        StoreCategory category = storeCategoryRepository.findCategory(categoryId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        category.delete(deletedBy);
    }

    public List<StoreCategoryResult> getAllCategories() {
        return storeCategoryRepository.findAllCategories().stream()
                .map(StoreCategoryResult::from)
                .toList();
    }

}
