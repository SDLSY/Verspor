import type { Metadata } from "next";
import StitchPage from "@/components/stitch/stitch-page";

export const metadata: Metadata = {
  title: "VesperO · 产品中心",
  description: "端云协同健康辅助系统的产品总览与闭环叙事。",
};

export default function HomePage() {
  return <StitchPage template="home" />;
}
