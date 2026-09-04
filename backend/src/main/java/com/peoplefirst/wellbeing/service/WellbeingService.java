package com.peoplefirst.wellbeing.service;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.AmenityDto;
import com.peoplefirst.wellbeing.dto.HospitalPartnerDto;
import com.peoplefirst.wellbeing.dto.ResortPartnerDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import com.peoplefirst.wellbeing.rules.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WellbeingService {

    private final SickLeaveWellbeingRule sickLeaveRule;
    private final HalfDaySickLeaveWellbeingRule halfDaySickLeaveRule;
    private final StressExpressionWellbeingRule stressRule;
    private final VacationNudgeWellbeingRule vacationNudgeRule;
    private final VolunteeringWellbeingRule volunteeringRule;

    private final List<AmenityDto> amenities = new ArrayList<>();
    private final List<HospitalPartnerDto> hospitals = new ArrayList<>();
    private final List<ResortPartnerDto> resorts = new ArrayList<>();

    public WellbeingService(SickLeaveWellbeingRule sickLeaveRule,
                            HalfDaySickLeaveWellbeingRule halfDaySickLeaveRule,
                            StressExpressionWellbeingRule stressRule,
                            VacationNudgeWellbeingRule vacationNudgeRule,
                            VolunteeringWellbeingRule volunteeringRule) {
        this.sickLeaveRule = sickLeaveRule;
        this.halfDaySickLeaveRule = halfDaySickLeaveRule;
        this.stressRule = stressRule;
        this.vacationNudgeRule = vacationNudgeRule;
        this.volunteeringRule = volunteeringRule;
        initializeCatalogs();
    }

    private void initializeCatalogs() {
        // Amenities per SPEC.md §6
        amenities.add(new AmenityDto("amenity-1", "On-Campus Gymnasium & Fitness Hub", "Fitness", "6:00 AM - 10:00 PM", "Building 1, Basement Level", "State-of-the-art cardio and weight training facility with certified trainers."));
        amenities.add(new AmenityDto("amenity-2", "On-Site General Physician (GP)", "Healthcare", "9:00 AM - 5:00 PM (Mon-Fri)", "Building 2, 1st Floor, Health Center", "Full-time licensed GP for general medical consultations and basic prescriptions."));
        amenities.add(new AmenityDto("amenity-3", "On-Site Psychologist & Counseling", "Mental Wellbeing", "10:00 AM - 6:00 PM (By Appt)", "Building 2, 2nd Floor, Quiet Zone", "Confidential one-on-one psychological support and stress counseling sessions."));
        amenities.add(new AmenityDto("amenity-4", "Over-Call Legal Advisor", "Advisory", "24/7 Hotline", "Phone / Virtual Consultation", "Confidential legal guidance for civil, real-estate, and personal advisory."));
        amenities.add(new AmenityDto("amenity-5", "Comprehensive Health & Term Insurance", "Benefits", "Continuous Coverage", "Corporate Policy Group A", "Family floater medical insurance cover up to ₹10,00,000 with 90-day claim window."));
        amenities.add(new AmenityDto("amenity-6", "On-Site Yoga Studio", "Fitness & Mindfulness", "7:00 AM - 8:30 AM & 5:30 PM - 7:00 PM", "Building 1, 5th Floor Terrace Hall", "Guided guided Hatha and Ashtanga yoga sessions for physical and mental calm."));
        amenities.add(new AmenityDto("amenity-7", "On-Site Zumba Studio", "Recreation & Dance", "Tuesday & Thursday 6:00 PM - 7:00 PM", "Building 1, 5th Floor Terrace Hall", "High-energy dance cardio sessions led by certified Zumba instructors."));
        amenities.add(new AmenityDto("amenity-8", "Recreational Lounge", "Recreation", "Open 24/7", "Building 3, 3rd Floor", "Table tennis tables, professional snooker tables, chess tables, and carrom boards."));
        amenities.add(new AmenityDto("amenity-9", "Zero-Gravity Recliner Massage Chairs", "Relaxation", "8:00 AM - 8:00 PM", "Building 1, 4th Floor Relaxation Pod", "Acoustic-dampened room with heated ergonomic zero-gravity massage recliners."));

        // Hospital partners per SPEC.md §6
        hospitals.add(new HospitalPartnerDto("Apollo Hospitals Bannerghatta", "Bangalore", "154/11, Opp. IIMB, Bannerghatta Road", "20% Discount on OPD Consultations", "15% Discount on Diagnostics & Pathology", "+91-80-2630-4050"));
        hospitals.add(new HospitalPartnerDto("Manipal Hospital HAL Airport Road", "Bangalore", "98, HAL Old Airport Rd, Kodihalli", "20% Discount on OPD Consultations", "20% Discount on Diagnostics", "+91-80-2502-4444"));
        hospitals.add(new HospitalPartnerDto("Fortis Hospital Cunningham Road", "Bangalore", "14, Cunningham Rd, Vasanth Nagar", "15% Discount on OPD", "15% Discount on Health Checks", "+91-80-4199-4444"));
        hospitals.add(new HospitalPartnerDto("Apollo Health City Jubilee Hills", "Hyderabad", "Road No 72, Film Nagar, Jubilee Hills", "20% Discount on OPD Consultations", "15% Discount on Diagnostics", "+91-40-2360-7777"));
        hospitals.add(new HospitalPartnerDto("Yashoda Hospital Hitec City", "Hyderabad", "Hitec City Main Rd, Madhapur", "20% Discount on OPD", "15% Discount on Lab Tests", "+91-40-4567-4567"));
        hospitals.add(new HospitalPartnerDto("Kaiser Permanente San Jose Medical Center", "San Jose", "250 Hospital Pkwy, San Jose, CA", "Network In-Plan Co-pay Waiver", "Preventive Lab Screening Covered 100%", "+1-408-972-3000"));

        // Resort partners per SPEC.md §6
        resorts.add(new ResortPartnerDto("The Tamara Coorg (Luxury Rainforest Getaway)", "Coorg, Karnataka", "Out-of-city", "25% Off Corporate Tariff", "PEOPLEFIRST-TAMARA25"));
        resorts.add(new ResortPartnerDto("Windflower Spa & Resort Bandipur", "Bandipur, Karnataka", "Out-of-city", "20% Off Weekend Packages", "PEOPLEFIRST-WIND20"));
        resorts.add(new ResortPartnerDto("Angsana Oasis Spa & Resort", "Bangalore North", "In-city", "20% Off Day-Out & Stay", "PEOPLEFIRST-ANGSANA"));
        resorts.add(new ResortPartnerDto("Golconda Resorts & Spa", "Gandipet, Hyderabad", "In-city", "20% Off Room Bookings", "PEOPLEFIRST-GOLCONDA"));
        resorts.add(new ResortPartnerDto("Carmel Valley Ranch Wellness Sanctuary", "Carmel, California", "Out-of-city", "15% Off Midweek Retreats", "PEOPLEFIRST-CVR15"));
    }

    public List<AmenityDto> getAllAmenities() {
        return amenities;
    }

    public List<HospitalPartnerDto> getHospitalPartners(String city) {
        if (city == null || city.trim().isEmpty()) {
            return hospitals;
        }
        String lower = city.toLowerCase();
        List<HospitalPartnerDto> filtered = hospitals.stream()
                .filter(h -> h.getCity().toLowerCase().contains(lower) || lower.contains(h.getCity().toLowerCase()))
                .toList();
        return filtered.isEmpty() ? hospitals : filtered;
    }

    public List<ResortPartnerDto> getResortPartners() {
        return resorts;
    }

    public List<WellbeingSuggestionDto> evaluateLeaveWellbeing(LeaveRequest request, User user) {
        List<WellbeingSuggestionDto> suggestions = new ArrayList<>();

        // 1. Sick leave rule
        WellbeingSuggestionDto sickSug = sickLeaveRule.evaluate(request, user, hospitals);
        if (sickSug != null) {
            suggestions.add(sickSug);
        }

        // 2. Half-day sick leave rule
        WellbeingSuggestionDto halfDaySug = halfDaySickLeaveRule.evaluate(request, user);
        if (halfDaySug != null) {
            suggestions.add(halfDaySug);
        }

        // 5. Volunteering leave rule
        WellbeingSuggestionDto volSug = volunteeringRule.evaluate(request, user);
        if (volSug != null) {
            suggestions.add(volSug);
        }

        return suggestions;
    }

    public WellbeingSuggestionDto evaluateStressMessage(String message) {
        return stressRule.evaluate(message);
    }

    public WellbeingSuggestionDto checkVacationNudge(User user, boolean hasTakenLeaveInLastQuarter) {
        return vacationNudgeRule.evaluate(user, hasTakenLeaveInLastQuarter, resorts);
    }
}
