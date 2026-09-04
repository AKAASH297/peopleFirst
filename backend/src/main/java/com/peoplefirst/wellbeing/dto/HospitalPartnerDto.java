package com.peoplefirst.wellbeing.dto;

public class HospitalPartnerDto {

    private String name;
    private String city;
    private String address;
    private String opdDiscount;
    private String labTestDiscount;
    private String contactNumber;

    public HospitalPartnerDto() {
    }

    public HospitalPartnerDto(String name, String city, String address, String opdDiscount, String labTestDiscount, String contactNumber) {
        this.name = name;
        this.city = city;
        this.address = address;
        this.opdDiscount = opdDiscount;
        this.labTestDiscount = labTestDiscount;
        this.contactNumber = contactNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getOpdDiscount() {
        return opdDiscount;
    }

    public void setOpdDiscount(String opdDiscount) {
        this.opdDiscount = opdDiscount;
    }

    public String getLabTestDiscount() {
        return labTestDiscount;
    }

    public void setLabTestDiscount(String labTestDiscount) {
        this.labTestDiscount = labTestDiscount;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }
}
