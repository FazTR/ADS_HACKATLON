import { NextRequest } from "next/server";
import { spawn } from "child_process";

export const dynamic = "force-dynamic";

export async function GET(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;

    // Spawn ffmpeg for mpjpeg stream
    const ffmpeg = spawn("ffmpeg", [
      "-f", "v4l2",
      "-framerate", "15",
      "-video_size", "640x480",
      "-i", "/dev/video0",
      "-f", "mpjpeg",
      "-q:v", "5",
      "-"
    ]);

    const stream = new ReadableStream({
      start(controller) {
        ffmpeg.stdout.on("data", (chunk) => {
          controller.enqueue(chunk);
        });

        ffmpeg.stdout.on("end", () => {
          try { controller.close(); } catch (e) {}
        });

        ffmpeg.stderr.on("data", (data) => {
          // Ignore ffmpeg logs
        });

        ffmpeg.on("error", (err) => {
          console.error("FFmpeg error:", err);
          try { controller.error(err); } catch (e) {}
        });
      },
      cancel() {
        ffmpeg.kill("SIGKILL");
      }
    });

    return new Response(stream, {
      status: 200,
      headers: {
        "Content-Type": "multipart/x-mixed-replace;boundary=ffmpeg",
        "Cache-Control": "no-store, no-cache, must-revalidate, max-age=0",
        "Pragma": "no-cache",
        "Connection": "keep-alive"
      },
    });
  } catch (error) {
    console.error("Video feed error:", error);
    return new Response("Video feed unavailable", { status: 503 });
  }
}
