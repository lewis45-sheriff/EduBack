package com.EduePoa.EP.BankIntergration;

import com.EduePoa.EP.BankIntergration.BankRequest.BankRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import org.springframework.web.bind.annotation.RequestParam;

public interface BankService {
    CustomResponse<?> postTransactions(BankRequestDTO bankRequestDTO);
    CustomResponse<?>getTransactions();

}
