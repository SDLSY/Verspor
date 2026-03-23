import type { Metadata } from "next";
import StitchPage from "@/components/stitch/stitch-page";

export const metadata: Metadata = {
  title: "VesperO · 关于我们",
  description: "品牌理念、价值主张与团队定位介绍。",
};

export default function AboutPage() {
  return <StitchPage template="about" />;
}
