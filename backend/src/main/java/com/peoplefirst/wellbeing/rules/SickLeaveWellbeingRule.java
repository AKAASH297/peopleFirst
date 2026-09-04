package com.peoplefirst.wellbeing.rules;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.HospitalPartnerDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SickLeaveWellbeingRule {

    public WellbeingSuggestionDto evaluate(LeaveRequest request, User user, List<HospitalPartnerDto> allHospitals) {
        if (request.getLeaveType() != LeaveType.SICK) {
            return null;
        }

        // Filter hospitals by user's base location (or fallback to all if none match)
        String userLocation = user.getBaseLocation() != null ? user.getBaseLocation().trim().toLowerCase() : "";
        List<HospitalPartnerDto> locationHospitals = new ArrayList<>();
        for (HospitalPartnerDto h : allHospitals) {
            if (h.getCity().toLowerCase().contains(userLocation) || userLocation.contains(h.getCity().toLowerCase())) {
                locationHospitals.add(h);
            }
        }
        if (locationHospitals.isEmpty()) {
            locationHospitals = allHospitals;
        }

        WellbeingSuggestionDto suggestion = new WellbeingSuggestionDto(
                "SICK_LEAVE_APPLIED",
                "Health & Medical Care Support (Kura Concierge)",
                "Did you consult a doctor? If yes, please remember to retain and submit your OPD or hospitalization bills within 90 days for corporate insurance reimbursement. You can also visit our network partner hospitals in " + user.getBaseLocation() + " to avail exclusive discounts.",
                "PROMPT"
        );
        suggestion.setActionUrl("https://insurance.peoplefirst.internal/claims");
        suggestion.setPartnerHospitals(locationHospitals);
        return suggestion;
    }
}
