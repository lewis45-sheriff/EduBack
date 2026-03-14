package com.EduePoa.EP.Procurement.SupplierDashboard;

import com.EduePoa.EP.Utils.CustomResponse;

public interface SupplierDashboardService {
    CustomResponse<?> getSummary(Long supplierId);
}
