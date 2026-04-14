package com.familylifeagent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyProfileDTO {

    private Long userId;

    private Integer familySize;

    private Boolean hasElderly;

    private Boolean hasChild;

    private String elderlyPreference;

    private String childPreference;

    private String budgetRange;

    private String defaultArea;

    private String distancePreference;
}
