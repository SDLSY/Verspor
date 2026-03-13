import type { Metadata } from "next";
import { IBM_Plex_Mono, Manrope } from "next/font/google";
import "./globals.css";

const heading = Manrope({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-heading",
});

const body = IBM_Plex_Mono({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-body",
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "VesperO 模型运维后台",
  description: "用于管理推荐策略、患者运营、报告处理与系统运维的一体化后台。",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className={`${heading.variable} ${body.variable}`}>{children}</body>
    </html>
  );
}
