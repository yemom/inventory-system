package com.inventory.backend.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StaffStatsDTO {
    private long totalStaff;
    private long activeStaff;
    private long inactiveStaff;
    private long managers;
    private long supervisors;
    private long cashiers;
    private long inventoryStaff;
}
