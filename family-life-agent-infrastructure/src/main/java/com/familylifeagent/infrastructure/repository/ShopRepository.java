package com.familylifeagent.infrastructure.repository;

import com.familylifeagent.infrastructure.entity.ShopEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<ShopEntity, Long> {

    @Query("""
            select s from ShopEntity s
            left join fetch s.shopType st
            left join fetch s.detail d
            where (:keyword is null or :keyword = ''
                    or lower(s.shopName) like lower(concat('%', :keyword, '%'))
                    or lower(s.area) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.tags, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(st.typeName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(st.typeCode, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(st.description, '')) like lower(concat('%', :keyword, '%')))
            order by s.rating desc, s.id asc
            """)
    List<ShopEntity> searchByKeyword(@Param("keyword") String keyword);

    @Query("""
            select s from ShopEntity s
            left join fetch s.shopType st
            left join fetch s.detail d
            where (:area is null or :area = '' or s.area = :area)
              and (:typeKeyword is null or :typeKeyword = ''
                   or lower(coalesce(st.typeName, '')) like lower(concat('%', :typeKeyword, '%'))
                   or lower(coalesce(st.typeCode, '')) like lower(concat('%', :typeKeyword, '%')))
              and (:suitableForElderly is null or s.suitableForElderly = :suitableForElderly)
              and (:hasChildrenPlayArea is null or s.hasChildrenPlayArea = :hasChildrenPlayArea)
              and (:minPrice is null or s.averagePrice >= :minPrice)
              and (:maxPrice is null or s.averagePrice <= :maxPrice)
            order by s.rating desc, s.averagePrice asc, s.id asc
            """)
    List<ShopEntity> searchAdvanced(@Param("area") String area,
                                    @Param("typeKeyword") String typeKeyword,
                                    @Param("suitableForElderly") Boolean suitableForElderly,
                                    @Param("hasChildrenPlayArea") Boolean hasChildrenPlayArea,
                                    @Param("minPrice") Integer minPrice,
                                    @Param("maxPrice") Integer maxPrice);

    @Query("""
            select s from ShopEntity s
            left join fetch s.shopType st
            left join fetch s.detail d
            where s.id = :shopId
            """)
    Optional<ShopEntity> findDetailById(@Param("shopId") Long shopId);
}
