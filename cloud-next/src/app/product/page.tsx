import type { Metadata } from "next";
import StitchPage from "@/components/stitch/stitch-page";

export const metadata: Metadata = {
  title: "VesperO · 产品体验",
  description: "核心功能与用户闭环体验说明。",
};

export default function ProductPage() {
  return <StitchPage template="product" />;
}
