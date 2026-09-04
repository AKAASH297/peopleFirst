package com.peoplefirst.wellbeing.controller;

import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.repository.LeaveRequestRepository;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.AmenityDto;
import com.peoplefirst.wellbeing.dto.HospitalPartnerDto;
import com.peoplefirst.wellbeing.dto.ResortPartnerDto;
import com.peoplefirst.wellbeing.dto.VacationEmailDto;
import com.peoplefirst.wellbeing.dto.WeeklyWellbeingDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import com.peoplefirst.wellbeing.service.WellbeingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/wellbeing")
public class WellbeingController {

    private final WellbeingService wellbeingService;
    private final CurrentUserProvider currentUserProvider;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveService leaveService;

    public WellbeingController(WellbeingService wellbeingService,
                               CurrentUserProvider currentUserProvider,
                               LeaveRequestRepository leaveRequestRepository,
                               LeaveService leaveService) {
        this.wellbeingService = wellbeingService;
        this.currentUserProvider = currentUserProvider;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveService = leaveService;
    }

    @GetMapping("/amenities")
    public ResponseEntity<List<AmenityDto>> getAmenities() {
        return ResponseEntity.ok(wellbeingService.getAllAmenities());
    }

    @GetMapping("/hospitals")
    public ResponseEntity<List<HospitalPartnerDto>> getHospitals(@RequestParam(required = false) String city) {
        User currentUser = currentUserProvider.getCurrentUser();
        String searchCity = (city != null && !city.isBlank()) ? city : currentUser.getBaseLocation();
        return ResponseEntity.ok(wellbeingService.getHospitalPartners(searchCity));
    }

    @GetMapping("/resorts")
    public ResponseEntity<List<ResortPartnerDto>> getResorts() {
        return ResponseEntity.ok(wellbeingService.getResortPartners());
    }

    @GetMapping("/vacation-nudge")
    public ResponseEntity<WellbeingSuggestionDto> getVacationNudge() {
        User currentUser = currentUserProvider.getCurrentUser();
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<LeaveRequest> recentLeaves = leaveRequestRepository.findByUserIdAndStartDateAfter(currentUser.getId(), threeMonthsAgo);
        boolean hasTakenLeave = !recentLeaves.isEmpty();

        WellbeingSuggestionDto nudge = wellbeingService.checkVacationNudge(currentUser, hasTakenLeave);
        return ResponseEntity.ok(nudge);
    }

    @PostMapping("/send-vacation-email")
    public ResponseEntity<VacationEmailDto> sendVacationEmail() {
        User currentUser = currentUserProvider.getCurrentUser();
        VacationEmailDto emailDto = wellbeingService.sendVacationNudgeEmail(currentUser);
        return ResponseEntity.ok(emailDto);
    }

    @GetMapping("/weekly-status")
    public ResponseEntity<WeeklyWellbeingDto> getWeeklyStatus() {
        User currentUser = currentUserProvider.getCurrentUser();
        List<LeaveRequest> userLeaves = leaveService.getLeaveEntitiesForUser(currentUser.getId());
        WeeklyWellbeingDto status = wellbeingService.getWeeklyWellbeingReport(currentUser, userLeaves);
        return ResponseEntity.ok(status);
    }
}
