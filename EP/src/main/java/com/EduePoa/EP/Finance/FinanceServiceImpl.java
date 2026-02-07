package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Finance.Responses.StudentBalanceDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {
    private final FinanceRepository financeRepository;
    private final StudentRepository studentRepository;
    private final AuditService auditService;

    @Override
    public CustomResponse<?> getStudentsWithBalances() {
        CustomResponse<List<StudentBalanceDTO>> response = new CustomResponse<>();
        try {
            // Fetch all finances, including those with 0 balance
            List<Finance> finances = financeRepository.findAll();

            List<StudentBalanceDTO> studentBalances = finances.stream()
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

    @Override
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
