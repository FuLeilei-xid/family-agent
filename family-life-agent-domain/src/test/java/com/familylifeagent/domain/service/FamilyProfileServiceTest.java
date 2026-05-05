package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FamilyProfileService — covers save, query, queryOrDefault,
 * startup recovery from USER.md, and parseUserProfile edge cases.
 */
@ExtendWith(MockitoExtension.class)
class FamilyProfileServiceTest {

    @Mock
    private FamilyMemoryService familyMemoryService;

    private FamilyProfileService profileService;

    @BeforeEach
    void setUp() {
        // Simulate empty USER.md on startup — init() falls back to defaults
        when(familyMemoryService.readUserProfile()).thenReturn("");
        profileService = new FamilyProfileService(familyMemoryService);
        profileService.init();
    }

    // ======================== Save / Query ========================

    @Test
    @DisplayName("save() should persist to cache and sync to USER.md")
    void testSavePersistsAndSyncs() {
        FamilyProfileDTO profile = FamilyProfileDTO.builder()
                .userId(2001L)
                .familySize(4)
                .hasElderly(Boolean.FALSE)
                .hasChild(Boolean.TRUE)
                .budgetRange("100-200")
                .defaultArea("滨江")
                .distancePreference("中")
                .build();

        FamilyProfileDTO saved = profileService.save(profile);

        assertNotNull(saved);
        assertEquals(2001L, saved.getUserId());
        assertEquals("滨江", saved.getDefaultArea());

        // Verify sync was called
        verify(familyMemoryService).syncProfile(profile);
    }

    @Test
    @DisplayName("save() with null userId should throw")
    void testSaveNullUserIdThrows() {
        FamilyProfileDTO profile = FamilyProfileDTO.builder()
                .familySize(2)
                .build();

        assertThrows(IllegalArgumentException.class, () -> profileService.save(profile));
    }

    @Test
    @DisplayName("save() with null DTO should throw")
    void testSaveNullDtoThrows() {
        assertThrows(IllegalArgumentException.class, () -> profileService.save(null));
    }

    @Test
    @DisplayName("query() should return cached profile after save")
    void testQueryReturnsCachedProfile() {
        FamilyProfileDTO profile = FamilyProfileDTO.builder()
                .userId(3001L)
                .familySize(5)
                .hasElderly(Boolean.TRUE)
                .hasChild(Boolean.TRUE)
                .budgetRange("200+")
                .defaultArea("上城")
                .distancePreference("远")
                .build();

        profileService.save(profile);

        FamilyProfileDTO queried = profileService.query(3001L);
        assertNotNull(queried);
        assertEquals(5, queried.getFamilySize());
        assertEquals("上城", queried.getDefaultArea());
    }

    @Test
    @DisplayName("query() with null userId should return null")
    void testQueryNullUserId() {
        assertNull(profileService.query(null));
    }

    @Test
    @DisplayName("query() unknown userId should return null")
    void testQueryUnknownUserId() {
        assertNull(profileService.query(9999L));
    }

    @Test
    @DisplayName("queryOrDefault() should return default for unknown userId")
    void testQueryOrDefaultReturnsDefault() {
        FamilyProfileDTO result = profileService.queryOrDefault(9999L);
        assertNotNull(result);
        assertEquals(9999L, result.getUserId());
        assertEquals(3, result.getFamilySize()); // default
        assertTrue(result.getHasElderly());
        assertTrue(result.getHasChild());
        assertEquals("50-120", result.getBudgetRange());
    }

    @Test
    @DisplayName("queryOrDefault() should return cached profile over default")
    void testQueryOrDefaultPrefersCached() {
        FamilyProfileDTO profile = FamilyProfileDTO.builder()
                .userId(4001L)
                .familySize(2)
                .hasElderly(Boolean.FALSE)
                .hasChild(Boolean.FALSE)
                .budgetRange("50以内")
                .defaultArea("拱墅")
                .distancePreference("近")
                .build();
        profileService.save(profile);

        FamilyProfileDTO result = profileService.queryOrDefault(4001L);
        assertEquals(2, result.getFamilySize());
        assertEquals("拱墅", result.getDefaultArea());
    }

    // ======================== Startup Recovery from USER.md ========================

    @Test
    @DisplayName("init() should recover profile from USER.md when available")
    void testInitRecoversFromUserMd() {
        // Simulate a USER.md with saved data
        String userMdContent = """
                # 家庭画像

                - 用户ID：5001
                - 家庭人数：6
                - 是否有老人：true
                - 是否有儿童：false
                - 预算范围：150-300
                - 默认区域：余杭
                - 距离偏好：中
                """;

        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenReturn(userMdContent);

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init();

        FamilyProfileDTO recovered = service.query(5001L);
        assertNotNull(recovered, "Should recover profile from USER.md");
        assertEquals(5001L, recovered.getUserId());
        assertEquals(6, recovered.getFamilySize());
        assertTrue(recovered.getHasElderly());
        assertFalse(recovered.getHasChild());
        assertEquals("150-300", recovered.getBudgetRange());
        assertEquals("余杭", recovered.getDefaultArea());
        assertEquals("中", recovered.getDistancePreference());
    }

