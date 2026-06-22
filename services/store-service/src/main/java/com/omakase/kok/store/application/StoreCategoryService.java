package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.CreateStoreCategoryCommand;
import com.omakase.kok.store.application.command.UpdateStoreCategoryCommand;
import com.omakase.kok.store.application.result.StoreCategoryResult;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
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
    private final StoreRepository storeRepository;

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

        validateNoDuplicate(command.getName(), command.getSortOrder(), parent, null);

        StoreCategory category = StoreCategory.create(command.getName(), command.getSortOrder(), parent);
        return StoreCategoryResult.from(storeCategoryRepository.save(category));
    }

    @Transactional
    public StoreCategoryResult updateCategory(UpdateStoreCategoryCommand command) {
        StoreCategory category = storeCategoryRepository.findCategory(command.getCategoryId())
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        String newName = command.getName() != null ? command.getName() : category.getName();
        Integer newSortOrder = command.getSortOrder() != null ? command.getSortOrder() : category.getSortOrder();
        validateNoDuplicate(newName, newSortOrder, category.getParent(), category.getCategoryId());

        category.update(command.getName(), command.getSortOrder());
        return StoreCategoryResult.from(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId, UUID deletedBy) {
        StoreCategory category = storeCategoryRepository.findCategory(categoryId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        // 대분류: 활성 자식 카테고리가 있으면 삭제 불가
        if (!category.isSubCategory()
                && storeCategoryRepository.existsActiveChildren(category)) {
            throw new BaseException(StoreErrorCode.CATEGORY_HAS_CHILDREN);
        }

        // 소분류: 해당 카테고리를 사용 중인 활성 매장이 있으면 삭제 불가
        if (category.isSubCategory()
                && storeRepository.existsActiveStoreByCategory(category)) {
            throw new BaseException(StoreErrorCode.CATEGORY_HAS_STORES);
        }

        category.delete(deletedBy);
    }

    private void validateNoDuplicate(String name, Integer sortOrder, StoreCategory parent, UUID excludeId) {
        if (storeCategoryRepository.existsActiveSiblingByName(name, parent, excludeId)) {
            throw new BaseException(StoreErrorCode.CATEGORY_DUPLICATE_NAME);
        }
        if (sortOrder != null && storeCategoryRepository.existsActiveSiblingBySortOrder(sortOrder, parent, excludeId)) {
            throw new BaseException(StoreErrorCode.CATEGORY_DUPLICATE_SORT_ORDER);
        }
    }

    public List<StoreCategoryResult> getAllCategories() {
        return storeCategoryRepository.findAllCategories().stream()
                .map(StoreCategoryResult::withChildren)
                .toList();
    }

}
