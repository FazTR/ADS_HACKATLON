import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

// Earthquake events
export async function GET() {
  try {
    const earthquakes = await fetchWithAuth("/system/earthquakes");
    if (Array.isArray(earthquakes) && earthquakes.length > 0) {
      const fs = await import('fs');
      fs.writeFileSync('earthquake_dump.json', JSON.stringify(earthquakes[0], null, 2));
    }
    return NextResponse.json(Array.isArray(earthquakes) ? earthquakes : []);
  } catch (error) {
    console.error("API error (earthquakes):", error instanceof Error ? error.message : error);
    return NextResponse.json([]);
  }
}
