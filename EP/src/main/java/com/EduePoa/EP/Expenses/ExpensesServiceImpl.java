package com.EduePoa.EP.Expenses;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Expenses.Requests.ExpenseRequestDTO;
import com.EduePoa.EP.Expenses.Responses.ExpenseResponseDTO;
import com.EduePoa.EP.Expenses.Responses.ExpenseSummaryDTO;
import com.EduePoa.EP.Procurement.Ledger.LedgerService;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExpensesServiceImpl implements ExpensesService {

    private final ExpensesRepository expensesRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;

    @Override
    @Audit(module = "EXPENSES", action = "CREATE")
    @Transactional
    public CustomResponse<?> create(ExpenseRequestDTO dto) {
        CustomResponse<ExpenseResponseDTO> response = new CustomResponse<>();
        try {
            User currentUser = getCurrentUser();

            // Auto-generate expense number: EXP-XXXX
            long count = expensesRepository.countAll();
            String expenseNumber = String.format("EXP-%04d", count + 1);

            Expenses expense = Expenses.builder()
                    .expenseNumber(expenseNumber)
                    .expenseDate(dto.getExpenseDate())
                    .amount(dto.getAmount())
                    .category(dto.getCategory())
                    .paymentMethod(dto.getPaymentMethod())
                    .description(dto.getDescription())
                    .vendorName(dto.getVendorName())
                    .receiptNumber(dto.getReceiptNumber())
                    .referenceNumber(dto.getReferenceNumber())
                    .status(dto.getStatus() != null ? dto.getStatus() : "SUBMITTED")
                    .notes(dto.getNotes())
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .build();

            Expenses saved = expensesRepository.save(expense);

            response.setMessage("Expense created successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.CREATED.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAll(int page, int size, String sortBy, String sortDir,
                                     String search, String category, String status,
                                     String paymentMethod, LocalDate startDate, LocalDate endDate) {
        CustomResponse<Page<ExpenseResponseDTO>> response = new CustomResponse<>();
        try {
            Sort sort = Sort.by(
                    "DESC".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC,
                    sortBy != null ? sortBy : "expenseDate"
            );
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Expenses> expensePage = expensesRepository.findByFilters(
                    search, category, status, paymentMethod, startDate, endDate, pageable);

            Page<ExpenseResponseDTO> dtoPage = expensePage.map(this::toResponseDTO);

            response.setMessage("Expenses fetched successfully");
            response.setEntity(dtoPage);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getById(Long id) {
        CustomResponse<ExpenseResponseDTO> response = new CustomResponse<>();
        try {
            Expenses expense = expensesRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));

            response.setMessage("Expense fetched successfully");
            response.setEntity(toResponseDTO(expense));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "EXPENSES", action = "UPDATE")
    @Transactional
    public CustomResponse<?> update(Long id, ExpenseRequestDTO dto) {
        CustomResponse<ExpenseResponseDTO> response = new CustomResponse<>();
        try {
            Expenses existing = expensesRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));

            existing.setExpenseDate(dto.getExpenseDate());
            existing.setAmount(dto.getAmount());
            existing.setCategory(dto.getCategory());
            existing.setPaymentMethod(dto.getPaymentMethod());
            existing.setDescription(dto.getDescription());
            existing.setVendorName(dto.getVendorName());
            existing.setReceiptNumber(dto.getReceiptNumber());
            existing.setReferenceNumber(dto.getReferenceNumber());
            if (dto.getStatus() != null) {
                existing.setStatus(dto.getStatus());
            }
            existing.setNotes(dto.getNotes());
            existing.setUpdatedAt(LocalDateTime.now());

            Expenses saved = expensesRepository.save(existing);

            response.setMessage("Expense updated successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "EXPENSES", action = "DELETE")
    @Transactional
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            Expenses expense = expensesRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));

            if ("APPROVED".equalsIgnoreCase(expense.getStatus())) {
                throw new RuntimeException(
                        "Cannot delete expense '" + expense.getExpenseNumber() + "' because it has already been approved.");
            }

            expensesRepository.deleteById(id);

            response.setMessage("Expense deleted successfully");
            response.setEntity(null);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getSummary(LocalDate startDate, LocalDate endDate) {
        CustomResponse<ExpenseSummaryDTO> response = new CustomResponse<>();
        try {
            // Default to current month if no dates provided
            LocalDate from = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
            LocalDate to = endDate != null ? endDate : LocalDate.now();

            BigDecimal totalAmount = expensesRepository.sumAmountByDateRange(from, to);
            Long totalCount = expensesRepository.countByDateRange(from, to);
            BigDecimal approvedAmount = expensesRepository.sumAmountByStatusAndDateRange("APPROVED", from, to);
            BigDecimal pendingAmount = expensesRepository.sumAmountByStatusAndDateRange("SUBMITTED", from, to);

            ExpenseSummaryDTO summary = ExpenseSummaryDTO.builder()
                    .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
                    .totalCount(totalCount != null ? totalCount : 0L)
                    .approvedAmount(approvedAmount != null ? approvedAmount : BigDecimal.ZERO)
                    .pendingAmount(pendingAmount != null ? pendingAmount : BigDecimal.ZERO)
                    .startDate(from)
                    .endDate(to)
                    .build();

            response.setMessage("Expense summary fetched successfully");
            response.setEntity(summary);
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "EXPENSES", action = "APPROVE")
    @Transactional
    public CustomResponse<?> approveExpense(Long id) {
        CustomResponse<ExpenseResponseDTO> response = new CustomResponse<>();
        try {
            Expenses existing = expensesRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));

            if ("APPROVED".equalsIgnoreCase(existing.getStatus())) {
                throw new RuntimeException("Expense is already approved: " + existing.getExpenseNumber());
            }

            existing.setStatus("APPROVED");
            existing.setUpdatedAt(LocalDateTime.now());
            Expenses saved = expensesRepository.save(existing);

            // Record debit in ledger
            User approver = getCurrentUser();
            ledgerService.recordDebit(
                    saved.getAmount(),
                    "Expense approved: " + saved.getDescription(),
                    saved.getId(),
                    saved.getExpenseNumber(),
                    saved.getExpenseDate(),
                    approver
            );

            response.setMessage("Expense approved and ledger debit recorded successfully");
            response.setEntity(toResponseDTO(saved));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        } catch (Exception e) {
            response.setMessage("Error approving expense: " + e.getMessage());
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("No authenticated user found");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Current user not found: " + auth.getName()));
    }

    private ExpenseResponseDTO toResponseDTO(Expenses expense) {
        String createdByName = null;
        if (expense.getCreatedBy() != null) {
            User creator = expense.getCreatedBy();
            createdByName = (creator.getFirstName() != null ? creator.getFirstName() : "")
                    + " " + (creator.getLastName() != null ? creator.getLastName() : "");
            createdByName = createdByName.trim();
            if (createdByName.isEmpty()) {
                createdByName = creator.getEmail();
            }
        }

        return ExpenseResponseDTO.builder()
                .id(expense.getId())
                .expenseNumber(expense.getExpenseNumber())
                .expenseDate(expense.getExpenseDate())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .paymentMethod(expense.getPaymentMethod())
                .description(expense.getDescription())
                .vendorName(expense.getVendorName())
                .receiptNumber(expense.getReceiptNumber())
                .referenceNumber(expense.getReferenceNumber())
                .status(expense.getStatus())
                .notes(expense.getNotes())
                .createdByName(createdByName)
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
