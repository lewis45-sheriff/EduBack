package com.EduePoa.EP.MPesa;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
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
import com.google.gson.Gson;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.*;
import java.math.BigDecimal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service

public class MpesaServiceImpl implements MpesaServiceInterface {
    @Autowired
    private MpesaTransactionRepository mpesaTransactionRepository;
    @Autowired
    private StudentRepository mpesaStudentRepository;
    @Autowired
    private FinanceRepository financeRepository;
    @Autowired
    private FinanceTransactionRepository financeTransactionRepository;
    @Autowired
    private StudentInvoicesRepository studentInvoicesRepository;
    @Autowired
    private FinanceTransactionService financeTransactionService;
    @Autowired
    private AuditService auditService;

    @Value("${mpesa.app.key}")
    private String appKeY;

    @Value("${mpesa.app.secret}")
    private String appSecret;

    @Value("${mpesa.token.url}")
    private String stkAuthUrl;

    @Value("${mpesa.stk.url}")
    private String stkUrl;

    @Value("${mpesa.stk.transactionType}")
    private String transactionType;

    @Value("${mpesa.stk.password}")
    private String password;

    @Value("${mpesa.stk.shortCode}")
    private String shortCode;

    @Value("${mpesa.stk.callbackURL}")
    private String callBackUrl;

    @Value("${mpesa.b2c.url}")
    private String b2cUrl;

    @Value("${mpesa.b2c.securityCredential}")
    private String securityCredential;
    @Value("${mpesa.b2c.initiatorPassword}")
    private String initiatorPassword;

    @Value("${mpesa.b2c.commandId}")
    private String b2cCommandId;

    @Value("${mpesa.b2c.queTimeOutURL}")
    private String queTimeOutURL;

    @Value("${mpesa.b2c.callBackURL}")
    private String b2cResultUrl;

    @Value("${mpesa.b2c.shortCode}")
    private String b2cShortCode;

    @Value("${mpesa.b2c.initiatorName}")

    private String initiatorName;
    private String registerendpont = "https://sandbox.safaricom.co.ke/mpesa/c2b/v1/registerurl";
    private String simulateEndpoint = "https://sandbox.safaricom.co.ke/mpesa/c2b/v1/simulate";
    @Value("${mpesa.base.url}")
    private String baseUrl;

    Gson gson = new Gson();
    // private final RestTemplate restTemplate;
    //
    // public MpesaServiceImpl(RestTemplate restTemplate) {
    // this.restTemplate = restTemplate;
    // }

    // Generate M-Pesa Access Token

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