    @Test
    @DisplayName("init() should handle empty USER.md gracefully")
    void testInitHandlesEmptyUserMd() {
        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenReturn("");

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init();

        // Should fall back to default mock
        FamilyProfileDTO defaultProfile = service.query(1001L);
        assertNotNull(defaultProfile);
        assertEquals(3, defaultProfile.getFamilySize());
        assertEquals("西湖", defaultProfile.getDefaultArea());
    }

    @Test
    @DisplayName("init() should handle null USER.md gracefully")
    void testInitHandlesNullUserMd() {
        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenReturn(null);

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init();

        FamilyProfileDTO defaultProfile = service.query(1001L);
        assertNotNull(defaultProfile);
        assertEquals(3, defaultProfile.getFamilySize());
    }

    @Test
    @DisplayName("init() should handle malformed USER.md gracefully")
    void testInitHandlesMalformedUserMd() {
        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenReturn("这不是有效的家庭画像数据\n只是一些乱码");

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init();

        // Fallback to defaults since no valid userId extracted
        FamilyProfileDTO defaultProfile = service.query(1001L);
        assertNotNull(defaultProfile);
    }

    @Test
    @DisplayName("init() should handle exception from readUserProfile gracefully")
    void testInitHandlesReadException() {
        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenThrow(new RuntimeException("Disk failure"));

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init(); // Should not throw

        FamilyProfileDTO defaultProfile = service.query(1001L);
        assertNotNull(defaultProfile, "Should fall back to defaults on error");
    }

    // ======================== Parse USER.md with 中文 format ========================

    @Test
    @DisplayName("parseUserProfile should handle '是/否' boolean format")
    void testParseChineseBoolean() {
        String userMd = """
                # 家庭画像

                - 用户ID：7001
                - 家庭人数：4
                - 是否有老人：是
                - 是否有儿童：否
                - 预算范围：80-150
                - 默认区域：滨江
                - 距离偏好：近
                """;

        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenReturn(userMd);

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init();

        FamilyProfileDTO profile = service.query(7001L);
        assertNotNull(profile);
        assertTrue(profile.getHasElderly(), "'是' should parse as true");
        assertFalse(profile.getHasChild(), "'否' should parse as false");
    }

    @Test
    @DisplayName("parseUserProfile should handle mixed 中英文 separators")
    void testParseMixedSeparators() {
        String userMd = """
                # 家庭画像

                - 用户ID: 8001
                - 家庭人数: 5
                - 是否有老人: true
                - 是否有儿童: false
                """;

        FamilyMemoryService mockMemory = mock(FamilyMemoryService.class);
        when(mockMemory.readUserProfile()).thenReturn(userMd);

        FamilyProfileService service = new FamilyProfileService(mockMemory);
        service.init();

        FamilyProfileDTO profile = service.query(8001L);
        assertNotNull(profile);
        assertEquals(5, profile.getFamilySize());
        assertTrue(profile.getHasElderly());
        assertFalse(profile.getHasChild());
    }

    // ======================== Update existing profile ========================

    @Test
    @DisplayName("Re-saving a profile should update cache and sync again")
    void testResaveUpdatesProfile() {
        FamilyProfileDTO v1 = FamilyProfileDTO.builder()
                .userId(9001L)
                .familySize(3)
                .hasElderly(Boolean.TRUE)
                .hasChild(Boolean.TRUE)
                .budgetRange("50-100")
                .defaultArea("西湖")
                .distancePreference("近")
                .build();
        profileService.save(v1);

        // Update: child grew up, no more children
        FamilyProfileDTO v2 = FamilyProfileDTO.builder()
                .userId(9001L)
                .familySize(3)
                .hasElderly(Boolean.TRUE)
                .hasChild(Boolean.FALSE)
                .budgetRange("80-150")
                .defaultArea("滨江")
                .distancePreference("中")
                .build();
        FamilyProfileDTO saved = profileService.save(v2);

        FamilyProfileDTO queried = profileService.query(9001L);
        assertFalse(queried.getHasChild(), "Child flag should be updated");
        assertEquals("滨江", queried.getDefaultArea(), "Area should be updated");

        // Sync should have been called twice (once per save)
        verify(familyMemoryService, times(2)).syncProfile(any());
    }

    @Test
    @DisplayName("Multiple users should coexist in cache independently")
    void testMultipleUsersInCache() {
        FamilyProfileDTO userA = FamilyProfileDTO.builder()
                .userId(1001L).familySize(3).hasElderly(Boolean.TRUE).hasChild(Boolean.TRUE)
                .budgetRange("50-100").defaultArea("西湖").distancePreference("近").build();
        FamilyProfileDTO userB = FamilyProfileDTO.builder()
                .userId(2001L).familySize(2).hasElderly(Boolean.FALSE).hasChild(Boolean.FALSE)
                .budgetRange("100-200").defaultArea("滨江").distancePreference("远").build();

        profileService.save(userA);
        profileService.save(userB);

        FamilyProfileDTO qA = profileService.query(1001L);
        FamilyProfileDTO qB = profileService.query(2001L);

        assertEquals(3, qA.getFamilySize());
        assertEquals(2, qB.getFamilySize());
        assertEquals("西湖", qA.getDefaultArea());
        assertEquals("滨江", qB.getDefaultArea());
    }
}
