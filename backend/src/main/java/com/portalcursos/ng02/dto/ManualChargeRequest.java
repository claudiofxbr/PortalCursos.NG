package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentCategory;
import com.portalcursos.ng02.model.ESecretaryProcessType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo de {@code POST /api/finance/charge} e {@code PUT /api/finance/invoices/{id}}.
 * Movido de dentro de {@code FinancialController} (Lote E, item F2 da auditoria) para
 * poder ser usado também por {@code PaymentService}.
 */
public class ManualChargeRequest {
    private BigDecimal amount;
    private LocalDate dueDate;
    private Long studentId;
    private EAcademicLevel academicLevel;
    private EPaymentCategory category;
    private ESecretaryProcessType secretaryProcessType;
    private String description;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public EAcademicLevel getAcademicLevel() { return academicLevel; }
    public void setAcademicLevel(EAcademicLevel academicLevel) { this.academicLevel = academicLevel; }
    public EPaymentCategory getCategory() { return category; }
    public void setCategory(EPaymentCategory category) { this.category = category; }
    public ESecretaryProcessType getSecretaryProcessType() { return secretaryProcessType; }
    public void setSecretaryProcessType(ESecretaryProcessType secretaryProcessType) { this.secretaryProcessType = secretaryProcessType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
