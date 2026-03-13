import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    status: "ok",
    service: "cloud-next",
    now: Date.now(),
  });
}
