package com.peoplefirst.wellbeing.rules;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import org.springframework.stereotype.Component;

@Component
public class HalfDaySickLeaveWellbeingRule {

    public WellbeingSuggestionDto evaluate(LeaveRequest request, User user) {
        if (request.getLeaveType() == LeaveType.SICK && request.isHalfDay()) {
            String sickRoomDetails;
            String loc = user.getBaseLocation() != null ? user.getBaseLocation().toLowerCase() : "";
            if (loc.contains("hyderabad")) {
                sickRoomDetails = "Building 3, 2nd Floor, Room 208 (First Aid & Rest Bay)";
            } else if (loc.contains("san jose")) {
                sickRoomDetails = "Building A, 1st Floor, Room 114 (Wellness Suite)";
            } else {
                // Default Bangalore / general campus
                sickRoomDetails = "Building 2, 3rd Floor, Room 304 (Medical Bay & Resting Room)";
            }

            WellbeingSuggestionDto suggestion = new WellbeingSuggestionDto(
                    "HALF_DAY_SICK_LEAVE_APPLIED",
                    "On-Campus Sick Room Available",
                    "Would you like to rest in the office sick room before heading home? The sick room is quiet, sanitized, and equipped with recliners and first aid: " + sickRoomDetails + ".",
                    "SUGGESTION"
            );
            return suggestion;
        }
        return null;
    }
}
