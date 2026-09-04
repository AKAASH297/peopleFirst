package com.peoplefirst.leave.service;

import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.repository.LeaveBalanceRepository;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveBalanceService(LeaveBalanceRepository leaveBalanceRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    @Transactional
    public List<LeaveBalance> initializeUserBalancesIfAbsent(User user, int year) {
        List<LeaveBalance> existing = leaveBalanceRepository.findByUserIdAndYear(user.getId(), year);
        if (!existing.isEmpty()) {
            return existing;
        }

        List<LeaveBalance> createdList = new ArrayList<>();
        for (LeaveType type : LeaveType.values()) {
            if (type.isEligibleForUser(user.isContractor())) {
                double quota = type.getDefaultQuotaForUser(user.isContractor());
                LeaveBalance balance = new LeaveBalance(user.getId(), type, quota, year);
                createdList.add(leaveBalanceRepository.save(balance));
            }
        }
        return createdList;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalance> getUserBalances(UUID userId, int year) {
        return leaveBalanceRepository.findByUserIdAndYear(userId, year);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalance> getUsersBalances(List<UUID> userIds, int year) {
        return leaveBalanceRepository.findByUserIdInAndYear(userIds, year);
    }

    @Transactional
    public LeaveBalance getOrCreateUserBalance(User user, LeaveType leaveType, int year) {
        return leaveBalanceRepository.findByUserIdAndLeaveTypeAndYear(user.getId(), leaveType, year)
                .orElseGet(() -> {
                    double quota = leaveType.getDefaultQuotaForUser(user.isContractor());
                    LeaveBalance balance = new LeaveBalance(user.getId(), leaveType, quota, year);
                    return leaveBalanceRepository.save(balance);
                });
    }

    @Transactional
    public void reservePendingDays(User user, LeaveType leaveType, double days, int year) {
        LeaveBalance balance = getOrCreateUserBalance(user, leaveType, year);
        if (balance.getRemainingDays() < days) {
            throw new PolicyViolationException("Insufficient leave balance for " + leaveType.getDisplayName() +
                    ". Available remaining: " + balance.getRemainingDays() + ", Requested: " + days);
        }
        balance.setPendingDays(balance.getPendingDays() + days);
        balance.recalculateRemainingDays();
        leaveBalanceRepository.save(balance);
    }

    @Transactional
    public void commitApprovedDays(User user, LeaveType leaveType, double days, int year) {
        LeaveBalance balance = getOrCreateUserBalance(user, leaveType, year);
        balance.setPendingDays(Math.max(0.0, balance.getPendingDays() - days));
        balance.setUsedDays(balance.getUsedDays() + days);
        balance.recalculateRemainingDays();
        leaveBalanceRepository.save(balance);
    }

    @Transactional
    public void releasePendingDays(User user, LeaveType leaveType, double days, int year) {
        LeaveBalance balance = getOrCreateUserBalance(user, leaveType, year);
        balance.setPendingDays(Math.max(0.0, balance.getPendingDays() - days));
        balance.recalculateRemainingDays();
        leaveBalanceRepository.save(balance);
    }

    @Transactional
    public void restoreDaysOnCancel(User user, LeaveType leaveType, double days, boolean wasApproved, int year) {
        LeaveBalance balance = getOrCreateUserBalance(user, leaveType, year);
        if (wasApproved) {
            balance.setUsedDays(Math.max(0.0, balance.getUsedDays() - days));
        } else {
            balance.setPendingDays(Math.max(0.0, balance.getPendingDays() - days));
        }
        balance.recalculateRemainingDays();
        leaveBalanceRepository.save(balance);
    }

    @Transactional
    public void adjustPendingDaysOnEdit(User user, LeaveType oldType, double oldDays,
                                        LeaveType newType, double newDays, int year) {
        // Release old
        LeaveBalance oldBalance = getOrCreateUserBalance(user, oldType, year);
        oldBalance.setPendingDays(Math.max(0.0, oldBalance.getPendingDays() - oldDays));
        oldBalance.recalculateRemainingDays();
        leaveBalanceRepository.save(oldBalance);

        // Reserve new
        LeaveBalance newBalance = getOrCreateUserBalance(user, newType, year);
        if (newBalance.getRemainingDays() < newDays) {
            // Rollback old release
            oldBalance.setPendingDays(oldBalance.getPendingDays() + oldDays);
            oldBalance.recalculateRemainingDays();
            leaveBalanceRepository.save(oldBalance);
            throw new PolicyViolationException("Insufficient leave balance for " + newType.getDisplayName() +
                    ". Available remaining: " + newBalance.getRemainingDays() + ", Requested: " + newDays);
        }
        newBalance.setPendingDays(newBalance.getPendingDays() + newDays);
        newBalance.recalculateRemainingDays();
        leaveBalanceRepository.save(newBalance);
    }
}
