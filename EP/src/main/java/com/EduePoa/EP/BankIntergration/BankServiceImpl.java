package com.EduePoa.EP.BankIntergration;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.BankIntergration.BankRequest.BankRequestDTO;
import com.EduePoa.EP.BankIntergration.BankResponse.BankResponseDTO;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionService;
import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import com.EduePoa.EP.StudentInvoices.StudentInvoices;
import com.EduePoa.EP.StudentInvoices.StudentInvoicesRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankServiceImpl implements BankService {

    private final BankRepository bankRepository;
    private final StudentRepository studentRepository;
    private final FinanceRepository financeRepository;
    private final FinanceTransactionRepository financeTransactionRepository;
    private final StudentInvoicesRepository studentInvoicesRepository;
    private final FinanceTransactionService financeTransactionService;

    @Override
    @Transactional
    public CustomResponse<?> postTransactions(BankRequestDTO bankRequestDTO) {
        CustomResponse<Bank> response = new CustomResponse<>();

        try {
            // Validate callback structure
//            if (!isValidCallback(bankRequestDTO)) {
//                response.setMessage("Invalid callback structure");
//                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
//                response.setEntity(null);
//                return response;
//            }

            String transactionRef = bankRequestDTO.getTransaction().getReference();

            // Validate transaction reference
            if (transactionRef == null || transactionRef.isEmpty()) {
                response.setMessage("Transaction reference is required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(null);
                return response;
            }



            // Validate transaction status
            if (!"SUCCESS".equalsIgnoreCase(bankRequestDTO.getTransaction().getStatus())) {
                Bank failedBank = saveBankRecord(bankRequestDTO, null);
                response.setMessage("Transaction failed: " + bankRequestDTO.getTransaction().getRemarks());
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(failedBank);
                return response;
            }

            // Extract and validate amount
            BigDecimal amount = bankRequestDTO.getTransaction().getAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                response.setMessage("Invalid transaction amount");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(null);
                return response;
            }

            // Extract student identifier (bill number)
            String studentIdentifier = extractStudentIdentifier(bankRequestDTO);
            if (studentIdentifier == null || studentIdentifier.isEmpty()) {
                Bank unlinkedBank = saveBankRecord(bankRequestDTO, null);
                log.warn("Payment received without student identifier. Transaction: {}", transactionRef);
                response.setMessage("Payment received but no student identifier provided");
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(unlinkedBank);
                return response;
            }

            // Find student by admission number
            Optional<Student> studentOpt = studentRepository.findByAdmissionNumber(studentIdentifier);
            if (studentOpt.isEmpty()) {
                Bank unlinkedBank = saveBankRecord(bankRequestDTO, null);
                log.warn("Student not found for identifier: {}. Transaction: {}", studentIdentifier, transactionRef);
                response.setMessage(String.format(
                        "Payment received but student not found. Contact admin with reference: %s",
                        transactionRef
                ));
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(unlinkedBank);
                return response;
            }

            Student student = studentOpt.get();

            // Get current term and year
            Term currentTerm = Term.getCurrentTerm();
            if (currentTerm == null) {
                Bank unlinkedBank = saveBankRecord(bankRequestDTO, student);
                throw new IllegalStateException("No active term found for current date");
            }

            Year currentYear = Year.now();

            // Find the invoice for this student in current term
            Optional<StudentInvoices> invoiceOpt = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, currentTerm, currentYear);

            if (invoiceOpt.isEmpty()) {
                Bank unlinkedBank = saveBankRecord(bankRequestDTO, student);
                log.warn("No invoice found for student: {} in current term", student.getAdmissionNumber());
                response.setMessage(String.format(
                        "Payment received but no invoice found for current term. Contact admin with reference: %s",
                        transactionRef
                ));
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(unlinkedBank);
                return response;
            }

            StudentInvoices invoice = invoiceOpt.get();

            // Save bank record first
            Bank bank = saveBankRecord(bankRequestDTO, student);
            log.info("Bank record saved successfully for student: {} with transaction: {}",
                    student.getAdmissionNumber(), transactionRef);

            // Create transaction DTO
            CreateTransactionDTO transactionDTO = createTransactionDTO(bankRequestDTO, invoice, currentTerm, currentYear);

            // Call the existing createTransaction method
            CustomResponse<?> transactionResponse = financeTransactionService.createTransaction(
                    student.getId(),
                    transactionDTO
            );

            if (transactionResponse.getStatusCode() != HttpStatus.CREATED.value()) {
                log.error("Failed to create finance transaction: {}", transactionResponse.getMessage());
                response.setMessage("Payment saved but failed to update finance records: " + transactionResponse.getMessage());
                response.setStatusCode(HttpStatus.PARTIAL_CONTENT.value());
                response.setEntity(bank);
                return response;
            }

            log.info("Finance transaction created successfully for student: {}", student.getAdmissionNumber());

            // Get the updated finance balance from the transaction response
            Map<String, Object> transactionData = (Map<String, Object>) transactionResponse.getEntity();
            Finance updatedFinance = (Finance) transactionData.get("finance");

            response.setMessage(String.format(
                    "Payment processed successfully. New balance: KES %.2f",
                    updatedFinance.getBalance()
            ));
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(bank);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
        } catch (Exception e) {
            log.error("Error processing bank callback: {}", e.getMessage(), e);
            response.setMessage("Error processing payment: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }
    private CreateTransactionDTO createTransactionDTO(BankRequestDTO bankRequestDTO,
                                                      StudentInvoices invoice,
                                                      Term currentTerm,
                                                      Year currentYear) {
        CreateTransactionDTO transactionDTO = new CreateTransactionDTO();

        transactionDTO.setTransactionType(FinanceTransaction.TransactionType.INCOME);
        transactionDTO.setAmount(bankRequestDTO.getTransaction().getAmount());
        transactionDTO.setCategory("School Fees Payment");
        transactionDTO.setPaymentMethod(mapPaymentMethod(bankRequestDTO.getTransaction().getPaymentMode()));
        transactionDTO.setReference(bankRequestDTO.getTransaction().getReference());
        transactionDTO.setInvoiceId(invoice.getId());
        transactionDTO.setTerm(currentTerm);
        transactionDTO.setYear(currentYear);
        transactionDTO.setTransactionDate(parseTransactionDate(bankRequestDTO.getTransaction().getDate()));

        String description = String.format(
                "%s payment - Ref: %s from %s",
                bankRequestDTO.getTransaction().getPaymentMode(),
                bankRequestDTO.getTransaction().getReference(),
                bankRequestDTO.getCustomer().getMobileNumber()
        );
        transactionDTO.setDescription(description);

        return transactionDTO;
    }

    @Override
    public CustomResponse<?> getTransactions() {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Transactions retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving transactions: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage("Error retrieving transactions: " + e.getMessage());
        }
        return response;
    }
    @Override
    @Transactional
    public CustomResponse<?> reconcileTransaction(String bankTransactionId, Long studentId) {
        CustomResponse<Object> response = new CustomResponse<>();

        try {
            // Get the bank transaction
            Bank bankTransaction = bankRepository.findById(Long.valueOf(bankTransactionId))
                    .orElseThrow(() -> new RuntimeException("Bank transaction not found with ID: " + bankTransactionId));

            // Verify bank transaction is not already linked
            if (bankTransaction.getStudent() != null) {
                throw new RuntimeException("Bank transaction is already reconciled with student: " +
                        bankTransaction.getStudent().getAdmissionNumber());
            }

            // Get the student
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

            // Get current term and year
            Term currentTerm = Term.getCurrentTerm();
            if (currentTerm == null) {
                throw new RuntimeException("No active term found for current date");
            }

            Year currentYear = Year.now();

            // Find the invoice for this student in current term
            Optional<StudentInvoices> invoiceOpt = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, currentTerm, currentYear);

            if (invoiceOpt.isEmpty()) {
                throw new RuntimeException("No invoice found for student in current term. Please create an invoice first.");
            }

            StudentInvoices invoice = invoiceOpt.get();

            // Create transaction DTO from bank transaction
            CreateTransactionDTO transactionDTO = new CreateTransactionDTO();
            transactionDTO.setTransactionType(FinanceTransaction.TransactionType.INCOME);
            transactionDTO.setAmount(bankTransaction.getAmount());
            transactionDTO.setCategory("School Fees Payment");
            transactionDTO.setPaymentMethod(mapPaymentMethod(bankTransaction.getPaymentMode()));
            transactionDTO.setReference(bankTransaction.getTransactionReference());
            transactionDTO.setInvoiceId(invoice.getId());
            transactionDTO.setTerm(currentTerm);
            transactionDTO.setYear(currentYear);
            transactionDTO.setTransactionDate(parseTransactionDate(bankTransaction.getTransactionDate()));

            String description = String.format(
                    "Reconciled %s payment - Ref: %s from %s",
                    bankTransaction.getPaymentMode(),
                    bankTransaction.getTransactionReference(),
                    bankTransaction.getMobileNumber()
            );
            transactionDTO.setDescription(description);

            // Call the existing createTransaction method from FinanceTransactionService
            CustomResponse<?> transactionResponse = financeTransactionService.createTransaction(studentId, transactionDTO);

            if (transactionResponse.getStatusCode() != HttpStatus.CREATED.value()) {
                throw new RuntimeException("Failed to create transaction: " + transactionResponse.getMessage());
            }

            // Update bank transaction to link with student
            bankTransaction.setStudent(student);
            bankTransaction.setNarrative(String.format(
                    "Reconciled school fee payment for %s - %s %s",
                    student.getAdmissionNumber(),
                    student.getFirstName(),
                    student.getLastName()
            ));
            Bank updatedBankTransaction = bankRepository.save(bankTransaction);

            // Prepare response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("bankTransaction", updatedBankTransaction);
            responseData.put("transactionDetails", transactionResponse.getEntity());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Transaction reconciled successfully");
            response.setEntity(responseData);

        } catch (RuntimeException e) {
            log.error("Error reconciling transaction: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }

        return response;
    }
//
//    @Override
//    public CustomResponse<?> getTransactionsWithPagination(int page, int size) {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//            Page<Bank> transactionsPage = bankRepository.findAll(pageRequest);
//
//            List<BankResponseDTO> dtos = convertToDTOList(transactionsPage.getContent());
//
//            Map<String, Object> result = new HashMap<>();
//            result.put("transactions", dtos);
//            result.put("currentPage", transactionsPage.getNumber());
//            result.put("totalItems", transactionsPage.getTotalElements());
//            result.put("totalPages", transactionsPage.getTotalPages());
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(result);
//            response.setMessage("Transactions retrieved successfully");
//        } catch (Exception e) {
//            log.error("Error retrieving paginated transactions: {}", e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving transactions: " + e.getMessage());
//        }
//        return response;
//    }

//    @Override
//    public CustomResponse<?> getTransactionByReference(String reference) {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            Optional<Bank> transaction = bankRepository.findByTransactionReference(reference);
//            if (transaction.isPresent()) {
//                BankResponseDTO dto = convertToDTO(transaction.get());
//                response.setStatusCode(HttpStatus.OK.value());
//                response.setEntity(dto);
//                response.setMessage("Transaction found");
//            } else {
//                response.setStatusCode(HttpStatus.NOT_FOUND.value());
//                response.setEntity(null);
//                response.setMessage("Transaction not found");
//            }
//        } catch (Exception e) {
//            log.error("Error retrieving transaction by reference: {}", e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving transaction: " + e.getMessage());
//        }
//        return response;
//    }

//    @Override
//    public CustomResponse<?> getTransactionsByStudentId(Long studentId) {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            List<Bank> transactions = bankRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
//            List<BankResponseDTO> dtos = convertToDTOList(transactions);
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(dtos);
//            response.setMessage("Transactions retrieved successfully");
//        } catch (Exception e) {
//            log.error("Error retrieving transactions for student {}: {}", studentId, e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving transactions: " + e.getMessage());
//        }
//        return response;
//    }

//    @Override
//    public CustomResponse<?> getTransactionsByAdmissionNumber(String admissionNumber) {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            List<Bank> transactions = bankRepository.findByBillNumberOrderByCreatedAtDesc(admissionNumber);
//            List<BankResponseDTO> dtos = convertToDTOList(transactions);
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(dtos);
//            response.setMessage("Transactions retrieved successfully");
//        } catch (Exception e) {
//            log.error("Error retrieving transactions for admission {}: {}", admissionNumber, e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving transactions: " + e.getMessage());
//        }
//        return response;
//    }
//
//    @Override
//    public CustomResponse<?> getTransactionsByDateRange(String startDate, String endDate) {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            List<Bank> transactions = bankRepository.findByTransactionDateBetween(startDate, endDate);
//            List<BankResponseDTO> dtos = convertToDTOList(transactions);
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(dtos);
//            response.setMessage("Transactions retrieved successfully");
//        } catch (Exception e) {
//            log.error("Error retrieving transactions by date range: {}", e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving transactions: " + e.getMessage());
//        }
//        return response;
//    }

//    @Override
//    public CustomResponse<?> getUnmatchedTransactions() {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            List<Bank> transactions = bankRepository.findByStudentIsNullOrderByCreatedAtDesc();
//            List<BankResponseDTO> dtos = convertToDTOList(transactions);
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(dtos);
//            response.setMessage("Unmatched transactions retrieved successfully");
//        } catch (Exception e) {
//            log.error("Error retrieving unmatched transactions: {}", e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving transactions: " + e.getMessage());
//        }
//        return response;
//    }

//    @Override
//    public CustomResponse<?> getTransactionStatistics() {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            Map<String, Object> stats = new HashMap<>();
//
//            Long totalTransactions = bankRepository.count();
//            Long unmatchedTransactions = bankRepository.countByStudentIsNull();
//            Long matchedTransactions = totalTransactions - unmatchedTransactions;
//            Long successfulTransactions = bankRepository.countByStatus("SUCCESS");
//            Long failedTransactions = bankRepository.countByStatus("FAILED");
//
//            BigDecimal totalAmount = bankRepository.sumTotalAmount();
//            BigDecimal totalUnmatchedAmount = bankRepository.sumUnmatchedAmount();
//            BigDecimal totalSuccessfulAmount = bankRepository.sumAmountByStatus("SUCCESS");
//
//            stats.put("totalTransactions", totalTransactions);
//            stats.put("matchedTransactions", matchedTransactions);
//            stats.put("unmatchedTransactions", unmatchedTransactions);
//            stats.put("successfulTransactions", successfulTransactions);
//            stats.put("failedTransactions", failedTransactions);
//            stats.put("totalAmount", totalAmount != null ? totalAmount : BigDecimal.ZERO);
//            stats.put("totalUnmatchedAmount", totalUnmatchedAmount != null ? totalUnmatchedAmount : BigDecimal.ZERO);
//            stats.put("totalSuccessfulAmount", totalSuccessfulAmount != null ? totalSuccessfulAmount : BigDecimal.ZERO);
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(stats);
//            response.setMessage("Statistics retrieved successfully");
//        } catch (Exception e) {
//            log.error("Error retrieving transaction statistics: {}", e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error retrieving statistics: " + e.getMessage());
//        }
//        return response;
//    }
//
//    @Override
//    public CustomResponse<?> searchTransactions(String searchTerm) {
//        CustomResponse<Object> response = new CustomResponse<>();
//        try {
//            List<Bank> transactions = bankRepository.searchByCustomerNameOrMobile(searchTerm);
//            List<BankResponseDTO> dtos = convertToDTOList(transactions);
//
//            response.setStatusCode(HttpStatus.OK.value());
//            response.setEntity(dtos);
//            response.setMessage("Search completed successfully");
//        } catch (Exception e) {
//            log.error("Error searching transactions: {}", e.getMessage(), e);
//            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
//            response.setEntity(null);
//            response.setMessage("Error searching transactions: " + e.getMessage());
//        }
//        return response;
//    }

    // ============= PRIVATE HELPER METHODS =============

    private boolean isValidCallback(BankRequestDTO dto) {
        return dto != null
                && dto.getTransaction() != null
                && dto.getCustomer() != null;
    }

    private String extractStudentIdentifier(BankRequestDTO dto) {
        if (dto.getTransaction() != null && dto.getTransaction().getBillNumber() != null) {
            return dto.getTransaction().getBillNumber().trim().toUpperCase();
        }
        return null;
    }

    private Bank saveBankRecord(BankRequestDTO dto, Student student) {
        Bank bank = new Bank();

        // Set callback type
        bank.setCallbackType(dto.getCallbackType());

        // Set customer information
        if (dto.getCustomer() != null) {
            bank.setCustomerName(dto.getCustomer().getName());
            bank.setMobileNumber(dto.getCustomer().getMobileNumber());
            bank.setCustomerReference(dto.getCustomer().getReference());
        }

        // Set transaction information
        if (dto.getTransaction() != null) {
            bank.setTransactionDate(dto.getTransaction().getDate());
            bank.setTransactionReference(dto.getTransaction().getReference());
            bank.setPaymentMode(dto.getTransaction().getPaymentMode());
            bank.setAmount(dto.getTransaction().getAmount());
            bank.setCurrency(dto.getTransaction().getCurrency());
            bank.setBillNumber(dto.getTransaction().getBillNumber());
            bank.setServedBy(dto.getTransaction().getServedBy());
            bank.setAdditionalInfo(dto.getTransaction().getAdditionalInfo());
            bank.setOrderAmount(dto.getTransaction().getOrderAmount());
            bank.setServiceCharge(dto.getTransaction().getServiceCharge());
            bank.setOrderCurrency(dto.getTransaction().getOrderCurrency());
            bank.setStatus(dto.getTransaction().getStatus());
            bank.setRemarks(dto.getTransaction().getRemarks());
        }

        // Set bank information
        if (dto.getBank() != null) {
            bank.setBankReference(dto.getBank().getReference());
            bank.setTransactionType(dto.getBank().getTransactionType());
            bank.setAccountNumber(dto.getBank().getAccount());
        }

        // Set student and narrative
        if (student != null) {
            bank.setStudent(student);
            bank.setNarrative(String.format(
                    "School fee payment for %s - %s %s",
                    student.getAdmissionNumber(),
                    student.getFirstName(),
                    student.getLastName()
            ));
        } else {
            bank.setNarrative("Unmatched payment - requires manual reconciliation");
        }

        return bankRepository.save(bank);
    }

    private Finance updateFinanceRecord(Student student, BigDecimal amount) {
        Term currentTerm = Term.getCurrentTerm();

        if (currentTerm == null) {
            throw new IllegalStateException(
                    "No active term found for the current date. Please contact administration."
            );
        }

        Year academicYear = student.getAcademicYear();

        Optional<Finance> financeOpt = financeRepository.findByStudentIdAndTermAndYear(
                student.getId(),
                currentTerm,
                academicYear
        );

        Finance finance;
        if (financeOpt.isPresent()) {
            finance = financeOpt.get();
            BigDecimal newPaidAmount = finance.getPaidAmount().add(amount);
            finance.setPaidAmount(newPaidAmount);
            finance.setBalance(finance.getTotalFeeAmount().subtract(newPaidAmount));

            log.info("Updated finance for student: {}, Term: {}, Year: {}, New paid amount: {}, Balance: {}",
                    student.getAdmissionNumber(), currentTerm, academicYear, newPaidAmount, finance.getBalance());
        } else {
            finance = new Finance();
            finance.setStudentId(student.getId());

            BigDecimal totalFee = BigDecimal.ZERO;
            if (student.getFeeStructure() != null) {
                totalFee = BigDecimal.valueOf(student.getFeeStructure().getTotalAmount());
            }

            finance.setTotalFeeAmount(totalFee);
            finance.setPaidAmount(amount);
            finance.setBalance(totalFee.subtract(amount));
            finance.setTerm(currentTerm);
            finance.setYear(academicYear);

            log.info("Created new finance record for student: {}, Term: {}, Year: {}, Total fee: {}, Paid: {}, Balance: {}",
                    student.getAdmissionNumber(), currentTerm, academicYear, totalFee, amount, finance.getBalance());
        }

        finance.setLastUpdated(LocalDateTime.now());
        return financeRepository.save(finance);
    }

    private FinanceTransaction createFinanceTransaction(Student student, BigDecimal amount, BankRequestDTO dto) {
        Term currentTerm = Term.getCurrentTerm();
        Year currentYear = Year.now();
        Optional<StudentInvoices> optionalStudentInvoices = studentInvoicesRepository.findByStudentAndTermAndAcademicYear(student,currentTerm,currentYear);
        StudentInvoices studentInvoices = optionalStudentInvoices.get();

        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setStudentId(student.getId());
        transaction.setStudentName(String.format("%s %s", student.getFirstName(), student.getLastName()));
        transaction.setAdmissionNumber(student.getAdmissionNumber());
        transaction.setTransactionType(FinanceTransaction.TransactionType.INCOME);
        transaction.setCategory("School Fees Payment");
        transaction.setAmount(amount);
        transaction.setInvoiceId(studentInvoices.getId());
        transaction.setTerm(currentTerm);
        transaction.setTransactionDate(parseTransactionDate(dto.getTransaction().getDate()));

        String description = String.format(
                "%s payment - Ref: %s from %s",
                dto.getTransaction().getPaymentMode(),
                dto.getTransaction().getReference(),
                dto.getCustomer().getMobileNumber()
        );
        transaction.setDescription(description);

        transaction.setPaymentMethod(mapPaymentMethod(dto.getTransaction().getPaymentMode()));
        transaction.setReference(dto.getTransaction().getReference());
        transaction.setYear(student.getAcademicYear());

        return financeTransactionRepository.save(transaction);
    }

    private LocalDate parseTransactionDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return LocalDate.now();
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, formatter);
            return dateTime.toLocalDate();
        } catch (Exception e) {
            log.warn("Failed to parse transaction date: {}, using current date", dateStr);
            return LocalDate.now();
        }
    }

    private FinanceTransaction.PaymentMethod mapPaymentMethod(String paymentMode) {
        if (paymentMode == null) {
            return FinanceTransaction.PaymentMethod.OTHER;
        }

        return switch (paymentMode.toUpperCase()) {
            case "MPESA" -> FinanceTransaction.PaymentMethod.MPESA;
            case "CARD" -> FinanceTransaction.PaymentMethod.CARD;
            case "EQUITEL" -> FinanceTransaction.PaymentMethod.EQUITEL;
            case "PAYPAL" -> FinanceTransaction.PaymentMethod.PAYPAL;
            default -> FinanceTransaction.PaymentMethod.OTHER;
        };
    }

    private BankResponseDTO convertToDTO(Bank bank) {
        BankResponseDTO dto = new BankResponseDTO();
        // Map entity fields to DTO
        // Implement based on your BankResponseDTO structure
        return dto;
    }

    private List<BankResponseDTO> convertToDTOList(List<Bank> banks) {
        return banks.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
}