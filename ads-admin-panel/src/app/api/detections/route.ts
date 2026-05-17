import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const detections = await fetchWithAuth("/admin/detections");
    return NextResponse.json(detections);
  } catch (error) {
    console.error("API error (detections):", error instanceof Error ? error.message : error);
    // Return empty array on backend failure
    return NextResponse.json([]);
  }
}
