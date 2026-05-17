import { NextResponse } from "next/server";
import { API_BASE_URL, API_KEY } from "@/lib/api";

export const dynamic = "force-dynamic";

export async function GET() {
  const url = `${API_BASE_URL}/stream`;
  
  const controller = new AbortController();
  // 3s connection timeout

  try {
    const response = await fetch(url, {
      headers: {
        "x-api-key": API_KEY,
        "Accept": "text/event-stream",
      },
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      return NextResponse.json({ error: "Stream failed" }, { status: response.status });
    }

    // Proxy the stream
    return new Response(response.body, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache, no-transform",
        "Connection": "keep-alive",
      },
    });
  } catch (error) {
    clearTimeout(timeoutId);
    console.error("SSE Proxy error:", error instanceof Error ? error.message : error);
    // Immediate error on backend failure
    return NextResponse.json({ error: "Stream unavailable" }, { status: 503 });
  }
}
