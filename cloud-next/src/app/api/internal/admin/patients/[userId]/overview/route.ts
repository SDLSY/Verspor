import { getAdminPatientOverview } from "@/lib/admin-patients";
import { withAdminRoute } from "@/lib/admin-route";

type RouteContext = {
  params: Promise<{ userId: string }>;
};

export async function GET(_req: Request, context: RouteContext) {
  const { userId } = await context.params;
  return withAdminRoute(async () => getAdminPatientOverview(userId));
}
