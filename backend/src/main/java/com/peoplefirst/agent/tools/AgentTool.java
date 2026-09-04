package com.peoplefirst.agent.tools;

public enum AgentTool {
    CHECK_BALANCE("check_balance"),
    APPLY_LEAVE("apply_leave"),
    CANCEL_LEAVE("cancel_leave"),
    VIEW_LEAVES("view_leaves"),
    CHECK_POLICY("get_policy"),
    WELLBEING("wellbeing"),
    TICKET_INQUIRY("ticket_info");

    private final String name;

    AgentTool(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static AgentTool fromName(String name) {
        for (AgentTool tool : values()) {
            if (tool.name.equals(name)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("Unknown agent tool: " + name);
    }
}
