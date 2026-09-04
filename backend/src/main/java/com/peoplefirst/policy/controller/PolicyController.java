package com.peoplefirst.policy.controller;

import com.peoplefirst.policy.dto.PolicyResponseDto;
import com.peoplefirst.policy.service.PolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ResponseEntity<PolicyResponseDto> getPolicies() {
        return ResponseEntity.ok(policyService.getCompanyPolicies());
    }
}
