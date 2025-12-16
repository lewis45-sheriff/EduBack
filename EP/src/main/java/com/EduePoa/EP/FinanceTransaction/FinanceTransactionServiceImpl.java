package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceTransactionServiceImpl implements FinanceTransactionService {
    private final StudentRepository studentRepository;
    private final FinanceRepository financeRepository;
    private final FinanceTransactionRepository financeTransactionRepository;

    @Override
    public CustomResponse<?> createTransaction(Long studentId, CreateTransactionDTO createTransactionDTO) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            // Validate student exists
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Get Finance record for student - it must exist (created when invoice is generated)
            Finance finance = financeRepository.findByStudentId(studentId)
                    .orElseThrow(() -> new RuntimeException(
                            "No finance record found for student. Please create an invoice first."
                    ));

            // Create the transaction
            FinanceTransaction transaction = getFinanceTransaction(studentId, createTransactionDTO, student);

            // Save transaction first
            FinanceTransaction savedTransaction = financeTransactionRepository.save(transaction);

            // Update Finance record based on transaction type
            if (createTransactionDTO.getTransactionType() == FinanceTransaction.TransactionType.INCOME) {
                // For INCOME transactions (payments received)
                finance.setPaidAmount(finance.getPaidAmount().add(createTransactionDTO.getAmount()));
                finance.setBalance(finance.getTotalFeeAmount().subtract(finance.getPaidAmount()));
            } else if (createTransactionDTO.getTransactionType() == FinanceTransaction.TransactionType.EXPENSE) {
                // For EXPENSE transactions (refunds or adjustments)
                finance.setPaidAmount(finance.getPaidAmount().subtract(createTransactionDTO.getAmount()));
                finance.setBalance(finance.getTotalFeeAmount().subtract(finance.getPaidAmount()));
            }

            finance.setLastUpdated(LocalDateTime.now());
            Finance updatedFinance = financeRepository.save(finance);

            // Prepare response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transaction", savedTransaction);
            responseData.put("finance", updatedFinance);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Transaction created successfully");
            response.setEntity(responseData);

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }
    @Override
    public CustomResponse<?> getTransactions() {
        CustomResponse<List<FinanceTransaction>> response = new CustomResponse<>();
        try {
            List<FinanceTransaction> transactions = financeTransactionRepository.findAllByOrderByTransactionDateDesc();

            if (transactions.isEmpty()) {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("No transactions found");
                response.setEntity(new ArrayList<>());
            } else {
                response.setStatusCode(HttpStatus.OK.value());
                response.setMessage("Transactions retrieved successfully");
                response.setEntity(transactions);
            }

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getByStudentId(Long studentId) {
        CustomResponse<List<FinanceTransaction>> response = new CustomResponse<>();
        try {
            List<FinanceTransaction> transactions = financeTransactionRepository.findByStudentId(studentId);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Transactions retrieved successfully");
            response.setEntity(transactions);

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }


    private static FinanceTransaction getFinanceTransaction(Long studentId, CreateTransactionDTO createTransactionDTO, Student student) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setStudentId(studentId);
        transaction.setStudentName(student.getFirstName());
        transaction.setAdmissionNumber(student.getAdmissionNumber());
        transaction.setTransactionType(createTransactionDTO.getTransactionType());
        transaction.setCategory(createTransactionDTO.getCategory());
        transaction.setAmount(createTransactionDTO.getAmount());
        transaction.setTransactionDate(createTransactionDTO.getTransactionDate());
        transaction.setDescription(createTransactionDTO.getDescription());
        transaction.setPaymentMethod(createTransactionDTO.getPaymentMethod());
        transaction.setReference(createTransactionDTO.getReference());
        return transaction;
    }
}
