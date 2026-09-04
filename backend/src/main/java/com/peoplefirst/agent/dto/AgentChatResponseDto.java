package com.peoplefirst.agent.dto;

import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;

import java.util.List;

public class AgentChatResponseDto {

    private String reply;
    private String intent;
    private boolean actionExecuted;
    private String actionName;
    private Object actionData;
    private List<WellbeingSuggestionDto> wellbeingSuggestions;
    private List<String> quickReplies;

    public AgentChatResponseDto() {
    }

    public AgentChatResponseDto(String reply, String intent) {
        this.reply = reply;
        this.intent = intent;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public boolean isActionExecuted() {
        return actionExecuted;
    }

    public void setActionExecuted(boolean actionExecuted) {
        this.actionExecuted = actionExecuted;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public Object getActionData() {
        return actionData;
    }

    public void setActionData(Object actionData) {
        this.actionData = actionData;
    }

    public List<WellbeingSuggestionDto> getWellbeingSuggestions() {
        return wellbeingSuggestions;
    }

    public void setWellbeingSuggestions(List<WellbeingSuggestionDto> wellbeingSuggestions) {
        this.wellbeingSuggestions = wellbeingSuggestions;
    }

    public List<String> getQuickReplies() {
        return quickReplies;
    }

    public void setQuickReplies(List<String> quickReplies) {
        this.quickReplies = quickReplies;
    }
}
