package com.EduePoa.EP.MpesaPaybill;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Finance.Finance;
import com.EduePoa.EP.Finance.FinanceRepository;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.FinanceTransaction.FinanceTransactionRepository;
import com.EduePoa.EP.MpesaPaybill.Requests.ConfirmationRequest;
import com.EduePoa.EP.MpesaPaybill.Requests.RegisterRequest;
import com.EduePoa.EP.MpesaPaybill.Requests.ValidationRequest;
import com.EduePoa.EP.MpesaPaybill.Response.ConfirmationResponse;
import com.EduePoa.EP.MpesaPaybill.Response.ValidationResponse;
import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.service.MpesaCallbackTenantResolver;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaPaybillService {
    @Value("${mpesapaybill.app.key}")
    private String appKeY;

    @Value("${mpesapaybill.app.secret}")
    private String appSecret;

    @Value("${mpesapaybill.token.url}")
    private String stkAuthUrl;
    private final MpesaPaybillRepository mpesaPaybillRepository;
    private final FinanceRepository financeRepository;
    private final FinanceTransactionRepository financeTransactionRepository;
    private final MpesaCallbackTenantResolver mpesaCallbackTenantResolver;
    private final com.EduePoa.EP.StudentRegistration.StudentRepository studentRepository;
    private final com.EduePoa.EP.GeneralLedger.service.posting.GlPostingGateway glPostingGateway;

    /**
     * Lists all M-Pesa C2B (paybill) transactions for the current tenant, newest first,
     * shaped for the reconciliation dashboard. Reconciliation state is derived by matching
     * each paybill {@code TransID} to a {@link FinanceTransaction} with the same reference:
     * when a posting exists, the row is considered reconciled and the linked student id /
     * posting date are surfaced.
     */
    @Transactional(readOnly = true)
    CustomResponse<java.util.List<com.EduePoa.EP.MpesaPaybill.Response.MpesaC2bTransactionResponse>> getAllC2bTransactions() {
        java.util.List<MpesaPaybill> paybills = mpesaPaybillRepository.findAll();

        java.util.List<com.EduePoa.EP.MpesaPaybill.Response.MpesaC2bTransactionResponse> items =
                paybills.stream()
                        .sorted(java.util.Comparator.comparing(MpesaPaybill::getId,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                        .map(this::toC2bResponse)
                        .toList();

        CustomResponse<java.util.List<com.EduePoa.EP.MpesaPaybill.Response.MpesaC2bTransactionResponse>> response =
                new CustomResponse<>();
        response.setStatusCode(HttpStatus.OK.value());
        response.setMessage("M-Pesa C2B transactions retrieved");
        response.setEntity(items);
        return response;
    }

    private com.EduePoa.EP.MpesaPaybill.Response.MpesaC2bTransactionResponse toC2bResponse(MpesaPaybill p) {
        String transId = p.getTransID();

        // Derive reconciliation from the finance posting keyed on the same reference (TransID).
        boolean reconciled = false;
        String studentId = null;
        String reconciledAt = null;
        String reconciledBy = null;
        if (transId != null && !transId.isBlank()) {
            java.util.Optional<FinanceTransaction> ftOpt = financeTransactionRepository.findFirstByReference(transId);
            if (ftOpt.isPresent()) {
                FinanceTransaction ft = ftOpt.get();
                reconciled = true;
                studentId = ft.getStudentId() != null ? String.valueOf(ft.getStudentId()) : null;
                reconciledAt = ft.getCreatedAt() != null ? ft.getCreatedAt().toString() : null;
                reconciledBy = ft.getStudentName();
            }
        }
        // Fall back to the account reference (BillRefNumber = student id) when no posting matched.
        if (studentId == null && p.getBillRefNumber() != null && !p.getBillRefNumber().isBlank()) {
            studentId = p.getBillRefNumber().trim();
        }

        return com.EduePoa.EP.MpesaPaybill.Response.MpesaC2bTransactionResponse.builder()
                .internalId(p.getId() != null ? String.valueOf(p.getId()) : (transId != null ? transId : ""))
                .transactionId(transId != null ? transId : "")
                .transactionType(p.getTransactionType() != null ? p.getTransactionType() : "Pay Bill")
                .transID(transId != null ? transId : "")
                .transTime(p.getTransTime())
                .transAmount(parseAmount(p.getTransAmount()))
                .businessShortCode(p.getBusinessShortCode())
                .billRefNumber(p.getBillRefNumber())
                .invoiceNumber(p.getInvoiceNumber())
                .orgAccountBalance(parseAmount(p.getOrgAccountBalance()))
                .thirdPartyTransID(p.getThirdPartyTransID())
                .msisdn(p.getMSISDN() != null ? p.getMSISDN() : "")
                .firstName(p.getFirstName())
                .middleName(p.getMiddleName())
                .lastName(p.getLastName())
                .isReconciled(reconciled)
                .studentId(reconciled ? studentId : null)
                .reconciledAt(reconciledAt)
                .reconciledBy(reconciledBy)
                .build();
    }

    private double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0d;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

CustomResponse<?> registerUrl(RegisterRequest registerRequest) {
    CustomResponse<String> response = new CustomResponse<>();

    try {
        // Get access token using your existing method
        String accessToken = generateToken();

        OkHttpClient client = new OkHttpClient();

        // Build JSON payload
        String payload = String.format(
                "{\"ShortCode\":\"174379\",\"ResponseType\":\"Completed\",\"ConfirmationURL\":\"%s\",\"ValidationURL\":\"%s\"}",
                registerRequest.getConfirmationURL(),
                registerRequest.getValidationURL()
        );


        RequestBody body = RequestBody.create(
                payload,
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/c2b/v2/registerurl")
                .post(body)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .build();

        Response httpResponse = client.newCall(request).execute();
        String responseBody = httpResponse.body() != null ? httpResponse.body().string() : "";

        if (httpResponse.isSuccessful()) {
            response.setMessage("URL registration successful");
            response.setEntity(responseBody);
            response.setStatusCode(HttpStatus.OK.value());
        } else {
            response.setMessage("URL registration failed: " + responseBody);
            response.setEntity(null);
            response.setStatusCode(httpResponse.code());
        }

    } catch (IOException e) {
        response.setMessage("Network error: " + e.getMessage());
        response.setEntity(null);
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    } catch (RuntimeException e) {
        response.setMessage(e.getMessage());
        response.setEntity(null);
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    return response;
}
    CustomResponse<?> validate(ValidationRequest validationRequest) {
        CustomResponse<ValidationResponse> response = new CustomResponse<>();
        try {
            System.out.println("===== VALIDATION REQUEST =====");
            System.out.println("Transaction ID: " + validationRequest.getTransID());
            System.out.println("Amount: " + validationRequest.getTransAmount());
            System.out.println("Account: " + validationRequest.getBillRefNumber());
            System.out.println("Phone: " + validationRequest.getMSISDN());
            System.out.println("==============================");

            // Create validation response object
            ValidationResponse validationResponse = new ValidationResponse();

            // 1. Validate account/bill reference number
            String accountNumber = validationRequest.getBillRefNumber();
            if (accountNumber == null || accountNumber.trim().isEmpty()) {
                validationResponse.setResultCode("C2B00012");
                validationResponse.setResultDesc("Invalid Account Number");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(validationResponse);
                response.setMessage("Transaction rejected: Invalid account");
                return response;
            }

            // 2. Check if account exists in your system
            // boolean accountExists = accountService.exists(accountNumber);
            // Uncomment below when you implement account checking
            /*
            if (!accountExists) {
                validationResponse.setResultCode("C2B00012");
                validationResponse.setResultDesc("Account not found");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(validationResponse);
                response.setMessage("Transaction rejected: Account not found");
                return response;
            }
            */

            // 3. Validate amount
            double amount;
            try {
                amount = Double.parseDouble(validationRequest.getTransAmount());
            } catch (NumberFormatException e) {
                validationResponse.setResultCode("C2B00013");
                validationResponse.setResultDesc("Invalid Amount");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(validationResponse);
                response.setMessage("Transaction rejected: Invalid amount format");
                return response;
            }

            // Check minimum amount (e.g., KES 10)
            if (amount < 10) {
                validationResponse.setResultCode("C2B00013");
                validationResponse.setResultDesc("Amount below minimum threshold");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(validationResponse);
                response.setMessage("Transaction rejected: Amount too low");
                return response;
            }

            // Check maximum amount (optional)
            if (amount > 150000) { // Example: KES 150,000 max
                validationResponse.setResultCode("C2B00013");
                validationResponse.setResultDesc("Amount exceeds maximum threshold");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(validationResponse);
                response.setMessage("Transaction rejected: Amount too high");
                return response;
            }

            // 4. Check for duplicate transaction
            // boolean isDuplicate = transactionRepository.existsByTransID(validationRequest.getTransID());
            // Uncomment below when you implement duplicate checking
            /*
            if (isDuplicate) {
                validationResponse.setResultCode("C2B00011");
                validationResponse.setResultDesc("Duplicate transaction");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(validationResponse);
                response.setMessage("Transaction rejected: Duplicate");
                return response;
            }
            */

            // 5. Additional business logic validations
            // - Check if account is active
            // - Check if account is not suspended
            // - Fraud detection checks
            // Add your custom validation logic here

            // All validations passed - ACCEPT TRANSACTION
            validationResponse.setResultCode("0");
            validationResponse.setResultDesc("Accepted");

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(validationResponse);
            response.setMessage("Transaction validated successfully");

            System.out.println("Validation Result: ACCEPTED");

        } catch (Exception e) {
            // In case of error, reject the transaction
            ValidationResponse validationResponse = new ValidationResponse();
            validationResponse.setResultCode("C2B00011");
            validationResponse.setResultDesc("System error occurred");

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(validationResponse);
            response.setMessage("Validation error: " + e.getMessage());

            System.err.println("Validation Error: " + e.getMessage());
            e.printStackTrace();
        }
        return response;
    }
    @Transactional
    CustomResponse<?> processCallBack(ConfirmationRequest request) {
        CustomResponse<ConfirmationResponse> response = new CustomResponse<>();
        try {
            System.out.println("===== CONFIRMATION REQUEST =====");
            System.out.println("Transaction ID: " + request.getTransID());
            System.out.println("Amount: " + request.getTransAmount());
            System.out.println("Account: " + request.getBillRefNumber());
            System.out.println("Phone: " + request.getMSISDN());
            System.out.println("Time: " + request.getTransTime());
            System.out.println("Customer: " + request.getFirstName() + " " + request.getLastName());
            System.out.println("================================");

            // Resolve tenant from callback payload (shortcode or account reference)
            boolean tenantResolved = mpesaCallbackTenantResolver.resolveAndSetTenant(
                    request.getBusinessShortCode(),
                    request.getBillRefNumber());

            if (!tenantResolved) {
                log.error("Cannot process M-Pesa callback: unable to resolve tenant. " +
                                "TransID={}, ShortCode={}, BillRefNumber={}",
                        request.getTransID(), request.getBusinessShortCode(), request.getBillRefNumber());

                ConfirmationResponse confirmationResponse = new ConfirmationResponse();
                confirmationResponse.setResultCode("0");
                confirmationResponse.setResultDesc("Success");

                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(confirmationResponse);
                response.setMessage("Payment acknowledged but tenant could not be resolved");
                return response;
            }

            // Create confirmation response
            ConfirmationResponse confirmationResponse = new ConfirmationResponse();

            // Parse transaction details
            String transactionId = request.getTransID();
            // The account reference (BillRefNumber) is the student's ADMISSION NUMBER —
            // the real, human-facing unique identifier a parent types when paying. We
            // resolve the student from it rather than assuming a numeric primary key.
            String admissionNumber = request.getBillRefNumber() != null
                    ? request.getBillRefNumber().trim() : null;
            double amount = Double.parseDouble(request.getTransAmount());
            String phoneNumber = request.getMSISDN();
            String transactionTime = request.getTransTime();
            LocalDateTime transactionDate = parseTransactionTime(transactionTime);

            if (admissionNumber == null || admissionNumber.isBlank()) {
                throw new RuntimeException("Missing account reference (admission number) in callback");
            }

            // Resolve the student by admission number (tenant-scoped).
            com.EduePoa.EP.StudentRegistration.Student student = studentRepository
                    .findByAdmissionNumber(admissionNumber)
                    .orElseThrow(() -> new RuntimeException(
                            "No student found for admission number: " + admissionNumber));
            Long studentId = student.getId();

            // Get current term and year
            Term currentTerm = Term.getCurrentTerm();
            Year currentYear = Year.now();

            if (currentTerm == null) {
                throw new RuntimeException("No active term found for current date");
            }

            //  Save to MpesaPaybill table
            MpesaPaybill paybillTransaction = new MpesaPaybill();

            paybillTransaction.setTransactionType(request.getTransactionType());
            paybillTransaction.setTransID(request.getTransID());
            paybillTransaction.setTransTime(request.getTransTime());
            paybillTransaction.setTransAmount(request.getTransAmount());
            paybillTransaction.setBusinessShortCode(request.getBusinessShortCode());
            paybillTransaction.setBillRefNumber(request.getBillRefNumber());
            paybillTransaction.setInvoiceNumber(request.getInvoiceNumber());
            paybillTransaction.setOrgAccountBalance(request.getOrgAccountBalance());
            paybillTransaction.setThirdPartyTransID(request.getThirdPartyTransID());
            paybillTransaction.setMSISDN(request.getMSISDN());
            paybillTransaction.setFirstName(request.getFirstName());
            paybillTransaction.setMiddleName(request.getMiddleName());
            paybillTransaction.setLastName(request.getLastName());

            mpesaPaybillRepository.save(paybillTransaction);


            System.out.println("✓ Saved to MpesaPaybill table");

            // STEP 2: Update Finance table
            Finance finance = financeRepository.findByStudentIdAndTermAndYear(
                    studentId, currentTerm, currentYear
            ).orElseThrow(() -> new RuntimeException("Finance record not found for student: " + studentId));

            BigDecimal paymentAmount = BigDecimal.valueOf(amount);
            BigDecimal currentPaidAmount = finance.getPaidAmount();
            BigDecimal newPaidAmount = currentPaidAmount.add(paymentAmount);
            BigDecimal newBalance = finance.getTotalFeeAmount().subtract(newPaidAmount);

            finance.setPaidAmount(newPaidAmount);
            finance.setBalance(newBalance);
            finance.setLastUpdated(LocalDateTime.now());
            financeRepository.save(finance);

            System.out.println("✓ Updated Finance table - New balance: " + newBalance);

            // Create FinanceTransaction record. Use the resolved student's own name and
            // admission number (the authoritative record) rather than the free-text name
            // fields on the M-Pesa payload, which reflect the paying phone's registered name.
            String studentFullName = java.util.stream.Stream.of(
                            student.getFirstName(), student.getMiddleName(), student.getLastName())
                    .filter(s -> s != null && !s.isBlank())
                    .reduce((a, b) -> a + " " + b)
                    .orElse(admissionNumber);
            FinanceTransaction financeTransaction = new FinanceTransaction();
            financeTransaction.setStudentId(studentId);
            financeTransaction.setStudentName(studentFullName);
            financeTransaction.setAdmissionNumber(admissionNumber);
            financeTransaction.setTransactionType(FinanceTransaction.TransactionType.INCOME);
            financeTransaction.setCategory("FEE_PAYMENT");
            financeTransaction.setSource(FinanceTransaction.TransactionSource.MPESA_CALLBACK);
            financeTransaction.setAmount(paymentAmount);
            financeTransaction.setTransactionDate(transactionDate.toLocalDate());
            financeTransaction.setDescription("M-Pesa payment - " + transactionId);
            financeTransaction.setPaymentMethod(FinanceTransaction.PaymentMethod.MPESA);
            financeTransaction.setReference(transactionId);
            financeTransaction.setYear(currentYear);
            FinanceTransaction savedPaybillTxn = financeTransactionRepository.save(financeTransaction);

            System.out.println("✓ Created FinanceTransaction record");

            // Durable double-entry GL posting for this confirmed C2B receipt. Keyed on
            // the FinanceTransaction id; the GL idempotency constraint also protects
            // against a duplicate C2B confirmation producing a second journal even
            // though this legacy path has no transaction-level dedup of its own.
            try {
                com.EduePoa.EP.GeneralLedger.service.posting.GlPostingPayload glPayload =
                        new com.EduePoa.EP.GeneralLedger.service.posting.GlPostingPayload(
                                savedPaybillTxn.getTransactionDate(),
                                savedPaybillTxn.getReference(),
                                "M-Pesa paybill fee payment - " + transactionId,
                                java.util.Map.of("amount",
                                        paymentAmount.setScale(2, java.math.RoundingMode.HALF_UP)));
                glPostingGateway.requestPosting(
                        com.EduePoa.EP.GeneralLedger.enums.GlSourceType.STUDENT_FEE_RECEIPT,
                        savedPaybillTxn.getId(), glPayload);
            } catch (Exception glEx) {
                log.error("Failed to enqueue GL posting for paybill transaction {}: {}",
                        savedPaybillTxn.getId(), glEx.getMessage());
            }

            // Optional: Send notifications
            // sendSmsConfirmation(phoneNumber, amount, transactionId);
            // sendEmailConfirmation(accountNumber, amount, transactionId);

            // Always return success to Safaricom
            confirmationResponse.setResultCode("0");
            confirmationResponse.setResultDesc("Success");

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(confirmationResponse);
            response.setMessage("Payment processed successfully");

            System.out.println("Payment processed successfully for Student ID: " + studentId);
            System.out.println("Amount credited: KES " + amount);
            System.out.println("New balance: KES " + newBalance);

        } catch (NumberFormatException e) {
            System.err.println("Error parsing amount or student ID: " + e.getMessage());

            ConfirmationResponse confirmationResponse = new ConfirmationResponse();
            confirmationResponse.setResultCode("0");
            confirmationResponse.setResultDesc("Success");

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(confirmationResponse);
            response.setMessage("Payment acknowledged with parsing error");

            saveFailedTransaction(request, e.getMessage());

        } catch (Exception e) {
            System.err.println("Error processing callback: " + e.getMessage());
            e.printStackTrace();

            ConfirmationResponse confirmationResponse = new ConfirmationResponse();
            confirmationResponse.setResultCode("0");
            confirmationResponse.setResultDesc("Success");

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(confirmationResponse);
            response.setMessage("Payment acknowledged with processing error");

            saveFailedTransaction(request, e.getMessage());
        }
        // NOTE: TenantContext is intentionally NOT cleared here. This method is
        // @Transactional, so the commit (and the JPA flush that fires the
        // TenantEntityListener @PreUpdate/@PrePersist callbacks) runs AFTER this
        // method returns. Clearing the context in a finally block inside the
        // transactional boundary would wipe it before flush, causing a spurious
        // TenantContextMissingException and rolling back a successful payment.
        // The caller (controller) clears the context after the transaction completes.
        return response;
    }


    private LocalDateTime parseTransactionTime(String transTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            return LocalDateTime.parse(transTime, formatter);
        } catch (Exception e) {
            System.err.println("Error parsing transaction time: " + transTime);
            return LocalDateTime.now();
        }
    }


    private void saveFailedTransaction(ConfirmationRequest request, String errorMessage) {
        try {
            // Save the raw M-Pesa paybill record even if processing failed
            MpesaPaybill paybillTransaction = new MpesaPaybill();

            paybillTransaction.setTransactionType(request.getTransactionType());
            paybillTransaction.setTransID(request.getTransID());
            paybillTransaction.setTransTime(request.getTransTime());
            paybillTransaction.setTransAmount(request.getTransAmount());
            paybillTransaction.setBusinessShortCode(request.getBusinessShortCode());
            paybillTransaction.setBillRefNumber(request.getBillRefNumber());
            paybillTransaction.setInvoiceNumber(request.getInvoiceNumber());
            paybillTransaction.setOrgAccountBalance(request.getOrgAccountBalance());
            paybillTransaction.setThirdPartyTransID(request.getThirdPartyTransID());
            paybillTransaction.setMSISDN(request.getMSISDN());
            paybillTransaction.setFirstName(request.getFirstName());
            paybillTransaction.setMiddleName(request.getMiddleName());
            paybillTransaction.setLastName(request.getLastName());

             mpesaPaybillRepository.save(paybillTransaction);


            System.err.println("Saved failed transaction with status FAILED: " + request.getTransID());
            System.err.println("Error: " + errorMessage);
        } catch (Exception ex) {
            System.err.println("Could not save failed transaction: " + ex.getMessage());
        }
    }
//    CustomResponse<?>simulateTransaction()





    public String generateToken() {
        String appKeySecret = Credentials.basic(appKeY, appSecret);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(stkAuthUrl)
                .header("Authorization", appKeySecret)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                return jsonResponse.getString("access_token");
            } else {
                throw new Exception("Failed to get access token. Response code: " + response.code());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }




}
