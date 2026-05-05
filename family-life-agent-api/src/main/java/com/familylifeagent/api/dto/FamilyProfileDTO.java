package com.familylifeagent.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyProfileDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Min(value = 1, message = "家庭人数最少为1")
    private Integer familySize;

    private Boolean hasElderly;

    private Boolean hasChild;

    @Size(max = 50, message = "预算范围不能超过50个字符")
    private String budgetRange;

    @Size(max = 30, message = "区域名称不能超过30个字符")
    private String defaultArea;

    @Size(max = 20, message = "距离偏好不能超过20个字符")
    private String distancePreference;
}
