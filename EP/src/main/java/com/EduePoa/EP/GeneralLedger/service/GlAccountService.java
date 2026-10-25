package com.EduePoa.EP.GeneralLedger.service;

import com.EduePoa.EP.GeneralLedger.dto.GlAccountRequest;
import com.EduePoa.EP.GeneralLedger.entity.GlAccount;
import com.EduePoa.EP.GeneralLedger.enums.GlAccountType;
import com.EduePoa.EP.GeneralLedger.enums.GlNormalBalance;
import com.EduePoa.EP.GeneralLedger.repository.GlAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator management of the chart of accounts. Enforces:
 * <ul>
 *   <li>normal balance must match the account type's conventional side;</li>
 *   <li>tenant-scoped unique account codes;</li>
 *   <li>system accounts cannot change code/type/normal-balance, and cannot be
 *       deactivated, because posting rules depend on them.</li>
 * </ul>
 * Client-supplied {@code systemKey} is never accepted (reserved for seeding).
 */
@Service
@RequiredArgsConstructor
public class GlAccountService {

    private final GlAccountRepository accountRepository;

    @Transactional
    public GlAccount create(GlAccountRequest req) {
        validate(req, true);
        if (accountRepository.existsByCode(req.code().trim())) {
            throw new GlException("An account with code " + req.code() + " already exists");
        }
        GlAccount account = GlAccount.builder()
                .code(req.code().trim())
                .name(req.name().trim())
                .accountType(req.accountType())
                .normalBalance(req.normalBalance())
                .active(req.active() == null || req.active())
                .systemAccount(false)
                .systemKey(null)
                .description(req.description())
                .build();
        return accountRepository.save(account);
    }

    @Transactional
    public GlAccount update(Long id, GlAccountRequest req) {
        GlAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new GlException("Account not found: " + id));
        validate(req, false);

        if (account.isSystemAccount()) {
            // Protect postings: a system account's identity and side are immutable.
            if (req.code() != null && !req.code().trim().equals(account.getCode())) {
                throw new GlException("A system account's code cannot be changed");
            }
            if (req.accountType() != null && req.accountType() != account.getAccountType()) {
                throw new GlException("A system account's type cannot be changed");
            }
            if (req.normalBalance() != null && req.normalBalance() != account.getNormalBalance()) {
                throw new GlException("A system account's normal balance cannot be changed");
            }
            if (req.active() != null && !req.active()) {
                throw new GlException("A system account required by posting rules cannot be deactivated");
            }
        } else {
            if (req.code() != null && !req.code().trim().equals(account.getCode())) {
                if (accountRepository.existsByCode(req.code().trim())) {
                    throw new GlException("An account with code " + req.code() + " already exists");
                }
                account.setCode(req.code().trim());
            }
            if (req.accountType() != null) {
                account.setAccountType(req.accountType());
            }
            if (req.normalBalance() != null) {
                account.setNormalBalance(req.normalBalance());
            }
            if (req.active() != null) {
                account.setActive(req.active());
            }
            // Re-validate the type/normal-balance pairing after applying changes.
            validateNormalBalance(account.getAccountType(), account.getNormalBalance());
        }

        if (req.name() != null && !req.name().isBlank()) {
            account.setName(req.name().trim());
        }
        if (req.description() != null) {
            account.setDescription(req.description());
        }
        return accountRepository.save(account);
    }

    private void validate(GlAccountRequest req, boolean creating) {
        if (req == null) {
            throw new GlException("Request body is required");
        }
        if (creating) {
            if (req.code() == null || req.code().isBlank()) {
                throw new GlException("Account code is required");
            }
            if (req.name() == null || req.name().isBlank()) {
                throw new GlException("Account name is required");
            }
            if (req.accountType() == null) {
                throw new GlException("Account type is required");
            }
            if (req.normalBalance() == null) {
                throw new GlException("Normal balance is required");
            }
            validateNormalBalance(req.accountType(), req.normalBalance());
        }
    }

    /**
     * Rejects a normal balance that contradicts the account type's conventional
     * side. (Contra accounts, if ever needed, would require a documented explicit
     * exception; none are seeded.)
     */
    private void validateNormalBalance(GlAccountType type, GlNormalBalance normal) {
        if (type != null && normal != null && type.defaultNormalBalance() != normal) {
            throw new GlException(type + " accounts conventionally carry a "
                    + type.defaultNormalBalance() + " balance, not " + normal
                    + ". Adjust the type or normal balance.");
        }
    }
}
