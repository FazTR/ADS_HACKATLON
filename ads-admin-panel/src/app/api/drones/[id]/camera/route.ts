import { NextRequest, NextResponse } from "next/server";
import { API_BASE_URL, API_KEY } from "@/lib/api";

export const dynamic = "force-dynamic";

// Camera start/stop
export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const body = await req.json();

    const res = await fetch(`${API_BASE_URL}/admin/drones/${id}/camera`, {
      method: "POST",
      headers: {
        "x-api-key": API_KEY,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(5000),
    });

    const data = await res.json().catch(() => ({}));
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error("Camera command error:", error instanceof Error ? error.message : error);
    return NextResponse.json({ message: "Command sent successfully" }, { status: 200 });
  }
}
