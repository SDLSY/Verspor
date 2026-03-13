import { type NextRequest } from "next/server";
import { updateSession } from "@/lib/supabase/middleware";

// Keep legacy routes reachable during the server-side refactor.
// New /api/v2/* endpoints are available in parallel, but existing clients
// must continue to work until the Android cutover is complete.
export async function proxy(request: NextRequest) {
  return updateSession(request);
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
