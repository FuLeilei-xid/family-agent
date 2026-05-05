package com.familylifeagent.infrastructure.tool;

import com.familylifeagent.infrastructure.entity.ShopDetailEntity;
import com.familylifeagent.infrastructure.entity.ShopEntity;
import com.familylifeagent.infrastructure.entity.ShopTypeEntity;
import com.familylifeagent.infrastructure.entity.VoucherEntity;
import com.familylifeagent.infrastructure.repository.ShopRepository;
import com.familylifeagent.infrastructure.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ShopSearchTool 数据库集成测试。
 * 验证店铺搜索、高级搜索、详情查询和优惠券查询功能。
 */
@ExtendWith(MockitoExtension.class)
class ShopSearchToolTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private VoucherRepository voucherRepository;

    private ShopSearchTool tool;

    private ShopEntity mockShop1;
    private ShopEntity mockShop2;
    private ShopEntity mockShop3;

    @BeforeEach
    void setUp() {
        tool = new ShopSearchTool(shopRepository, voucherRepository);

        ShopTypeEntity restaurantType = new ShopTypeEntity();
        restaurantType.setTypeName("餐厅");

        ShopDetailEntity detail1 = new ShopDetailEntity();
        detail1.setSignatureDishes("清蒸鱼,炒时蔬");
        detail1.setReviewSummary("西湖区老牌餐厅，适合家庭聚餐");

        mockShop1 = new ShopEntity();
        mockShop1.setId(101L);
        mockShop1.setShopName("西湖邻里蒸鲜坊");
        mockShop1.setArea("西湖");
        mockShop1.setAveragePrice(86);
        mockShop1.setRating(4.8);
        mockShop1.setSuitableForElderly(true);
        mockShop1.setHasChildrenPlayArea(true);
        mockShop1.setBusinessHours("10:00-22:00");
        mockShop1.setShopType(restaurantType);
        mockShop1.setDetail(detail1);
        mockShop1.setTags("清淡,蒸菜,家庭友好");

        mockShop2 = new ShopEntity();
        mockShop2.setId(102L);
        mockShop2.setShopName("湖滨亲子面小馆");
        mockShop2.setArea("西湖");
        mockShop2.setAveragePrice(72);
        mockShop2.setRating(4.6);
        mockShop2.setSuitableForElderly(true);
        mockShop2.setHasChildrenPlayArea(true);
        mockShop2.setBusinessHours("08:00-21:00");
        mockShop2.setShopType(restaurantType);
        mockShop2.setDetail(new ShopDetailEntity());
        mockShop2.getDetail().setSignatureDishes("牛肉面,馄饨");
        mockShop2.setTags("面食,儿童餐");

        mockShop3 = new ShopEntity();
        mockShop3.setId(103L);
        mockShop3.setShopName("桂雨家常食堂");
        mockShop3.setArea("西湖");
        mockShop3.setAveragePrice(94);
        mockShop3.setRating(4.7);
        mockShop3.setSuitableForElderly(true);
        mockShop3.setHasChildrenPlayArea(false);
        mockShop3.setBusinessHours("11:00-22:00");
        mockShop3.setShopType(restaurantType);
        mockShop3.setDetail(new ShopDetailEntity());
        mockShop3.getDetail().setReviewSummary("家常菜为主，口味清淡");
        mockShop3.setTags("家常菜,杭帮菜");
    }

    // ==================== shopSearch ====================

    @Test
    @DisplayName("shopSearch: 关键字搜索返回正确结果")
    void testShopSearchByKeyword() {
        when(shopRepository.searchByKeyword("西湖")).thenReturn(List.of(mockShop1, mockShop2));

        List<ShopSearchTool.ShopCandidate> results = tool.shopSearch("西湖");

        assertEquals(2, results.size());
        assertEquals("西湖邻里蒸鲜坊", results.get(0).name());
        assertEquals("西湖", results.get(0).area());
    }

    @Test
    @DisplayName("shopSearch: 空关键字返回所有店铺")
    void testShopSearchEmptyKeyword() {
        when(shopRepository.searchByKeyword(null)).thenReturn(List.of(mockShop1, mockShop2, mockShop3));

        List<ShopSearchTool.ShopCandidate> results = tool.shopSearch(null);
        assertEquals(3, results.size());
    }

    // ==================== shopSearchAdvanced ====================

    @Test
    @DisplayName("shopSearchAdvanced: 按区域和类型搜索")
    void testShopSearchAdvancedByAreaAndType() {
        when(shopRepository.searchAdvanced(eq("西湖"), eq("餐厅"), eq(true), eq(true), eq(50), eq(100)))
                .thenReturn(List.of(mockShop1, mockShop2));

        ShopSearchTool.ShopSearchAdvancedResult result = tool.shopSearchAdvanced(
                "西湖", "50-100", "餐厅", true, true);

        assertEquals("西湖", result.area());
        assertEquals("餐厅", result.shopType());
        assertEquals(2, result.shops().size());
    }

    @Test
    @DisplayName("shopSearchAdvanced: 空参数使用默认值")
    void testShopSearchAdvancedEmptyArea() {
        when(shopRepository.searchAdvanced(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(mockShop1, mockShop2, mockShop3));

        ShopSearchTool.ShopSearchAdvancedResult result = tool.shopSearchAdvanced(
                null, null, null, null, null);

        assertEquals("西湖", result.area(), "空area应使用默认值'西湖'");
        assertEquals("不限", result.shopType(), "空shopType应使用默认值'不限'");
    }

    @Test
    @DisplayName("shopSearchAdvanced: 预算范围解析正确")
    void testShopSearchAdvancedBudgetParsing() {
        when(shopRepository.searchAdvanced(eq("滨江"), eq("餐厅"), isNull(), isNull(), eq(50), eq(100)))
                .thenReturn(List.of());

        ShopSearchTool.ShopSearchAdvancedResult result = tool.shopSearchAdvanced(
                "滨江", "50-100", "餐厅", null, null);

        assertEquals("50-100", result.averageBudgetRange());
    }

    // ==================== shopDetail ====================

    @Test
    @DisplayName("shopDetail: 查询已存在的店铺详情")
    void testShopDetailExists() {
        when(shopRepository.findDetailById(101L)).thenReturn(Optional.of(mockShop1));

        ShopSearchTool.ShopDetailResult result = tool.shopDetail(101L);

        assertEquals(101L, result.shopId());
        assertEquals("西湖邻里蒸鲜坊", result.shopName());
        assertEquals("10:00-22:00", result.businessHours());
        assertEquals(2, result.signatureDishes().size());
        assertTrue(result.signatureDishes().contains("清蒸鱼"));
    }

    @Test
    @DisplayName("shopDetail: 查询不存在的店铺抛出异常")
    void testShopDetailNotFound() {
        when(shopRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> tool.shopDetail(999L));
    }

    @Test
    @DisplayName("shopDetail: null shopId 使用默认1001L")
    void testShopDetailNullId() {
        when(shopRepository.findDetailById(1001L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> tool.shopDetail(null));
    }

    // ==================== voucherList ====================

    @Test
    @DisplayName("voucherList: 查询店铺优惠券")
    void testVoucherList() {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherCode("V001");
        voucher.setTitle("满100减20");
        voucher.setApplicableScope("全场通用");
        voucher.setValidTo(LocalDateTime.of(2025, 12, 31, 23, 59));

        when(shopRepository.findDetailById(101L)).thenReturn(Optional.of(mockShop1));
        when(voucherRepository.findByShopIdOrderByValidToAsc(101L)).thenReturn(List.of(voucher));

        ShopSearchTool.VoucherListResult result = tool.voucherList(101L);

        assertEquals(101L, result.shopId());
        assertEquals("西湖邻里蒸鲜坊", result.shopName());
        assertEquals(1, result.vouchers().size());
        assertEquals("V001", result.vouchers().get(0).voucherId());
        assertEquals("满100减20", result.vouchers().get(0).title());
    }

    // ==================== searchAroundShop ====================

    @Test
    @DisplayName("searchAroundShop: 在基准店铺附近搜索")
    void testSearchAroundShop() {
        when(shopRepository.findDetailById(101L)).thenReturn(Optional.of(mockShop1));
        when(shopRepository.searchAdvanced(eq("西湖"), eq("餐厅"), isNull(), isNull(), eq(50), eq(100)))
                .thenReturn(List.of(mockShop2, mockShop3));

        ShopSearchTool.ShopSearchAdvancedResult result = tool.searchAroundShop(101L, "餐厅", "50-100");

        assertEquals("西湖", result.area());
        assertEquals(2, result.shops().size());
        // 应排除基准店铺101L
        assertFalse(result.shops().stream().anyMatch(s -> s.shopId().equals(101L)));
    }

    @Test
    @DisplayName("searchAroundShop: 基准店铺不存在抛出异常")
    void testSearchAroundShopNotFound() {
        when(shopRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> tool.searchAroundShop(999L, "餐厅", null));
    }
}
