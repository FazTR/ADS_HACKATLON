import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const reports = await fetchWithAuth("/admin/reports");
    if (!Array.isArray(reports)) return NextResponse.json([]);

    const alerts = reports.map((report: any) => {
      let status = "Orta";
      let callerStatus = "Bilinmiyor";

      if (report.status === "under_rubble") {
        status = "Kritik";
        callerStatus = "Enkaz Altında";
      } else if (report.status === "injured") {
        status = "Acil";
        callerStatus = "Yaralı";
      } else if (report.status === "ok") {
        status = "Orta";
        callerStatus = "Güvende";
      }

      return {
        // Mapped fields
        id: report.id,
        device_id: report.device_id,
        timestamp: report.reported_at,
        location: {
          lat: report.latitude,
          lng: report.longitude,
          city: report.city ?? "Koordinat Verisi",
          district: report.district ?? `Cihaz: ${report.device_id}`,
        },
        status,
        callerStatus,
        message: `Mobil ihbar. Pil: %${report.battery_level}`,
        battery_level: report.battery_level,
        // Personal info
        full_name: report.full_name,
        birth_date: report.birth_date,
        address: report.address,
        gender: report.gender,
        blood_type: report.blood_type,
      };
    });

    return NextResponse.json(alerts);
  } catch (error) {
    console.error("API error (alerts):", error instanceof Error ? error.message : error);
    return NextResponse.json([]);
  }
}

export async function POST() {
  return NextResponse.json({ error: "Read-only mode" }, { status: 405 });
}
