package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Finance.Responses.StudentBalanceDTO;
import com.EduePoa.EP.StudentInvoices.InvoiceReversal;
import com.EduePoa.EP.StudentInvoices.InvoiceReversalRepository;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {
    private final FinanceRepository financeRepository;
    private final StudentRepository studentRepository;
    private final AuditService auditService;
    private final InvoiceReversalRepository invoiceReversalRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;

    @Override
    @Audit(module = "FINANCE", action = "GET_BALANCES")
    public CustomResponse<?> getStudentsWithBalances() {
        CustomResponse<List<StudentBalanceDTO>> response = new CustomResponse<>();
        try {
            List<Finance> finances = financeRepository.findAll();

            // Build the set of (student, term, year) keys whose invoice has been
            // reversed and NOT re-invoiced. Reversal hard-deletes the invoice (freeing
            // the term to be re-invoiced) and records an InvoiceReversal snapshot, so a
            // student is treated as "reversed" only when a reversal record exists and no
            // live (isDeleted = 'N') invoice currently exists for that same term/year.
            // This is independent of the balance amount.
            Set<String> reversedKeys = invoiceReversalRepository.findAll().stream()
                    .map(r -> financeKey(r.getStudentId(), r.getTerm(), r.getAcademicYear()))
                    .collect(Collectors.toCollection(HashSet::new));

            if (!reversedKeys.isEmpty()) {
                // Any term that has since been re-invoiced has a live invoice again, so
                // remove those keys - the student should reappear in the listing.
                studentInvoicesRepository.findByIsDeleted('N').stream()
                        .filter(inv -> inv.getStudent() != null)
                        .map(inv -> financeKey(inv.getStudent().getId(), inv.getTerm(), inv.getAcademicYear()))
                        .forEach(reversedKeys::remove);
            }

            List<StudentBalanceDTO> studentBalances = finances.stream()
                    // Hide only students whose invoice for this term/year was reversed
                    // (and not re-invoiced), regardless of their balance.
                    .filter(finance -> !reversedKeys.contains(
                            financeKey(finance.getStudentId(), finance.getTerm(), finance.getYear())))
                    .map(finance -> {
                        // Fetch student details
                        Student student = studentRepository.findById(finance.getStudentId())
                                .orElseThrow(() -> new RuntimeException("Student not found"));

                        String status;
                        BigDecimal balance = finance.getBalance();

                        if (balance.compareTo(BigDecimal.ZERO) > 0) {
                            status = "OUTSTANDING";
                        } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                            status = "OVERPAID";
                        } else {
                            status = "CLEARED";
                        }

                        return StudentBalanceDTO.builder()
                                .studentId(finance.getStudentId())
                                .studentName(student.getFirstName() + " " + student.getLastName())
                                .gradeName(student.getGradeName()) // Add grade name
                                .totalFeeAmount(finance.getTotalFeeAmount())
                                .paidAmount(finance.getPaidAmount())
                                .balance(balance)
                                .balanceStatus(status)
                                .term(finance.getTerm())
                                .year(finance.getYear())
                                .build();
                    })
                    .collect(Collectors.toList());

            response.setEntity(studentBalances);
            response.setMessage("Students with balances retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("FINANCE", "Retrieved balances for", String.valueOf(studentBalances.size()), "students");

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    /**
     * Builds a stable lookup key identifying a student's fee position for a given
     * term and year, used to match Finance rows against reversed / re-invoiced
     * invoices. Null-safe so partially populated records never throw.
     */
    private String financeKey(Long studentId, Term term, Year year) {
        return studentId + "|" + (term != null ? term.name() : "null")
                + "|" + (year != null ? year.toString() : "null");
    }

    @Override
    @Audit(module = "FINANCE", action = "GET_BALANCE_PER_STUDENT")
    public CustomResponse<?> getStudentsWithBalancePerStudent(Long studentId) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Term currentTerm = Term.getCurrentTerm();
            if (currentTerm == null) {
                response.setMessage("No active term found.");
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                return response;
            }

            Year currentYear = Year.now();
            Optional<Finance> finances;

            if (studentId != null) {
                finances = financeRepository.findByStudentIdAndTermAndYear(studentId, currentTerm, currentYear);
            } else {
                finances = financeRepository.findByTermAndYear(currentTerm, currentYear);
            }

            // Map results to a DTO or a simple structure
            List<Map<String, Object>> balances = finances.stream().map(f -> {
                Map<String, Object> map = new HashMap<>();
                map.put("studentId", f.getStudentId());
                map.put("totalFee", f.getTotalFeeAmount());
                map.put("paid", f.getPaidAmount());
                map.put("balance", f.getBalance());
                map.put("term", f.getTerm());
                map.put("year", f.getYear());
                return map;
            }).toList();

            response.setEntity(balances);
            response.setMessage("Balances retrieved successfully.");
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("FINANCE", "Retrieved balance for student ID:", String.valueOf(studentId));
        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

}
