package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FamilyProfileService {

    private final Map<Long, FamilyProfileDTO> profileCache = new ConcurrentHashMap<>();

    public FamilyProfileService() {
        FamilyProfileDTO mockProfile = new FamilyProfileDTO();
        mockProfile.setUserId(1001L);
        mockProfile.setFamilySize(3);
        mockProfile.setHasElderly(Boolean.TRUE);
        mockProfile.setHasChild(Boolean.TRUE);
        mockProfile.setElderlyPreference("老人清淡");
        mockProfile.setChildPreference("小孩面食");
        mockProfile.setBudgetRange("100以内");
        mockProfile.setDefaultArea("西湖");
        mockProfile.setDistancePreference("近");
        profileCache.put(mockProfile.getUserId(), mockProfile);
    }

    public FamilyProfileDTO save(FamilyProfileDTO profileDTO) {
        if (profileDTO == null || profileDTO.getUserId() == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        profileCache.put(profileDTO.getUserId(), profileDTO);
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
        FamilyProfileDTO profile = new FamilyProfileDTO();
        profile.setUserId(userId != null ? userId : 1001L);
        profile.setFamilySize(3);
        profile.setHasElderly(Boolean.TRUE);
        profile.setHasChild(Boolean.TRUE);
        profile.setElderlyPreference("清淡");
        profile.setChildPreference("面食");
        profile.setBudgetRange("50-120");
        profile.setDefaultArea("西湖");
        profile.setDistancePreference("近");
        return profile;
    }
}
