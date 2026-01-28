package com.EduePoa.EP.Analytics;

import com.EduePoa.EP.Analytics.Response.AnalyticsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/analytics/")
@Slf4j
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("summary")
    public ResponseEntity<AnalyticsResponse.AnalyticsSummary> getSummary(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(analyticsService.getSummary(period));
    }

    @GetMapping("fee-defaulters")
    public ResponseEntity<AnalyticsResponse.FeeDefaultersResponse> getFeeDefaulters(
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @RequestParam(required = false) Double minBalance) {
        return ResponseEntity.ok(analyticsService.getFeeDefaulters(limit, minBalance));
    }

    @GetMapping("revenue")
    public ResponseEntity<AnalyticsResponse.RevenueTrendsResponse> getRevenueTrends(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false, defaultValue = "month") String groupBy) {
        return ResponseEntity.ok(analyticsService.getRevenueTrends(startDate, endDate, groupBy));
    }

    @GetMapping("enrollment")
    public ResponseEntity<AnalyticsResponse.EnrollmentTrendsResponse> getEnrollmentTrends(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ResponseEntity.ok(analyticsService.getEnrollmentTrends(startDate, endDate));
    }

    @GetMapping("payment-methods")
    public ResponseEntity<AnalyticsResponse.PaymentMethodsResponse> getPaymentMethods(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(analyticsService.getPaymentMethods(startDate, endDate));
    }

    @GetMapping("transport")
    public ResponseEntity<AnalyticsResponse.TransportAnalyticsResponse> getTransportAnalytics() {
        return ResponseEntity.ok(analyticsService.getTransportAnalytics());
    }
}
