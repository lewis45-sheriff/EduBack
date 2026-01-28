package com.EduePoa.EP.Analytics;

import com.EduePoa.EP.Utils.CustomResponse;
import org.springframework.stereotype.Service;

import java.util.Date;

public interface AnalyticsService {
    com.EduePoa.EP.Utils.CustomResponse<?> getTransactions(Date period);

    com.EduePoa.EP.Analytics.Response.AnalyticsResponse.AnalyticsSummary getSummary(String period);

    com.EduePoa.EP.Analytics.Response.AnalyticsResponse.FeeDefaultersResponse getFeeDefaulters(int limit,
            Double minBalance);

    com.EduePoa.EP.Analytics.Response.AnalyticsResponse.RevenueTrendsResponse getRevenueTrends(String startDate,
            String endDate, String groupBy);

    com.EduePoa.EP.Analytics.Response.AnalyticsResponse.EnrollmentTrendsResponse getEnrollmentTrends(String startDate,
            String endDate);

    com.EduePoa.EP.Analytics.Response.AnalyticsResponse.PaymentMethodsResponse getPaymentMethods(String startDate,
            String endDate);

    com.EduePoa.EP.Analytics.Response.AnalyticsResponse.TransportAnalyticsResponse getTransportAnalytics();

}
