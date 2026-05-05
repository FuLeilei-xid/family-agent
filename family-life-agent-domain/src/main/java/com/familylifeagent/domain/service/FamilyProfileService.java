package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FamilyProfileService {

    private static final Logger log = LoggerFactory.getLogger(FamilyProfileService.class);

    private final FamilyMemoryService familyMemoryService;
    private final Map<Long, FamilyProfileDTO> profileCache = new ConcurrentHashMap<>();

    public FamilyProfileService(FamilyMemoryService familyMemoryService) {
        this.familyMemoryService = familyMemoryService;
    }

    /**
     * On startup, try to recover saved profile from USER.md.
     */
    @PostConstruct
    public void init() {
        try {
            String userProfileMd = familyMemoryService.readUserProfile();
            if (userProfileMd != null && !userProfileMd.isBlank()) {
                FamilyProfileDTO recovered = parseUserProfile(userProfileMd);
                if (recovered != null && recovered.getUserId() != null) {
                    profileCache.put(recovered.getUserId(), recovered);
                    log.info("Recovered profile from USER.md (userId={})", recovered.getUserId());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Could not recover profile from USER.md, using defaults", e);
        }
        // Fallback to default mock profile if nothing recovered
        FamilyProfileDTO mockProfile = createDefaultProfile();
        mockProfile.setUserId(1001L);
        profileCache.put(mockProfile.getUserId(), mockProfile);
        log.info("Initialized FamilyProfileService with default profile (userId=1001)");
    }

    public FamilyProfileDTO save(FamilyProfileDTO profileDTO) {
        if (profileDTO == null || profileDTO.getUserId() == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        profileCache.put(profileDTO.getUserId(), profileDTO);
        // Also persist to USER.md immediately
        familyMemoryService.syncProfile(profileDTO);
        log.info("Saved and synced profile (userId={}) to cache and USER.md", profileDTO.getUserId());
        return profileDTO;
    }

    public FamilyProfileDTO query(Long userId) {
        if (userId == null) {
            return null;
        }
        return profileCache.get(userId);
    }

    public FamilyProfileDTO queryOrDefault(Long userId) {
        FamilyProfileDTO cached = query(userId);
        return cached != null ? cached : defaultProfile(userId);
    }

    private FamilyProfileDTO defaultProfile(Long userId) {
        FamilyProfileDTO profile = createDefaultProfile();
        profile.setUserId(userId != null ? userId : 1001L);
        return profile;
    }

    private FamilyProfileDTO createDefaultProfile() {
        FamilyProfileDTO profile = new FamilyProfileDTO();
        profile.setFamilySize(3);
        profile.setHasElderly(Boolean.TRUE);
        profile.setHasChild(Boolean.TRUE);
        profile.setBudgetRange("50-120");
        profile.setDefaultArea("西湖");
        profile.setDistancePreference("近");
        return profile;
    }

    /**
     * Parse USER.md markdown content back into FamilyProfileDTO.
     * Format example:
     *   - 用户ID：1001
     *   - 家庭人数：3
     *   - 是否有老人：true
     */
    private FamilyProfileDTO parseUserProfile(String markdown) {
        if (markdown == null || markdown.isBlank()) return null;

        FamilyProfileDTO profile = new FamilyProfileDTO();

        profile.setUserId(extractLong(markdown, "用户ID"));
        Integer familySize = extractInt(markdown, "家庭人数");
        profile.setFamilySize(familySize != null ? familySize : 3);
        profile.setHasElderly(extractBoolean(markdown, "是否有老人"));
        profile.setHasChild(extractBoolean(markdown, "是否有儿童"));
        profile.setBudgetRange(extractString(markdown, "预算范围"));
        profile.setDefaultArea(extractString(markdown, "默认区域"));
        profile.setDistancePreference(extractString(markdown, "距离偏好"));

        return profile.getUserId() != null ? profile : null;
    }

    private Long extractLong(String text, String field) {
        Pattern p = Pattern.compile("-\\s*" + Pattern.quote(field) + "[:：]\\s*(\\d+)");
        Matcher m = p.matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private Integer extractInt(String text, String field) {
        Pattern p = Pattern.compile("-\\s*" + Pattern.quote(field) + "[:：]\\s*(\\d+)");
        Matcher m = p.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private Boolean extractBoolean(String text, String field) {
        Pattern p = Pattern.compile("-\\s*" + Pattern.quote(field) + "[:：]\\s*(true|false|TRUE|FALSE|是|否)");
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        String val = m.group(1);
        return "true".equalsIgnoreCase(val) || "是".equals(val);
    }

    private String extractString(String text, String field) {
        Pattern p = Pattern.compile("-\\s*" + Pattern.quote(field) + "[:：]\\s*(.+)");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}
