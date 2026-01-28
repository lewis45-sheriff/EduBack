package com.EduePoa.EP.Analytics.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AnalyticsResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnalyticsSummary {
        private Long totalStudents;
        private BigDecimal totalRevenue;
        private BigDecimal totalOutstanding;
        private Double collectionRate;
        private Long feeDefaulters;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeDefaulter {
        private Long studentId;
        private String studentName;
        private String admissionNumber;
        private String gradeName;
        private String parentPhone;
        private String parentEmail;
        private BigDecimal totalFees;
        private BigDecimal amountPaid;
        private BigDecimal balance;
        private LocalDate lastPaymentDate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeeDefaultersResponse {
        private List<FeeDefaulter> defaulters;
        private BigDecimal totalOutstanding;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RevenueTrend {
        private String period;
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal netRevenue;
        private BigDecimal previousPeriodIncome;
        private Double growthPercentage;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RevenueTrendsResponse {
        private List<RevenueTrend> revenueTrends;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EnrollmentTrend {
        private String period;
        private Long newAdmissions;
        private Long withdrawals;
        private Long totalStudents;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EnrollmentTrendsResponse {
        private List<EnrollmentTrend> enrollmentTrends;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentMethodStat {
        private String method;
        private Long count;
        private BigDecimal amount;
        private Double percentage;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentMethodsResponse {
        private List<PaymentMethodStat> paymentMethods;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TransportAnalytics {
        private Long totalVehicles;
        private Long activeVehicles;
        private Integer totalCapacity;
        private Double utilizationRate;
        private BigDecimal totalRevenue;
        private Long studentsTransported;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TransportAnalyticsResponse {
        private TransportAnalytics transportAnalytics;
    }
}
