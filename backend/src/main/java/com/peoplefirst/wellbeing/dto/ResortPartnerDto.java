package com.peoplefirst.wellbeing.dto;

public class ResortPartnerDto {

    private String name;
    private String destination;
    private String type; // In-city, Out-of-city, Hill station, Beach
    private String discount;
    private String couponCode;

    public ResortPartnerDto() {
    }

    public ResortPartnerDto(String name, String destination, String type, String discount, String couponCode) {
        this.name = name;
        this.destination = destination;
        this.type = type;
        this.discount = discount;
        this.couponCode = couponCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDiscount() {
        return discount;
    }

    public void setDiscount(String discount) {
        this.discount = discount;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }
}
