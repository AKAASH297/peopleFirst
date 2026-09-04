package com.peoplefirst.wellbeing.rules;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VolunteeringWellbeingRule {

    public WellbeingSuggestionDto evaluate(LeaveRequest request, User user) {
        if (request.getLeaveType() == LeaveType.VOLUNTEERING) {
            WellbeingSuggestionDto suggestion = new WellbeingSuggestionDto(
                    "VOLUNTEERING_LEAVE_APPLIED",
                    "CSR & Community Impact Initiative",
                    "Thank you for giving back to the community, " + user.getFullName() + "! Would you like to join one of our active corporate volunteering chapters (Green Earth Afforestation, Tech Literacy for Youth, or Animal Welfare Network)? We'd also love to feature your volunteering story on the company intranet banner.",
                    "SUGGESTION"
            );
            suggestion.setGroupSuggestions(List.of(
                    "Green Earth Afforestation Drive",
                    "Code & Tech Literacy for Underprivileged Youth",
                    "Community Food Bank & Kitchen Support",
                    "Paws & Care Animal Rescue"
            ));
            suggestion.setActionUrl("https://csr.peoplefirst.internal/enroll");
            return suggestion;
        }
        return null;
    }
}
