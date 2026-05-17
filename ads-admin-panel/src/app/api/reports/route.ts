import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

// Raw reports data
export async function GET() {
  try {
    const reports = await fetchWithAuth("/admin/reports");
    return NextResponse.json(Array.isArray(reports) ? reports : []);
  } catch (error) {
    console.error("API error (reports):", error instanceof Error ? error.message : error);
    return NextResponse.json([]);
  }
}
