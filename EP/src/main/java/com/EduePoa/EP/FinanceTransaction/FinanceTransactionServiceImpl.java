package com.EduePoa.EP.FinanceTransaction;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesService;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceTransactionServiceImpl implements FinanceTransactionService {
    private final StudentRepository studentRepository;
    private final FinanceRepository financeRepository;
    private final FinanceTransactionRepository financeTransactionRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;

    @Override
    @Transactional
    public CustomResponse<?> createTransaction(Long studentId, CreateTransactionDTO createTransactionDTO) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            // Get current term
            Term currentTerm = Term.getCurrentTerm();
            if (currentTerm == null) {
                throw new RuntimeException("No active term found for current date");
            }

            // Validate that the transaction is for the current term
            if (createTransactionDTO.getTerm() != currentTerm) {
                throw new RuntimeException("Transactions can only be created for the current term ("
                        + currentTerm.name() + "). Requested term: " + createTransactionDTO.getTerm().name());
            }

            // Validate current year
           Year currentYear = Year.now();
            if (createTransactionDTO.getYear() != currentYear) {
                throw new RuntimeException("Transactions can only be created for the current year ("
                        + currentYear + "). Requested year: " + createTransactionDTO.getYear());
            }

            // Validate student exists
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Validate and get the specific invoice
            StudentInvoices invoice = studentInvoicesRepository.findById(createTransactionDTO.getInvoiceId())
                    .orElseThrow(() -> new RuntimeException("Invoice not found with ID: " + createTransactionDTO.getInvoiceId()));

            // Verify the invoice belongs to this student
            if (!invoice.getStudent().getId().equals(studentId)) {
                throw new RuntimeException("Invoice does not belong to this student");
            }

            // Verify invoice is for the current term and year
            if (invoice.getTerm() != currentTerm || invoice.getAcademicYear() != currentYear) {
                throw new RuntimeException("Invoice is not for the current term and year. Cannot process transaction.");
            }

            // Verify invoice is not already cleared
            if (invoice.getStatus() == 'C') {
                throw new RuntimeException("Invoice is already cleared");
            }

            // Get Finance record for student (should be for current term)
            Finance finance = financeRepository.findByStudentIdAndTermAndYear(
                            studentId,
                            currentTerm,
                            currentYear)
                    .orElseThrow(() -> new RuntimeException(
                            "No finance record found for student in current term. Please create an invoice first."
                    ));

            // Create the transaction with invoice reference
            FinanceTransaction transaction = getFinanceTransaction(studentId, createTransactionDTO, student);
            transaction.setInvoiceId(invoice.getId());
//            transaction.set(currentTerm); // Ensure term is set to current
            transaction.setYear(currentYear); // Ensure year is current

            // Save transaction first
            FinanceTransaction savedTransaction = financeTransactionRepository.save(transaction);

            // Update Invoice based on transaction type
            if (createTransactionDTO.getTransactionType() == FinanceTransaction.TransactionType.INCOME) {
                // Payment received - update invoice
                invoice.setAmountPaid(invoice.getAmountPaid().add(createTransactionDTO.getAmount()));
                invoice.setBalance(invoice.getTotalAmount().subtract(invoice.getAmountPaid()));

                // Update invoice status
                if (invoice.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                    invoice.setStatus('C'); // Cleared
                } else if (invoice.getBalance().compareTo(invoice.getTotalAmount()) < 0) {
                    invoice.setStatus('P'); // Partially paid (still pending)
                }

                // Update Finance record
                finance.setPaidAmount(finance.getPaidAmount().add(createTransactionDTO.getAmount()));
                finance.setBalance(finance.getTotalFeeAmount().subtract(finance.getPaidAmount()));

            } else if (createTransactionDTO.getTransactionType() == FinanceTransaction.TransactionType.EXPENSE) {
                // Refund or adjustment - update invoice
                invoice.setAmountPaid(invoice.getAmountPaid().subtract(createTransactionDTO.getAmount()));
                invoice.setBalance(invoice.getTotalAmount().subtract(invoice.getAmountPaid()));

                // Update invoice status
                if (invoice.getBalance().compareTo(invoice.getTotalAmount()) == 0) {
                    invoice.setStatus('P'); // Back to pending
                }

                // Update Finance record
                finance.setPaidAmount(finance.getPaidAmount().subtract(createTransactionDTO.getAmount()));
                finance.setBalance(finance.getTotalFeeAmount().subtract(finance.getPaidAmount()));
            }

            // Save updated invoice
            StudentInvoices updatedInvoice = studentInvoicesRepository.save(invoice);

            // Save updated finance record
            finance.setLastUpdated(LocalDateTime.now());
            Finance updatedFinance = financeRepository.save(finance);

            // Prepare response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transaction", savedTransaction);
            responseData.put("invoice", updatedInvoice);
            responseData.put("finance", updatedFinance);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Transaction created and invoice updated successfully");
            response.setEntity(responseData); // Changed from null to responseData

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
    @Override
    public CustomResponse<?> getById(Long id) {
        CustomResponse<FinanceTransaction> response = new CustomResponse<>();
        try {
            Optional<FinanceTransaction> optionalFinanceTransaction =
                    financeTransactionRepository.findById(id);

            if (optionalFinanceTransaction.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                response.setMessage("Finance Transaction not found with ID: " + id);
                return response;
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(optionalFinanceTransaction.get());
            response.setMessage("Finance Transaction retrieved successfully");

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Error retrieving transaction: " + e.getMessage());
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
        return transaction;
    }
}
