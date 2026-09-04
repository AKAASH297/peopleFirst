package com.peoplefirst.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class AgentChatRequestDto {

    @NotBlank(message = "Message is required")
    private String message;

    private String conversationId;

    public AgentChatRequestDto() {
    }

    public AgentChatRequestDto(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
