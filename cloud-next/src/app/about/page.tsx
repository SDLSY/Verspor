import type { Route } from "next";
import { redirect } from "next/navigation";

export default function AboutPage() {
  redirect("/stitch/about.html" as Route);
}
