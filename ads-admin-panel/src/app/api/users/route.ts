import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

export const dynamic = "force-dynamic";

// Registered users list
export async function GET() {
  try {
    const users = await fetchWithAuth("/admin/users");
    return NextResponse.json(Array.isArray(users) ? users : []);
  } catch (error) {
    console.error("API error (users):", error instanceof Error ? error.message : error);
    return NextResponse.json([]);
  }
}
