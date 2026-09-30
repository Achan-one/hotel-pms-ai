package com.hotel.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hotel.domain.TagPreference;

import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiBatchTagDto {
    // AI에게 보낸 PMS 예약 번호. 모델이 예전 형식의 키(reservationId)로 답해도 받아 준다.
    @JsonAlias("reservationId")
    private String reservationNo;
    private List<String> preferredTags;
    private List<String> avoidTags;

    public GeminiBatchTagDto() {}

    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }

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