package com.peoplefirst.agent.controller;

import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponseDto> chat(@Valid @RequestBody AgentChatRequestDto request) {
        AgentChatResponseDto response = agentService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getAgentInfo() {
        return ResponseEntity.ok(Map.of(
                "name", "Kura",
                "role", "Leave Management & Wellbeing Concierge",
                "version", "1.0.0"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAgentStatus() {
        return ResponseEntity.ok(agentService.getAgentStatus());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        if (apiKey != null) {
            agentService.updateGenAiKey(apiKey);
        }
        return ResponseEntity.ok(agentService.getAgentStatus());
    }
}
