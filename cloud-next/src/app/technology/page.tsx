import type { Metadata } from "next";
import StitchPage from "@/components/stitch/stitch-page";

export const metadata: Metadata = {
  title: "VesperO · 核心技术",
  description: "端云协同与闭环能力的技术概览。",
};

export default function TechnologyPage() {
  return <StitchPage template="technology" />;
}
