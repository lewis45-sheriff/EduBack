package com.EduePoa.EP.BankIntergration;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.BankIntergration.BankRequest.BankRequestDTO;
import com.EduePoa.EP.BankIntergration.BankResponse.BankResponseDTO;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionRepository;
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

import java.awt.print.Pageable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankServiceImpl implements BankService {

    private final BankRepository bankRepository;
    private final StudentRepository studentRepository;
    private final FinanceRepository financeRepository;
    private final FinanceTransactionRepository financeTransactionRepository;

    @Override
    @Transactional
    public CustomResponse<?> postTransactions(BankRequestDTO bankRequestDTO) {
        CustomResponse<Bank> response = new CustomResponse<>();

        try {

            // Validate request
            if (bankRequestDTO.getTransId() == null || bankRequestDTO.getTransId().isEmpty()) {
                response.setMessage("Transaction ID is required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(null);
                return response;
            }

            // Check for duplicate transaction
            if (bankRepository.existsByTransId(bankRequestDTO.getTransId())) {
                response.setMessage("Duplicate transaction");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setEntity(null);
                return response;
            }

            // Extract and validate amount
            BigDecimal amount = validateAndParseAmount(bankRequestDTO.getTransAmount());
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                response.setMessage("Invalid transaction amount");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(null);
                return response;
            }

            // Extract student identifier
            String studentIdentifier = extractStudentIdentifier(bankRequestDTO);
            if (studentIdentifier == null || studentIdentifier.isEmpty()) {
                Bank unlinkedBank = saveBankRecord(bankRequestDTO, null);
                response.setMessage("Payment received but no student identifier provided");
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(unlinkedBank);
                return response;
            }

            // Find student
            Optional<Student> studentOpt = studentRepository.findByAdmissionNumber(studentIdentifier);
            if (studentOpt.isEmpty()) {
                Bank unlinkedBank = saveBankRecord(bankRequestDTO, null);
                response.setMessage("Payment received but student not found. Contact admin with reference: " + bankRequestDTO.getTransId());
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(unlinkedBank);
                return response;
            }

            Student student = studentOpt.get();

            // Save bank record
            Bank bank = saveBankRecord(bankRequestDTO, student);

            // Update finance record
            Finance finance = updateFinanceRecord(student, amount);

            // Create finance transaction
            FinanceTransaction transaction = createFinanceTransaction(student, amount, bankRequestDTO);



            response.setMessage("Payment processed successfully. New balance: ");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(bank);

        } catch (NumberFormatException e) {
            log.error("Invalid amount format: {}", bankRequestDTO.getTransAmount(), e);
            response.setMessage("Invalid transaction amount format");
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
        } catch (Exception e) {
            log.error("Error processing bank callback for TransID: {}", bankRequestDTO.getTransId(), e);
            response.setMessage("Error processing payment: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    private BankResponseDTO convertToDTO(Bank bank) {
        return new BankResponseDTO();
    }

    // Helper method to convert list of entities to DTOs
    private List<BankResponseDTO> convertToDTOList(List<Bank> banks) {
        return banks.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get all transactions
    public CustomResponse<?> getTransactions() {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Transactions retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get transactions with pagination
    public CustomResponse<?> getTransactionsWithPagination(int page, int size) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Pageable pageable = (Pageable) PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<Bank> transactionsPage = bankRepository.findAll((org.springframework.data.domain.Pageable) pageable);

            List<BankResponseDTO> dtos = convertToDTOList(transactionsPage.getContent());

            Map<String, Object> result = new HashMap<>();
            result.put("transactions", dtos);
            result.put("currentPage", transactionsPage.getNumber());
            result.put("totalItems", transactionsPage.getTotalElements());
            result.put("totalPages", transactionsPage.getTotalPages());

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(result);
            response.setMessage("Transactions retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get transaction by TransID
    public CustomResponse<?> getTransactionByTransId(String transId) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Optional<Bank> transaction = bankRepository.findByTransId(transId);
            if (transaction.isPresent()) {
                BankResponseDTO dto = convertToDTO(transaction.get());
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(dto);
                response.setMessage("Transaction found");
            } else {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                response.setMessage("Transaction not found");
            }
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get transactions by student ID
    public CustomResponse<?> getTransactionsByStudentId(Long studentId) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.findByStudentId(studentId);
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Transactions retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get transactions by admission number
    public CustomResponse<?> getTransactionsByAdmissionNumber(String admissionNumber) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.findByBillRefNumber(admissionNumber);
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Transactions retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get transactions by date range
    public CustomResponse<?> getTransactionsByDateRange(String startDate, String endDate) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.findByTransTimeBetween(startDate, endDate);
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Transactions retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get unmatched transactions (no student linked)
    public CustomResponse<?> getUnmatchedTransactions() {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.findByStudentIsNull();
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Unmatched transactions retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Get transaction statistics
    public CustomResponse<?> getTransactionStatistics() {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Map<String, Object> stats = new HashMap<>();

            Long totalTransactions = bankRepository.count();
            Long unmatchedTransactions = bankRepository.countByStudentIsNull();
            Long matchedTransactions = totalTransactions - unmatchedTransactions;

            Double totalAmount = bankRepository.sumTransAmount();
            Double totalUnmatchedAmount = bankRepository.sumUnmatchedTransAmount();

            stats.put("totalTransactions", totalTransactions);
            stats.put("matchedTransactions", matchedTransactions);
            stats.put("unmatchedTransactions", unmatchedTransactions);
            stats.put("totalAmount", totalAmount != null ? totalAmount : 0.0);
            stats.put("totalUnmatchedAmount", totalUnmatchedAmount != null ? totalUnmatchedAmount : 0.0);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(stats);
            response.setMessage("Statistics retrieved successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    // Search transactions by name or mobile
    public CustomResponse<?> searchTransactions(String searchTerm) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Bank> transactions = bankRepository.searchByCustomerNameOrMobile(searchTerm);
            List<BankResponseDTO> dtos = convertToDTOList(transactions);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtos);
            response.setMessage("Search completed successfully");
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    private BigDecimal validateAndParseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractStudentIdentifier(BankRequestDTO dto) {
        // Priority: BillRefNumber -> AccountNumber -> InvoiceNumber
//        if (dto.getBillRefNumber() != null && !dto.getBillRefNumber().trim().isEmpty()) {
//            return dto.getBillRefNumber().trim().toUpperCase();
//        }
//        if (dto.getAccountNumber() != null && !dto.getAccountNumber().trim().isEmpty()) {
//            return dto.getAccountNumber().trim().toUpperCase();
//        }
        if (dto.getMsisdn() != null && !dto.getInvoiceNumber().trim().isEmpty()) {
            return dto.getInvoiceNumber().trim().toUpperCase();
        }
        return null;
    }

    private Bank saveBankRecord(BankRequestDTO dto, Student student) {
        Bank bank = new Bank();
        bank.setTransType(dto.getTransactionType());
        bank.setTransId(dto.getTransId());
        bank.setFtRef(dto.getThirdPartyTransId());
        bank.setTransTime(dto.getTransTime());
        bank.setTransAmount(dto.getTransAmount());
        bank.setBusinessShortCode(dto.getBusinessShortCode());
        bank.setBillRefNumber(dto.getBillRefNumber());
        bank.setMobile(dto.getMsisdn());
        bank.setAccountNumber(dto.getAccountNumber());
        bank.setStatusCode("00");
        bank.setStatusDescription("Payment received successfully");

        String customerName = buildCustomerName(dto);
        bank.setCustomerName(customerName);

        if (student != null) {
            bank.setStudent(student);
            bank.setNarrative("School fee payment for " + student.getAdmissionNumber());
        } else {
            bank.setNarrative("Unmatched payment - requires manual reconciliation");
        }

        return bankRepository.save(bank);
    }

    private String buildCustomerName(BankRequestDTO dto) {
        StringBuilder name = new StringBuilder();
        if (dto.getFirstName() != null && !dto.getFirstName().isEmpty()) {
            name.append(dto.getFirstName().trim());
        }
        if (dto.getMiddleName() != null && !dto.getMiddleName().isEmpty()) {
            if (!name.isEmpty()) name.append(" ");
            name.append(dto.getMiddleName().trim());
        }
        if (dto.getLastName() != null && !dto.getLastName().isEmpty()) {
            if (!name.isEmpty()) name.append(" ");
            name.append(dto.getLastName().trim());
        }
        return !name.isEmpty() ? name.toString() : "Unknown";
    }

    private Finance updateFinanceRecord(Student student, BigDecimal amount) {
        // Get the current active term based on today's date
        Term currentTerm = Term.getCurrentTerm();

        // If no active term is found, throw an exception
        if (currentTerm == null) {
            throw new IllegalStateException(
                    "No active term found for the current date. Please contact administration."
            );
        }

        // Get the academic year from the student
        Year academicYear = student.getAcademicYear();

        Optional<Finance> financeOpt = financeRepository.findByStudentIdAndTermAndYear(
                student.getId(),
                currentTerm,
                academicYear
        );

        Finance finance;
        if (financeOpt.isPresent()) {
            // Update existing finance record
            finance = financeOpt.get();
            BigDecimal newPaidAmount = finance.getPaidAmount().add(amount);
            finance.setPaidAmount(newPaidAmount);
            finance.setBalance(finance.getTotalFeeAmount().subtract(newPaidAmount));

            log.info("Updated finance record for student: {}, Term: {}, Year: {}, Amount paid: {}",
                    student.getAdmissionNumber(), currentTerm, academicYear, amount);
        } else {
            // Create new finance record for the current term
            finance = new Finance();
            finance.setStudentId(student.getId());
            finance.setTotalFeeAmount(BigDecimal.ZERO);
            finance.setPaidAmount(amount);
            finance.setBalance(BigDecimal.ZERO.subtract(amount)); // Negative balance means overpayment
            finance.setTerm(currentTerm);
            finance.setYear(academicYear);

            log.info("Created new finance record for student: {}, Term: {}, Year: {}, Amount paid: {}",
                    student.getAdmissionNumber(), currentTerm, academicYear, amount);
        }

        finance.setLastUpdated(LocalDateTime.now());
        return financeRepository.save(finance);
    }

    // Alternative: If you want to set total fee amount from FeeStructure
    private Finance updateFinanceRecordWithFeeStructure(Student student, BigDecimal amount) {
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
        } else {
            finance = new Finance();
            finance.setStudentId(student.getId());

            // Set total fee amount from student's fee structure if available
            if (student.getFeeStructure() != null) {
                BigDecimal totalFee = BigDecimal.valueOf(student.getFeeStructure().getTotalAmount()); // Adjust based on your FeeStructure entity
                finance.setTotalFeeAmount(totalFee != null ? totalFee : BigDecimal.ZERO);
                finance.setBalance(finance.getTotalFeeAmount().subtract(amount));
            } else {
                finance.setTotalFeeAmount(BigDecimal.ZERO);
                finance.setBalance(BigDecimal.ZERO.subtract(amount));
            }

            finance.setPaidAmount(amount);
            finance.setTerm(currentTerm);
            finance.setYear(academicYear);
        }

        finance.setLastUpdated(LocalDateTime.now());
        return financeRepository.save(finance);
    }

    private FinanceTransaction createFinanceTransaction(Student student, BigDecimal amount, BankRequestDTO dto) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setStudentId(student.getId());
        transaction.setStudentName(student.getFirstName() + " " + student.getLastName());
        transaction.setAdmissionNumber(student.getAdmissionNumber());
        transaction.setTransactionType(FinanceTransaction.TransactionType.INCOME);
        transaction.setCategory("School Fees Payment");
        transaction.setAmount(amount);
        transaction.setTransactionDate(parseTransactionDate(dto.getTransTime()));
        transaction.setDescription("Equity Bank payment - " + dto.getTransId() + " from " + dto.getMsisdn());
        transaction.setPaymentMethod(FinanceTransaction.PaymentMethod.MPESA);
        transaction.setReference(dto.getTransId());
        transaction.setYear(student.getAcademicYear());

        return financeTransactionRepository.save(transaction);
    }

    private LocalDate parseTransactionDate(String transTime) {
        if (transTime == null || transTime.isEmpty()) {
            return LocalDate.now();
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            LocalDateTime dateTime = LocalDateTime.parse(transTime, formatter);
            return dateTime.toLocalDate();
        } catch (Exception e) {
            log.warn("Failed to parse transaction time: {}, using current date", transTime);
            return LocalDate.now();
        }
    }
}