package com.peoplefirst.agent.intent;

/**
 * Lightweight per-user conversation context tracker for contextual intent resolution.
 * Stores what the agent last asked so follow-up messages can be understood in context.
 * <p>
 * For example, if the agent last asked "Which type of leave?", and the user replies
 * just "casual", the context tells the parser this is a leave-type answer, not UNKNOWN.
 */
public class ConversationContext {

    /**
     * What the agent last prompted the user for.
     */
    public enum PromptType {
        NONE,              // No specific prompt pending
        LEAVE_TYPE,        // Agent asked "Which type of leave?"
        HALF_DAY_SESSION,  // Agent asked "Morning or Afternoon session?"
        DATE,              // Agent asked "What dates?"
        REASON,            // Agent asked "What's the reason?"
        CONFIRMATION,      // Agent asked "Shall I submit?" (yes/no)
        CANCEL_SELECT,     // Agent asked "Which leave to cancel?"
        EDIT_SELECT,       // Agent asked "Which leave to edit?"
        EDIT_FIELD,        // Agent asked "What would you like to update?"
        STRESS_FOLLOWUP,   // Agent offered stress intervention
        GENERAL            // Generic question / greeting
    }

    private AgentIntent lastIntent;
    private PromptType lastPromptType = PromptType.NONE;
    private String lastAgentReply; // truncated to 200 chars
    private long updatedAt = System.currentTimeMillis();

    public ConversationContext() {}

    public boolean isExpired() {
        return (System.currentTimeMillis() - updatedAt) > (15 * 60 * 1000L); // 15 min
    }

    public void update(AgentIntent intent, PromptType promptType, String agentReply) {
        this.lastIntent = intent;
        this.lastPromptType = promptType != null ? promptType : PromptType.NONE;
        this.lastAgentReply = agentReply != null
                ? (agentReply.length() > 200 ? agentReply.substring(0, 200) : agentReply)
                : null;
        this.updatedAt = System.currentTimeMillis();
    }

    public AgentIntent getLastIntent() { return lastIntent; }
    public void setLastIntent(AgentIntent lastIntent) { this.lastIntent = lastIntent; }

    public PromptType getLastPromptType() { return lastPromptType; }
    public void setLastPromptType(PromptType lastPromptType) { this.lastPromptType = lastPromptType; }

    public String getLastAgentReply() { return lastAgentReply; }

    public long getUpdatedAt() { return updatedAt; }
}
