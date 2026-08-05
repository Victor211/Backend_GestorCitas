package com.victor.appointmentmanager.api.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private long todayAppointments;
    private long activeCustomers;
    private long activeEmployees;
    private long activeServices;
    private List<UpcomingAppointmentResponse> upcomingAppointments;

}
