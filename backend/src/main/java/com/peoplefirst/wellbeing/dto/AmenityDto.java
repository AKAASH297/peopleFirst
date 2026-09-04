package com.peoplefirst.wellbeing.dto;

public class AmenityDto {

    private String id;
    private String name;
    private String category;
    private String timing;
    private String location;
    private String description;

    public AmenityDto() {
    }

    public AmenityDto(String id, String name, String category, String timing, String location, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.timing = timing;
        this.location = location;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTiming() {
        return timing;
    }

    public void setTiming(String timing) {
        this.timing = timing;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
