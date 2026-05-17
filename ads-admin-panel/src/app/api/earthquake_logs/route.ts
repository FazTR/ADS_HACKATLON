import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

// Crowdsourced earthquake logs
export async function GET() {
  try {
    const logs = await fetchWithAuth("/admin/earthquake_logs");
    return NextResponse.json(Array.isArray(logs) ? logs : []);
  } catch (error) {
    console.error("API error (earthquake_logs):", error instanceof Error ? error.message : error);
    return NextResponse.json([]);
  }
}