    // @Override
    // public MpesaPaymentResponseDTO initiateSTKPush(String phoneNumber, Double
    // amount, Long accountReference) {
    // return null;
    // }
    public MpesaPaymentResponseDTO initiateSTKPushForMaany(@NotNull String phoneNumber, @NotNull Double amount) {
        MpesaPaymentResponseDTO responseDTO = new MpesaPaymentResponseDTO();
        try {
            // Create HTTP client
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .connectTimeout(1000, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build();
            MediaType mediaType = MediaType.parse("application/json");

            // Log initial information
            log.info(String.format("Short code: %s", shortCode));

            // Prepare request body
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            JSONObject requestBody = createRequestBody(amount, phoneNumber, timestamp);

            // Log the request body
            log.info(String.format("Initiate STK Push Request Body: %s", requestBody));

            // Convert to request body and initiate API request
            String requestJson = new JSONArray().put(requestBody).toString().replaceAll("[\\[\\]]", "");
            RequestBody body = RequestBody.create(mediaType, requestJson);
            String token = generateToken();
            log.info(String.format("Token - %s", "Bearer " + token));

            Request request = new Request.Builder()
                    .url(stkUrl)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", String.format("Bearer %s", token))
                    .build();

            // Execute the request
            Response response = client.newCall(request).execute();

            // Log and parse the response
            log.info("{ Mpesa Service } {Initiate STK Push} { Successful }");
            assert response.body() != null;
            responseDTO = gson.fromJson(response.body().string(), MpesaPaymentResponseDTO.class);

        } catch (Exception e) {
            // Log and populate error details
            log.error("{ Mpesa Service } {Initiate STK Push} { ERROR } - {}", e.getMessage());
            responseDTO.setCheckoutRequestID(null);
            responseDTO.setCustomerMessage("ERROR");
            responseDTO.setResponseDescription(e.getLocalizedMessage());
        }
        auditService.log("MPESA", "Initiated STK Push for phone:", phoneNumber, "amount:", String.valueOf(amount));

        return responseDTO;
    }

    // public MpesaPaymentResponseDTO initiateSTKPush(@NotNull String phoneNumber,
    // @NotNull Double amount, Long accountReference) {
    // MpesaPaymentResponseDTO responseDTO = new MpesaPaymentResponseDTO();
    // try {
    //// Term term = termRepository.findTermById(termId).
    //// orElseThrow(()->new IllegalArgumentException("term with id not found"+
    // termId));
    ////
    //// System.out.println("this is "+ term.getTermName());
    // Long currentSchoolId = JwtUtils.getCurrentUserSchoolId();
    //
    //
    // String formattedPhone = formatPhone(phoneNumber);
    //
    //
    //
    // OkHttpClient client = new OkHttpClient().newBuilder()
    // .connectTimeout(1000, TimeUnit.SECONDS)
    // .readTimeout(300, TimeUnit.SECONDS)
    // .build();
    // MediaType mediaType = MediaType.parse("application/json");
    //
    // // Log initial information
    // log.info( String.format("Short code: %s", shortCode));
    //
    // // Prepare request body
    // String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new
    // java.util.Date());
    // JSONObject requestBody = createRequestBody(amount, phoneNumber, timestamp );
    //
    // // Log the request body
    // log.info(String.format("Initiate STK Push Request Body: %s", requestBody));
    //
    // // Convert to request body and initiate API request
    // String requestJson = new
    // JSONArray().put(requestBody).toString().replaceAll("[\\[\\]]", "");
    // RequestBody body = RequestBody.create(mediaType, requestJson);
    // String token = generateToken();
    // log.info( String.format("Token - %s", "Bearer " + token));
    //
    // Request request = new Request.Builder()
    // .url(stkUrl)
    // .method("POST", body)
    // .addHeader("Content-Type", "application/json")
    // .addHeader("Authorization", String.format("Bearer %s", token))
    // .build();
    //
    // Response response = client.newCall(request).execute();
    // log.info("{ Mpesa Service } {Initiate STK Push} { Successful }");
    //
    // // Parse the response and map to DTO
    // assert response.body() != null;
    // responseDTO = gson.fromJson(response.body().string(),
    // MpesaPaymentResponseDTO.class);
    // LocalDate currentDate = LocalDate.now();
    // Term activeTerm =
    // termRepository.findByStartMonthLessThanEqualAndEndMonthGreaterThanEqualAndSchool_Id(currentDate,
    // currentDate,currentSchoolId)
    // .orElseThrow(() -> new RuntimeException("No active term found for the current
    // date"));
    ////
    //
    // // Create and populate MpesaSTKTransaction
    // AtomicReference<MpesaSTKTransactions> transaction = new AtomicReference<>(new
    // MpesaSTKTransactions());
    // Student student = mpesaStudentRepository
    // .findById(accountReference)
    // .orElseThrow(() -> new IllegalArgumentException("Student not found with
    // admission number: " + accountReference));
    // transaction.get().setResponseCode(responseDTO.getResponseCode());
    // transaction.get().setMerchantRequestID(responseDTO.getMerchantRequestID());
    // transaction.get().setCheckoutRequestID(responseDTO.getCheckoutRequestID());
    // transaction.get().setCustomerMessage(responseDTO.getCustomerMessage());
    // transaction.get().setResponseDescription(responseDTO.getResponseDescription());
    // transaction.get().setPhoneNumber(phoneNumber);
    // transaction.get().setAccountReference(student);
    // transaction.get().setAmount(0.0);
    //
    // try {
    // mpesaTransactionRepository.save(transaction.get());
    // } catch (Exception e) {
    // log.error("Error saving MpesaSTKTransaction: " + e.getMessage(), e);
    // }
    //
    // // Create and populate FeePayment
    // AtomicReference<FeePayments> feePayment = new AtomicReference<>(new
    // FeePayments());
    //
    // FeePayments payment = feePayment.get();
    // payment.setAccountReference(student);
    // payment.setMerchantRequestID(responseDTO.getMerchantRequestID());
    // payment.setTransactionType("STK PUSH");
    // payment.setPhoneNumber(phoneNumber);
    // payment.setPaymentMode("M-Pesa");
    // payment.setPaymentDate(LocalDate.now());
    // payment.setAmount(0.0);
    // payment.setTermId(activeTerm.getId());
    //
    // School school = schoolRepository.findById(currentSchoolId)
    // .orElseThrow(() -> new EntityNotFoundException("School not found with ID: " +
    // currentSchoolId));
    // payment.setSchool(school);
    //
    //// Save transaction history
    // // Save transaction history
    // TransactionHistory transactionHistory = new TransactionHistory();
    // transactionHistory.setStudent(student); // Ensure student reference is set
    // transactionHistory.setReceiptNumber(responseDTO.getMerchantRequestID());
    // transactionHistoryRepository.save(transactionHistory);
    //
    // try {
    // feePaymentRepository.save(feePayment.get());
    //
    // } catch (Exception e) {
    // log.error("Error saving FeePayments: " + e.getMessage(), e);
    // }
    //
    //
    //
    // // Compute and save student balance
    // // Check balances from both previous terms and active term
    // Optional<StudentPaymentHistory> studentPaymentHistoryOpt =
    // studentPaymentHistoryRepository.findByStudentAndTermAndGrade(student,
    // activeTerm, student.getGrade());
    //
    // if (studentPaymentHistoryOpt.isPresent()) {
    // StudentPaymentHistory studentPaymentHistory = studentPaymentHistoryOpt.get();
    //
    // // Fetch the total paid amount
    // BigDecimal totalPaid =
    // transactionHistoryRepository.getTotalPaidAmountByStudentAndTermAndGrade(student.getId(),
    // activeTerm.getId(),student.getGrade().getId());
    //
    // // Set the total paid amount
    // studentPaymentHistory.setTermTotalFeePaid(totalPaid);
    //
    // // Save the updated history
    // studentPaymentHistoryRepository.save(studentPaymentHistory);
    // auditService.logAction("POST", "SACCO TRANSACTIONS ", studentPaymentHistory,
    // "Student payment history updated successfully.");
    // } else {
    // // Handle the case where no payment history exists for the student and term
    // log.warn("No existing StudentPaymentHistory found for student {} in term {}",
    // student.getId(), activeTerm.getId());
    // }
    //
    //
    //
    //
    // } catch (Exception e) {
    // log.info("{ Mpesa Service } {Initiate STK Push} { ERROR } - " +
    // e.getMessage());
    // responseDTO.setCheckoutRequestID(null);
    // responseDTO.setCustomerMessage("ERROR");
    // responseDTO.setResponseDescription(e.getLocalizedMessage());
    // }
    //
    //
    // return responseDTO;
    // }
    public MpesaPaymentResponseDTO initiateSTKPush(@NotNull String phoneNumber, @NotNull Double amount,
            Long accountReference) {
        MpesaPaymentResponseDTO responseDTO = new MpesaPaymentResponseDTO();

        try {
            String formattedPhone = formatPhone(phoneNumber);
            OkHttpClient client = getUnsafeOkHttpClient();

            MediaType mediaType = MediaType.parse("application/json");
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            JSONObject requestBody = createRequestBody(amount, formattedPhone, timestamp);

            log.info("Short code: {}", shortCode);
            log.info("Initiate STK Push Request Body: {}", requestBody);

            // Convert request body to JSON
            String requestJson = new JSONArray().put(requestBody).toString().replaceAll("[\\[\\]]", "");
            RequestBody body = RequestBody.create(mediaType, requestJson);

            // Generate token and initiate API request
            String token = generateToken();
            log.info("Token - Bearer {}", token);

            Request request = new Request.Builder()
                    .url(stkUrl)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + token)
                    .build();

            Response response = client.newCall(request).execute();
            log.info("{ Mpesa Service } {Initiate STK Push} { Successful }");

            if (response.body() == null) {
                throw new IllegalStateException("Empty response from M-Pesa API");
            }

            // Parse response and map to DTO
            responseDTO = gson.fromJson(response.body().string(), MpesaPaymentResponseDTO.class);

            // Find active term (you'll need to convert this to Term enum)
            Term activeTerm = Term.getCurrentTerm(); // Or however you determine current term

            // Retrieve student record
            Student student = mpesaStudentRepository.findById(accountReference)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Student not found with admission number: " + accountReference));

            // Save M-Pesa transaction (keep as is if MpesaSTKTransactions hasn't changed)
            MpesaSTKTransactions transaction = new MpesaSTKTransactions();
            transaction.setResponseCode(responseDTO.getResponseCode());
            transaction.setMerchantRequestID(responseDTO.getMerchantRequestID());
            transaction.setCheckoutRequestID(responseDTO.getCheckoutRequestID());
            transaction.setCustomerMessage(responseDTO.getCustomerMessage());
            transaction.setResponseDescription(responseDTO.getResponseDescription());
            transaction.setPhoneNumber(formattedPhone);
            transaction.setAccountReference(student);
            transaction.setAmount(amount);

            try {
                mpesaTransactionRepository.save(transaction);
            } catch (Exception e) {
                log.error("Error saving MpesaSTKTransaction: {}", e.getMessage(), e);
            }

            // Save fee payment using NEW FinanceTransaction structure
            FinanceTransaction payment = new FinanceTransaction();
            payment.setStudentId(student.getId());
            payment.setStudentName(student.getFirstName().concat(student.getLastName())); // Assuming Student has
                                                                                          // getName()
            payment.setAdmissionNumber(student.getAdmissionNumber()); // Assuming this exists
            payment.setTransactionType(FinanceTransaction.TransactionType.INCOME);
            payment.setCategory("Fee Payment"); // Or "STK PUSH" or however you categorize
            payment.setAmount(BigDecimal.valueOf(amount));
            payment.setTransactionDate(LocalDate.now());
            payment.setDescription(
                    "M-Pesa STK Push Payment - MerchantRequestID: " + responseDTO.getMerchantRequestID());
            payment.setPaymentMethod(FinanceTransaction.PaymentMethod.MPESA);
            payment.setYear(Year.now());

            payment.setInvoiceId(null); // Set if you have an invoice
            // Note: reference will be auto-generated by @PrePersist

            try {
                financeTransactionRepository.save(payment);
                // auditService.logAction("POST", "FEE PAYMENT", payment, "Fee payment posted
                // successfully");
            } catch (Exception e) {
                log.error("Error saving FinanceTransaction: {}", e.getMessage(), e);
            }

            // Update or create Finance record (summary)
            Optional<Finance> financeOpt = financeRepository.findByStudentIdAndTermAndYear(
                    student.getId(), activeTerm, Year.now());

            Finance finance;
            if (financeOpt.isPresent()) {
                finance = financeOpt.get();
            } else {
                finance = new Finance();
                finance.setStudentId(student.getId());
                finance.setTerm(activeTerm);
                finance.setYear(Year.now());
                finance.setTotalFeeAmount(BigDecimal.ZERO); // Set from fee structure
                finance.setPaidAmount(BigDecimal.ZERO);
                finance.setBalance(BigDecimal.ZERO);
            }

            // Update paid amount
            BigDecimal currentPaid = finance.getPaidAmount();
            finance.setPaidAmount(currentPaid.add(BigDecimal.valueOf(amount)));

            // Recalculate balance
            finance.setBalance(finance.getTotalFeeAmount().subtract(finance.getPaidAmount()));
            finance.setLastUpdated(LocalDateTime.now());

            try {
                financeRepository.save(finance);
                // auditService.logAction("POST", "FINANCE", finance, "Finance record updated
                // successfully");
            } catch (Exception e) {
                log.error("Error saving Finance: {}", e.getMessage(), e);
            }

            auditService.log("MPESA", "STK Push initiated for student:", student.getAdmissionNumber(), "amount:",
                    String.valueOf(amount));

        } catch (Exception e) {
            log.error("{ Mpesa Service } {Initiate STK Push} { ERROR } - {}", e.getMessage(), e);
            responseDTO.setCheckoutRequestID(null);
            responseDTO.setCustomerMessage("ERROR");
            responseDTO.setResponseDescription(e.getLocalizedMessage());
        }

        return responseDTO;
    }

