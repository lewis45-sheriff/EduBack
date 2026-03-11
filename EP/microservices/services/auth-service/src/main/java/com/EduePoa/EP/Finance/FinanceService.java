package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Utils.CustomResponse;

public interface FinanceService {

CustomResponse<?>getStudentsWithBalances();
CustomResponse<?> getStudentsWithBalancePerStudent(Long studentId);
}
