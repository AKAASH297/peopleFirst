package com.peoplefirst.wellbeing.rules;

import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class StressExpressionWellbeingRule {

    private static final List<String> STRESS_KEYWORDS = Arrays.asList(
            "stress", "stressed", "pressure", "exhausted", "burnout", "overwhelmed",
            "tired", "fatigue", "anxiety", "anxious", "hectic", "drained"
    );

    public boolean matches(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase();
        return STRESS_KEYWORDS.stream().anyMatch(lower::contains);
    }

    public WellbeingSuggestionDto evaluate(String message) {
        if (!matches(message)) {
            return null;
        }

        WellbeingSuggestionDto suggestion = new WellbeingSuggestionDto(
                "STRESS_EXPRESSION_DETECTED",
                "Take a Breath: Workplace Wellness Amenities",
                "I sense you might be experiencing heavy pressure or fatigue. Your wellbeing comes first! Would you like to recharge with a 30-minute recliner massage chair slot (Building 1, 4th Floor) or unwind with table tennis, snooker, or carrom in our Recreational Lounge? We also have our On-Site Psychologist and Yoga sessions available today.",
                "SUGGESTION"
        );
        suggestion.setActionUrl("/amenities");
        return suggestion;
    }
}
