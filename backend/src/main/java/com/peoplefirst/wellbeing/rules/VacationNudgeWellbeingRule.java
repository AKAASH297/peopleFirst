package com.peoplefirst.wellbeing.rules;

import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.ResortPartnerDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VacationNudgeWellbeingRule {

    public WellbeingSuggestionDto evaluate(User user, boolean hasTakenLeaveInLastQuarter, List<ResortPartnerDto> partnerResorts) {
        if (!hasTakenLeaveInLastQuarter) {
            WellbeingSuggestionDto suggestion = new WellbeingSuggestionDto(
                    "NO_LEAVE_LAST_QUARTER",
                    "Time for a Well-Deserved Break!",
                    "Hi " + user.getFullName() + ", we noticed you haven't taken any time off in the last 90 days. Taking regular breaks is vital for mental rejuvenation and long-term energy. Check out our company-tied partner resorts and getaways offering up to 25% corporate discounts!",
                    "NUDGE"
            );
            suggestion.setPartnerResorts(partnerResorts);
            return suggestion;
        }
        return null;
    }
}
