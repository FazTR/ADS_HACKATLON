package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class TurkiyeApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<ProvinceData>
)

data class ProvinceData(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("districts") val districts: List<DistrictData>
)

data class DistrictData(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)
