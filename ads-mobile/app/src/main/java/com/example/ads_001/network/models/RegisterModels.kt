package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("birth_date") val birthDate: String,
    @SerializedName("address") val address: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("district") val district: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("blood_type") val bloodType: String?
)

data class RegisterResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("message") val message: String?
)

data class UserProfileResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("birth_date") val birthDate: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("district") val district: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("blood_type") val bloodType: String?,
    @SerializedName("registered_at") val registeredAt: String?
)
