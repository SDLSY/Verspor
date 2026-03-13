import { getAdminModels } from "@/lib/admin-system";
import { withAdminRoute } from "@/lib/admin-route";

export async function GET() {
  return withAdminRoute(async () => getAdminModels());
}