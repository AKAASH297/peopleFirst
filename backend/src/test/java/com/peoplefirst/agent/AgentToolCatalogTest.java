package com.peoplefirst.agent;

import com.peoplefirst.agent.tools.AgentTool;
import com.peoplefirst.agent.tools.AgentToolCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolCatalogTest {

    @Test
    void exposesSevenGroundedToolsWithStrictSchemas() {
        List<Map<String, Object>> schemas = new AgentToolCatalog().getSchemas();
        assertEquals(7, schemas.size());
        List<String> names = schemas.stream()
                .map(s -> (String) ((Map<String, Object>) s.get("function")).get("name"))
                .toList();
        assertTrue(names.containsAll(List.of("check_balance", "apply_leave", "cancel_leave",
                "view_leaves", "get_policy", "wellbeing", "ticket_info")));
        Map<String, Object> apply = schemas.stream()
                .filter(s -> ((Map<String, Object>) s.get("function")).get("name").equals("apply_leave"))
                .findFirst().orElseThrow();
        Map<String, Object> params = (Map<String, Object>) ((Map<String, Object>) apply.get("function")).get("parameters");
        assertTrue(((List<String>) params.get("required")).containsAll(List.of("leaveType", "startDate", "endDate")));
    }

    @Test
    void enumResolvesByName() {
        assertEquals(AgentTool.APPLY_LEAVE, AgentTool.fromName("apply_leave"));
        assertEquals(AgentTool.WELLBEING, AgentTool.fromName("wellbeing"));
    }
}
