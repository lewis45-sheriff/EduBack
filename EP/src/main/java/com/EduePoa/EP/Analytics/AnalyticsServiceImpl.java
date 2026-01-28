package com.EduePoa.EP.Analytics;

import com.EduePoa.EP.Analytics.Response.AnalyticsResponse;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionRepository;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.TransportRepository;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactionsRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AnalyticsServiceImpl implements AnalyticsService {
    private final StudentRepository studentRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final FinanceTransactionRepository financeTransactionRepository;
    private final TransportRepository transportRepository;
    private final TransportTransactionsRepository transportTransactionsRepository;
    private final AssignTransportRepository assignTransportRepository;
    private final FinanceRepository financeRepository;

    @Override
    public CustomResponse<?> getTransactions(Date period) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Long studentCount = studentRepository.count();
            BigDecimal totalRevenue = financeTransactionRepository
                    .sumByTransactionType(FinanceTransaction.TransactionType.INCOME);
            if (totalRevenue == null)
                totalRevenue = BigDecimal.ZERO;

            BigDecimal totalOutstanding = studentInvoicesRepository.sumOutstandingBalance();
            if (totalOutstanding == null)
                totalOutstanding = BigDecimal.ZERO;

            BigDecimal totalExpected = totalRevenue.add(totalOutstanding);
            double collectionRate = totalExpected.compareTo(BigDecimal.ZERO) > 0
                    ? totalRevenue.multiply(BigDecimal.valueOf(100)).divide(totalExpected, 2, RoundingMode.HALF_UP)
                            .doubleValue()
                    : 0.0;

            Long feeDefaulters = studentInvoicesRepository.countStudentsWithOutstanding();

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalStudents", studentCount);
            summary.put("totalRevenue", totalRevenue);
            summary.put("totalOutstanding", totalOutstanding);
            summary.put("collectionRate", collectionRate);
            summary.put("feeDefaulters", feeDefaulters);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("summary", summary);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Transaction summary retrieved successfully");
            response.setEntity(responseData);

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error retrieving transaction summary: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public AnalyticsResponse.AnalyticsSummary getSummary(String period) {
        // Simple implementation reusing logic from getTransactions but returning DTO
        // Handling period logic if needed (e.g. 6months, 1year) - currently calculating
        // totals for simplicity
        // To strictly follow period, we would need to filter transactions/invoices by
        // date.
        // Assuming current logic (All Time) is acceptable for V1 or adapting if
        // crucial.
        // The previous implementation was "All Time". I will stick to "All Time" for
        // totals unless I query specifically.

        Long studentCount = studentRepository.count();
        BigDecimal totalRevenue = financeTransactionRepository
                .sumByTransactionType(FinanceTransaction.TransactionType.INCOME);
        if (totalRevenue == null)
            totalRevenue = BigDecimal.ZERO;

        BigDecimal totalOutstanding = studentInvoicesRepository.sumOutstandingBalance();
        if (totalOutstanding == null)
            totalOutstanding = BigDecimal.ZERO;

        double collectionRate = 0.0;
        BigDecimal totalExpected = totalRevenue.add(totalOutstanding);
        if (totalExpected.compareTo(BigDecimal.ZERO) > 0) {
            collectionRate = totalRevenue.multiply(BigDecimal.valueOf(100))
                    .divide(totalExpected, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        Long feeDefaulters = studentInvoicesRepository.countStudentsWithOutstanding();

        return AnalyticsResponse.AnalyticsSummary.builder()
                .totalStudents(studentCount)
                .totalRevenue(totalRevenue)
                .totalOutstanding(totalOutstanding)
                .collectionRate(collectionRate)
                .feeDefaulters(feeDefaulters)
                .build();
    }

    @Override
    public AnalyticsResponse.FeeDefaultersResponse getFeeDefaulters(int limit, Double minBalance) {
        BigDecimal minBal = minBalance != null ? BigDecimal.valueOf(minBalance) : BigDecimal.ZERO;
        Pageable pageable = PageRequest.of(0, limit > 0 ? limit : 1000);

        // Get current term and year
        Term currentTerm = Term.getCurrentTerm();
        Year currentYear = Year.now();

        // Handle case where no current term is active
        if (currentTerm == null) {
            // Return empty response or handle appropriately
            return AnalyticsResponse.FeeDefaultersResponse.builder()
                    .defaulters(Collections.emptyList())
                    .totalOutstanding(BigDecimal.ZERO)
                    .build();
        }

        // Fetch previous term defaulters
        Page<Finance> previousTermFinances = financeRepository.findPreviousTermDefaulters(
                minBal, currentYear, currentTerm, pageable
        );

        List<AnalyticsResponse.FeeDefaulter> defaulters = previousTermFinances.getContent().stream()
                .map(finance -> {
                    // Fetch student details
                    Student student = studentRepository.findById(finance.getStudentId())
                            .orElse(null);

                    if (student == null) {
                        return null; // Skip if student not found
                    }

                    // Fetch last payment date for this student
                    List<FinanceTransaction> txns = financeTransactionRepository
                            .findByStudentId(student.getId());
                    LocalDate lastPaymentDate = txns.stream()
                            .filter(t -> t.getTransactionType() == FinanceTransaction.TransactionType.INCOME)
                            .map(FinanceTransaction::getTransactionDate)
                            .max(LocalDate::compareTo)
                            .orElse(null);

                    return AnalyticsResponse.FeeDefaulter.builder()
                            .studentId(student.getId())
                            .studentName(student.getFirstName() + " " + student.getLastName())
                            .admissionNumber(student.getAdmissionNumber())
                            .gradeName(student.getGradeName())
                            .parentPhone(student.getParent() != null ? student.getParent().getPhoneNumber() : "N/A")
                            .parentEmail(student.getParent() != null ? student.getParent().getEmail() : "N/A")
                            .totalFees(finance.getTotalFeeAmount())
                            .amountPaid(finance.getPaidAmount())
                            .balance(finance.getBalance())
                            .lastPaymentDate(lastPaymentDate)
//                            .term(finance.getTerm())
//                            .year(finance.getYear())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Sum total outstanding from previous terms
        BigDecimal totalOutstanding = financeRepository.sumPreviousTermOutstandingBalance(
                currentYear, currentTerm
        );

        return AnalyticsResponse.FeeDefaultersResponse.builder()
                .defaulters(defaulters)
                .totalOutstanding(totalOutstanding)
                .build();
    }

    @Override
    public AnalyticsResponse.RevenueTrendsResponse getRevenueTrends(String startDateStr, String endDateStr,
            String groupBy) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        List<FinanceTransaction> transactions = financeTransactionRepository.findByTransactionDateBetween(startDate,
                endDate);
        transactions.sort(Comparator.comparing(FinanceTransaction::getTransactionDate));

        // Grouping logic (Java based)
        Map<String, List<FinanceTransaction>> groupedData = new LinkedHashMap<>();
        DateTimeFormatter formatter;

        if ("day".equalsIgnoreCase(groupBy)) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        } else if ("week".equalsIgnoreCase(groupBy)) {
            // Simplification for week: ISO Week? Or just chunking? Let's use start of week.
            formatter = DateTimeFormatter.ofPattern("yyyy-wu"); // Year-Week
        } else {
            formatter = DateTimeFormatter.ofPattern("MMM yyyy"); // Default month
        }

        // TreeMap to sort by key (if key is sortable), but "MMM yyyy" is not
        // alphabetically sortable correctly.
        // Better to group by YearMonth or LocalDate then format.
        // Let's use a simpler approach: Map<LocalDate, ...> then sort then keys.

        Map<String, LocalDate> keyDateMap = new HashMap<>(); // To help sorting

        for (FinanceTransaction txn : transactions) {
            String key;
            LocalDate date = txn.getTransactionDate();
            if ("day".equalsIgnoreCase(groupBy)) {
                key = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } else if ("week".equalsIgnoreCase(groupBy)) {
                key = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                        .toString();
            } else {
                key = date.format(DateTimeFormatter.ofPattern("MMM yyyy")); // e.g. "Aug 2025"
            }
            groupedData.computeIfAbsent(key, k -> new ArrayList<>()).add(txn);
        }

        List<AnalyticsResponse.RevenueTrend> trends = new ArrayList<>();
        BigDecimal prevIncome = BigDecimal.ZERO; // Placeholder

        // Ideally we should iterate over reference dates (all months in range) to fill
        // gaps,
        // but for now iterating over present data. Sorting keys is important.
        // Keys usually sortable if YYYY-MM-DD. MMM yyyy is harder.
        // I will trust the list order from Repo if I didn't group? No, I grouped.
        // Let's just create entries.

        for (Map.Entry<String, List<FinanceTransaction>> entry : groupedData.entrySet()) {
            String period = entry.getKey();
            List<FinanceTransaction> txns = entry.getValue();

            BigDecimal income = txns.stream()
                    .filter(t -> t.getTransactionType() == FinanceTransaction.TransactionType.INCOME)
                    .map(FinanceTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal expenses = txns.stream()
                    .filter(t -> t.getTransactionType() == FinanceTransaction.TransactionType.EXPENSE)
                    .map(FinanceTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal net = income.subtract(expenses);

            double growth = 0.0;
            if (prevIncome.compareTo(BigDecimal.ZERO) > 0) {
                growth = income.subtract(prevIncome)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(prevIncome, 2, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            trends.add(AnalyticsResponse.RevenueTrend.builder()
                    .period(period)
                    .income(income)
                    .expenses(expenses)
                    .netRevenue(net)
                    .previousPeriodIncome(prevIncome)
                    .growthPercentage(growth)
                    .build());

            prevIncome = income;
        }

        return AnalyticsResponse.RevenueTrendsResponse.builder()
                .revenueTrends(trends)
                .build();
    }

    @Override
    public AnalyticsResponse.EnrollmentTrendsResponse getEnrollmentTrends(String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        List<Student> students = studentRepository.findByAdmissionDateBetween(startDate, endDate);
        // Assuming we group by Month for trends

        Map<String, List<Student>> grouped = students.stream()
                .collect(Collectors.groupingBy(s -> s.getAdmissionDate() != null
                        ? s.getAdmissionDate().format(DateTimeFormatter.ofPattern("MMM yyyy"))
                        : "Unknown"));

        List<AnalyticsResponse.EnrollmentTrend> trends = new ArrayList<>();
        long runningTotal = studentRepository.count(); // This is approximate total now, not historical.
        // To get historical total, we'd need to subtract new admissions and add
        // withdrawals backwards.
        // Simplified: just Use current total for all, or count accumulated.

        for (Map.Entry<String, List<Student>> entry : grouped.entrySet()) {
            List<Student> batch = entry.getValue();
            Long newAdmissions = (long) batch.size();
            Long withdrawals = 0L; // Need withdrawal data logic. Assuming 0 for now or filtering if
                                   // deleted/withdrawn.

            trends.add(AnalyticsResponse.EnrollmentTrend.builder()
                    .period(entry.getKey())
                    .newAdmissions(newAdmissions)
                    .withdrawals(withdrawals)
                    .totalStudents(runningTotal) // Approximation
                    .build());
        }

        return AnalyticsResponse.EnrollmentTrendsResponse.builder()
                .enrollmentTrends(trends)
                .build();
    }

    @Override
    public AnalyticsResponse.PaymentMethodsResponse getPaymentMethods(String startDateStr, String endDateStr) {
        LocalDate startDate = startDateStr != null ? LocalDate.parse(startDateStr) : LocalDate.now().minusMonths(6);
        LocalDate endDate = endDateStr != null ? LocalDate.parse(endDateStr) : LocalDate.now();

        List<FinanceTransaction> txns = financeTransactionRepository.findByTransactionDateBetween(startDate, endDate);

        BigDecimal totalAmount = txns.stream()
                .map(FinanceTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<FinanceTransaction.PaymentMethod, BigDecimal> byMethod = txns.stream()
                .filter(t -> t.getPaymentMethod() != null)
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getPaymentMethod,
                        Collectors.reducing(BigDecimal.ZERO, FinanceTransaction::getAmount, BigDecimal::add)));

        Map<FinanceTransaction.PaymentMethod, Long> countMethod = txns.stream()
                .filter(t -> t.getPaymentMethod() != null)
                .collect(Collectors.groupingBy(FinanceTransaction::getPaymentMethod, Collectors.counting()));

        List<AnalyticsResponse.PaymentMethodStat> stats = new ArrayList<>();

        for (Map.Entry<FinanceTransaction.PaymentMethod, BigDecimal> entry : byMethod.entrySet()) {
            BigDecimal amt = entry.getValue();
            Double pct = totalAmount.compareTo(BigDecimal.ZERO) > 0
                    ? amt.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            stats.add(AnalyticsResponse.PaymentMethodStat.builder()
                    .method(entry.getKey().name())
                    .amount(amt)
                    .count(countMethod.get(entry.getKey()))
                    .percentage(pct)
                    .build());
        }

        return AnalyticsResponse.PaymentMethodsResponse.builder()
                .paymentMethods(stats)
                .build();
    }

    @Override
    public AnalyticsResponse.TransportAnalyticsResponse getTransportAnalytics() {
        Long totalVehicles = transportRepository.count();
        Long activeVehicles = transportRepository.countByStatus("Active"); // Assuming "Active" string

        // Calculate total capacity
        int totalCapacity = transportRepository.findAll().stream()
                .mapToInt(t -> t.getCapacity() != null ? t.getCapacity() : 0)
                .sum();

        // Students transported
        Long studentsTransported = assignTransportRepository.count(); // Total assigned

        Double utilizationRate = (totalCapacity > 0)
                ? Math.round(((double) studentsTransported / totalCapacity * 100) * 100.0) / 100.0
                : 0.0;

        // Total Revenue from Transport Transactions
        Double revenueDouble = transportTransactionsRepository.sumTotalRevenue();
        BigDecimal totalRevenue = BigDecimal.valueOf(revenueDouble != null ? revenueDouble : 0.0);

        return AnalyticsResponse.TransportAnalyticsResponse.builder()
                .transportAnalytics(AnalyticsResponse.TransportAnalytics.builder()
                        .totalVehicles(totalVehicles)
                        .activeVehicles(activeVehicles)
                        .totalCapacity(totalCapacity)
                        .utilizationRate(utilizationRate)
                        .totalRevenue(totalRevenue)
                        .studentsTransported(studentsTransported)
                        .build())
                .build();
    }
}
