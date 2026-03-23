import type { Route } from "next";
import { redirect } from "next/navigation";

export default function DemoPage() {
  redirect("/stitch/demo.html" as Route);
}
