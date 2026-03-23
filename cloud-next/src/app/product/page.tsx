import type { Route } from "next";
import { redirect } from "next/navigation";

export default function ProductPage() {
  redirect("/stitch/product.html" as Route);
}
