package com.hotel.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hotel.domain.TagPreference;

import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiBatchTagDto {
    private String reservationId;
    private List<String> preferredTags;
    private List<String> avoidTags;

    public GeminiBatchTagDto() {}

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public List<String> getPreferredTags() { return preferredTags; }
    public void setPreferredTags(List<String> preferredTags) { this.preferredTags = preferredTags; }

    public List<String> getAvoidTags() { return avoidTags; }
    public void setAvoidTags(List<String> avoidTags) { this.avoidTags = avoidTags; }

    public TagPreference toDomain() {
        Set<String> pref = (preferredTags != null) ? Set.copyOf(preferredTags) : Set.of();
        Set<String> avoid = (avoidTags != null) ? Set.copyOf(avoidTags) : Set.of();
        return new TagPreference(pref, avoid);
    }
}