import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

// Live drone telemetry
export async function GET() {
  try {
    const drones = await fetchWithAuth("/admin/drones");
    return NextResponse.json(Array.isArray(drones) ? drones : []);
  } catch (error) {
    console.error("API error (drones):", error instanceof Error ? error.message : error);
    return NextResponse.json([]);
  }
}
