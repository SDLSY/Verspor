import type { Route } from "next";
import { redirect } from "next/navigation";

export default function TechnologyPage() {
  redirect("/stitch/technology.html" as Route);
}