    private JSONObject createRequestBody(Double amount, String phoneNumber, String timestamp) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("BusinessShortCode", shortCode);
        requestBody.put("Password", generatePassword(shortCode, password, timestamp));
        requestBody.put("Timestamp", timestamp);
        requestBody.put("Amount", amount);
        requestBody.put("TransactionType", transactionType);
        requestBody.put("PartyA", phoneNumber);
        requestBody.put("PartyB", shortCode);
        requestBody.put("PhoneNumber", phoneNumber);
        requestBody.put("CallBackURL", callBackUrl);
        requestBody.put("AccountReference", "SCHOOLFEE");
        requestBody.put("TransactionDesc", "School Fee Payment");
        return requestBody;
    }

    public String generatePassword(String shortCode, String passkey, String timeStamp) {
        String credentials = shortCode + passkey + timeStamp;
        return Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    public static String formatPhone(String phone) {
        if (phone.startsWith("0")) {
            log.info("Starting with 0");
            phone = phone.replaceFirst("0", "254");
        } else if (phone.startsWith("+")) {
            log.info("Starting with +");
            phone = phone.substring(1);
        } else if (phone.startsWith("7") || phone.startsWith("1")) {
            phone = "254" + phone;
        }
        return phone;
    }

    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[] {};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            builder.connectTimeout(1000, TimeUnit.SECONDS);
            builder.readTimeout(300, TimeUnit.SECONDS);

            return builder.build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public CustomResponse<?> processCallback(Object object) {
        CustomResponse<Object> response = new CustomResponse<>();

        try {
            log.info("STK Callback received at {}", new Date());
            MpesaCallbackData callbackData = extractCallbackData(object);

            if (callbackData.merchantRequestId == null || callbackData.merchantRequestId.isBlank()
                    || callbackData.resultCode == null || callbackData.resultCode.isBlank()) {
                response.setMessage("Invalid callback response: missing merchant request ID or result code");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(null);
                return response;
            }

            Optional<MpesaSTKTransactions> txnOpt = mpesaTransactionRepository
                    .findByMerchantRequestID(callbackData.merchantRequestId);

            if (txnOpt.isEmpty()) {
                log.warn("M-Pesa callback received for unknown MerchantRequestID: {}", callbackData.merchantRequestId);
                response.setMessage("Callback received but transaction not found for reconciliation");
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(null);
                return response;
            }

            MpesaSTKTransactions mpesaTxn = txnOpt.get();
            Student student = mpesaTxn.getAccountReference();

            updateMpesaCallbackFields(mpesaTxn, callbackData);
            mpesaTransactionRepository.save(mpesaTxn);

            if (!"0".equals(callbackData.resultCode.trim())) {
                response.setMessage("M-Pesa transaction failed: " + callbackData.resultDesc);
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            if (student == null) {
                log.warn("M-Pesa payment received without student link. MerchantRequestID: {}",
                        callbackData.merchantRequestId);
                response.setMessage("Payment received but no student linked to this request");
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            BigDecimal amount = getCallbackAmount(callbackData, mpesaTxn);
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                response.setMessage("Invalid callback amount");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            String receiptNumber = safeTrim(callbackData.mpesaCode);
            if (receiptNumber == null || receiptNumber.isBlank()) {
                response.setMessage("Missing M-Pesa receipt number");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            if (financeTransactionRepository.existsByReference(receiptNumber)) {
                response.setMessage("Callback already processed for this receipt");
                response.setStatusCode(HttpStatus.OK.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            Term currentTerm = Term.getCurrentTerm();
            if (currentTerm == null) {
                throw new IllegalStateException("No active term found for current date");
            }
            Year currentYear = Year.now();

            Optional<StudentInvoices> invoiceOpt = studentInvoicesRepository
                    .findByStudentAndTermAndAcademicYear(student, currentTerm, currentYear);

            if (invoiceOpt.isEmpty()) {
                log.warn("No invoice found for student {} in current term", student.getAdmissionNumber());
                response.setMessage(String.format(
                        "Payment received but no invoice found for current term. Contact admin with reference: %s",
                        receiptNumber));
                response.setStatusCode(HttpStatus.ACCEPTED.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            CreateTransactionDTO transactionDTO = createMpesaTransactionDTO(
                    callbackData,
                    invoiceOpt.get(),
                    currentTerm,
                    currentYear,
                    amount,
                    receiptNumber);

            CustomResponse<?> transactionResponse = financeTransactionService.createTransaction(
                    student.getId(),
                    transactionDTO);

            if (transactionResponse.getStatusCode() != HttpStatus.CREATED.value()) {
                response.setMessage("Payment saved but failed to update finance records: "
                        + transactionResponse.getMessage());
                response.setStatusCode(HttpStatus.PARTIAL_CONTENT.value());
                response.setEntity(mpesaTxn);
                return response;
            }

            Map<String, Object> transactionData = (Map<String, Object>) transactionResponse.getEntity();
            Finance updatedFinance = (Finance) transactionData.get("finance");

            response.setMessage(String.format(
                    "M-Pesa payment processed successfully. New balance: KES %.2f",
                    updatedFinance.getBalance()));
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(mpesaTxn);

            auditService.log("MPESA", "Processed callback for receipt:", receiptNumber, "student:",
                    student.getAdmissionNumber(), "amount:", amount.toPlainString());
        } catch (IllegalArgumentException e) {
            log.error("M-Pesa callback validation error: {}", e.getMessage(), e);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setEntity(null);
        } catch (Exception e) {
            log.error("Error processing STK callback: {}", e.getMessage(), e);
            response.setMessage("Error processing M-Pesa payment: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    private CreateTransactionDTO createMpesaTransactionDTO(
            MpesaCallbackData callbackData,
            StudentInvoices invoice,
            Term term,
            Year year,
            BigDecimal amount,
            String receiptNumber) {
        CreateTransactionDTO transactionDTO = new CreateTransactionDTO();
        transactionDTO.setTransactionType(FinanceTransaction.TransactionType.INCOME);
        transactionDTO.setAmount(amount);
        transactionDTO.setCategory("School Fees Payment");
        transactionDTO.setPaymentMethod(FinanceTransaction.PaymentMethod.MPESA);
        transactionDTO.setReference(receiptNumber);
        transactionDTO.setInvoiceId(invoice.getId());
        transactionDTO.setTerm(term);
        transactionDTO.setYear(year);
        transactionDTO.setTransactionDate(parseMpesaTransactionDate(callbackData.transactionDate));
        transactionDTO.setDescription(String.format(
                "M-Pesa payment - Receipt: %s, MerchantRequestID: %s, Phone: %s",
                receiptNumber,
                callbackData.merchantRequestId,
                safeTrim(callbackData.phoneNumber)));
        return transactionDTO;
    }

    private void updateMpesaCallbackFields(MpesaSTKTransactions transaction, MpesaCallbackData callbackData) {
        if (callbackData.amount != null && callbackData.amount > 0) {
            transaction.setAmount(callbackData.amount);
        }
        String phone = safeTrim(callbackData.phoneNumber);
        if (phone != null && !phone.isBlank()) {
            transaction.setPhoneNumber(phone);
        }
        transaction.setResponseCode(safeTrim(callbackData.resultCode));
        transaction.setResponseDescription(safeTrim(callbackData.resultDesc));
        transaction.setCustomerMessage("Callback processed with result code: " + safeTrim(callbackData.resultCode));
    }

    private BigDecimal getCallbackAmount(MpesaCallbackData callbackData, MpesaSTKTransactions transaction) {
        if (callbackData.amount != null && callbackData.amount > 0) {
            return BigDecimal.valueOf(callbackData.amount);
        }
        if (transaction.getAmount() != null && transaction.getAmount() > 0) {
            return BigDecimal.valueOf(transaction.getAmount());
        }
        return null;
    }

    private LocalDate parseMpesaTransactionDate(Timestamp transactionDate) {
        if (transactionDate == null) {
            return LocalDate.now();
        }
        return transactionDate.toLocalDateTime().toLocalDate();
    }

    private MpesaCallbackData extractCallbackData(Object object) throws Exception {
        JSONObject callbackJson = new JSONObject(gson.toJson(object));
        MpesaCallbackData data = new MpesaCallbackData();

        if (!callbackJson.has("Body")) {
            return data;
        }

        JSONObject body = callbackJson.getJSONObject("Body");
        if (!body.has("stkCallback")) {
            return data;
        }

        JSONObject stkCallback = body.getJSONObject("stkCallback");
        data.resultCode = stkCallback.optString("ResultCode", "");
        data.resultDesc = stkCallback.optString("ResultDesc", "");
        data.merchantRequestId = stkCallback.optString("MerchantRequestID", "");

        if (!stkCallback.has("CallbackMetadata")) {
            return data;
        }

        JSONObject callbackMetadata = stkCallback.getJSONObject("CallbackMetadata");
        if (!callbackMetadata.has("Item")) {
            return data;
        }

        JSONArray items = callbackMetadata.getJSONArray("Item");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.optString("Name", "");

            switch (name) {
                case "Amount":
                    if (item.has("Value")) {
                        data.amount = item.optDouble("Value", 0.0);
                    }
                    break;
                case "MpesaReceiptNumber":
                    data.mpesaCode = item.optString("Value", "");
                    break;
                case "TransactionDate":
                    long rawDate = item.optLong("Value", 0L);
                    String dateString = String.valueOf(rawDate);
                    if (dateString.length() == 14) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                        data.transactionDate = new Timestamp(dateFormat.parse(dateString).getTime());
                    }
                    break;
                case "PhoneNumber":
                    data.phoneNumber = item.optString("Value", "");
                    break;
                default:
                    break;
            }
        }

        log.info("M-Pesa callback parsed: resultCode={}, merchantRequestId={}, receipt={}, amount={}",
                data.resultCode,
                data.merchantRequestId,
                data.mpesaCode,
                data.amount);
        return data;
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private static class MpesaCallbackData {
        private String resultCode;
        private String resultDesc;
        private String mpesaCode;
        private String merchantRequestId;
        private Timestamp transactionDate;
        private String phoneNumber;
        private Double amount;
    }
     public Object registerURLs() {

     try{
     String access_token = generateToken();
     String endpoint = baseUrl+"/transactions/c2b";
     JSONObject jsonObject = new JSONObject();
     jsonObject.put("ShortCode",shortCode);
     jsonObject.put("ResponseType","Completed");
     jsonObject.put("ConfirmationURL",endpoint+"/confirmation");
     jsonObject.put("ValidationURL",endpoint+"/validation");

     JSONArray jsonArray = new JSONArray();

     String requestJson =
     jsonArray.put(jsonObject).toString().replaceAll("[\\[\\]]", "");
     MediaType mediaType = MediaType.parse("application/json");
     System.out.println(requestJson);

     RequestBody requestBody = RequestBody.create(mediaType,requestJson);
     Request request = new Request.Builder()
     .url(registerendpont)
     .method("POST", requestBody)
     .addHeader("Content-Type", "application/json")
     .addHeader("Authorization", String.format("Bearer" + " " + "%s",
     access_token))
     .build();

     OkHttpClient httpClient = new OkHttpClient();
     Response response1 = httpClient.newCall(request).execute();

     assert response1.body() != null;
     if (response1.isSuccessful()){
     log.info("{ Mpesa Service } {register URLs } { Successful }");
     return response1.body().string();
     }else {
     log.info("{ Mpesa Service } {register URLs } { Failed }");
     return null;
     }

     }catch (Exception e){
     return e.getMessage();
     }
     }


     // Parse Access Token from Response
     private String parseAccessToken(String responseBody) {
     // Implement a proper JSON parsing mechanism (e.g., using Jackson or Gson)
     // This is a placeholder and should be replaced with actual logic
     return "access_token_placeholder";
     }
}
