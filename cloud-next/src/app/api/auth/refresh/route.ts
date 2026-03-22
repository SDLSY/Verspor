import { NextResponse } from "next/server";
import { hasAdminAccess } from "@/lib/admin-auth";
import { readDemoMetadata } from "@/lib/demo/bootstrap";
import { fail, ok, parseJsonBody } from "@/lib/http";
import { createPublicClient } from "@/lib/supabase";

type RefreshBody = {
  refreshToken?: string;
};

export async function POST(req: Request) {
  const body = parseJsonBody<RefreshBody>(await req.json().catch(() => ({})));
  const refreshToken = (body.refreshToken ?? "").trim();

  if (!refreshToken) {
    return NextResponse.json(fail(400, "refresh token required"), { status: 400 });
  }

  try {
    const client = createPublicClient();
    const { data, error } = await client.auth.refreshSession({
      refresh_token: refreshToken,
    });

    if (error || !data.session || !data.user) {
      return NextResponse.json(fail(401, error?.message ?? "refresh failed"), { status: 401 });
    }

    const metadata = data.user.user_metadata as Record<string, unknown> | null;
    const metadataName = metadata?.username;
    const email = data.user.email?.trim().toLowerCase() ?? "";
    const username = typeof metadataName === "string" ? metadataName : email.split("@")[0];
    const demoMetadata = readDemoMetadata(metadata);

    return NextResponse.json(
      ok("ok", {
        authState: "SIGNED_IN",
        email,
        token: data.session.access_token,
        refreshToken: data.session.refresh_token ?? "",
        userId: data.user.id,
        username,
        demoRole: demoMetadata.demoRole || null,
        demoScenario: demoMetadata.demoScenario || null,
        demoSeedVersion: demoMetadata.demoSeedVersion || null,
        displayName: demoMetadata.displayName || null,
        adminRole: typeof metadata?.adminRole === "string" ? metadata.adminRole : null,
        adminAccess: hasAdminAccess(data.user),
        canResendConfirmation: false,
      })
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "refresh failed";
    return NextResponse.json(fail(500, message), { status: 500 });
  }
}
