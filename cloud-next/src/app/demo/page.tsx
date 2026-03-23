import type { Metadata } from "next";
import StitchPage from "@/components/stitch/stitch-page";

export const metadata: Metadata = {
  title: "VesperO · 演示入口",
  description: "闭环演示路径与体验入口。",
};

export default function DemoPage() {
  return <StitchPage template="demo" />;
}
