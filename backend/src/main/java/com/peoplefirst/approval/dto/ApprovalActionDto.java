package com.peoplefirst.approval.dto;

public class ApprovalActionDto {

    private String comment;

    public ApprovalActionDto() {
    }

    public ApprovalActionDto(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
