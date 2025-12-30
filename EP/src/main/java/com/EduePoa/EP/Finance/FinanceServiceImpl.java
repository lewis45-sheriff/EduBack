package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Finance.Responses.StudentBalanceDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService{
    private final FinanceRepository financeRepository;
    private final StudentRepository studentRepository;
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
                                .studentName(student.getFirstName() + " " + student.getLastName()) // or student.getFullName()
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

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

}
