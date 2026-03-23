import fs from "node:fs";
import path from "node:path";

type StitchTemplate = "home" | "about" | "product" | "technology" | "demo";

type StitchTemplateData = {
  html: string;
};

const TEMPLATE_DIR = path.join(process.cwd(), "src", "stitch", "templates");

const LINK_RULES: Array<{ matcher: RegExp; href: string }> = [
  { matcher: /VesperO|Home/i, href: "/" },
  { matcher: /\u957f\u5e9a\u73af|\u9996\u9875/i, href: "/" },
  {
    matcher:
      /\u4ea7\u54c1|\u4ea7\u54c1\u4e2d\u5fc3|\u4ea7\u54c1\u7cfb\u5217|Product/i,
    href: "/product",
  },
  {
    matcher:
      /\u6280\u672f|\u6838\u5fc3\u6280\u672f|\u6280\u672f\u89e3\u6790|\u79d1\u5b66\u7814\u7a76|\u79d1\u5b66\u767d\u76ae\u4e66|Technology/i,
    href: "/technology",
  },
  { matcher: /\u5173\u4e8e|\u5173\u4e8e\u6211\u4eec|About/i, href: "/about" },
  {
    matcher:
      /\u5a92\u4f53\u8d44\u6599\u5e93|\u5a92\u4f53\u5e93|\u65b0\u95fb\u5305|Press Kit/i,
    href: "/about",
  },
  { matcher: /\u9690\u79c1\u653f\u7b56|Privacy Policy/i, href: "/about" },
  { matcher: /\u670d\u52a1\u6761\u6b3e|Terms of Service/i, href: "/about" },
  { matcher: /\u6848\u4f8b\u7814\u7a76|Case/i, href: "/product" },
];

const DEMO_BUTTON_RULES = [
  /\u9884\u7ea6\u4f53\u9a8c/,
  /\u9884\u7ea6\u6f14\u793a/,
  /\u9884\u7ea6\s*\/\s*\u4f53\u9a8c/,
  /Reserve\/Demo/i,
];

const BACKSTAGE_BUTTON_RULES = [/\u540e\u53f0/];
const START_JOURNEY_RULES = [
  /\u5f00\u59cb\u60a8\u7684\u8fdb\u5316\u4e4b\u65c5/,
  /\u5f00\u59cb.*\u8fdb\u5316.*\u4e4b\u65c5/,
];

const ENHANCEMENT_STYLE = `
  :root {
    font-kerning: normal;
  }
  body {
    text-rendering: optimizeLegibility;
    letter-spacing: 0.01em;
    font-feature-settings: "palt" 1, "liga" 1;
  }
  h1, h2, h3, h4 {
    letter-spacing: 0.02em;
    text-wrap: balance;
  }
  .stitch-card {
    position: relative;
    overflow: hidden;
    transition: transform .35s ease, box-shadow .35s ease, opacity .35s ease;
  }
  .stitch-card::after {
    content: "";
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0.14;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='320' height='200' viewBox='0 0 320 200'%3E%3Cdefs%3E%3CradialGradient id='g' cx='50%25' cy='50%25' r='60%25'%3E%3Cstop offset='0%25' stop-color='%234DE082' stop-opacity='0.4'/%3E%3Cstop offset='100%25' stop-color='%2310131B' stop-opacity='0'/%3E%3C/radialGradient%3E%3C/defs%3E%3Crect width='320' height='200' fill='url(%23g)'/%3E%3C/svg%3E");
    background-size: 320px 200px;
    background-repeat: no-repeat;
    background-position: 80% 20%;
    mix-blend-mode: screen;
  }
  .stitch-card:hover {
    transform: translateY(-6px);
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.3);
  }
  section {
    opacity: 0;
    transform: translateY(16px);
    animation: stitchFadeUp .8s ease forwards;
  }
  section:nth-of-type(1) { animation-delay: 0.05s; }
  section:nth-of-type(2) { animation-delay: 0.15s; }
  section:nth-of-type(3) { animation-delay: 0.25s; }
  section:nth-of-type(4) { animation-delay: 0.35s; }
  section:nth-of-type(5) { animation-delay: 0.45s; }
  @keyframes stitchFadeUp {
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    section {
      opacity: 1;
      transform: none;
      animation: none;
    }
    .stitch-card {
      transition: none;
    }
  }
`;

const PRODUCT_HERO_FIX_STYLE = `
  body[data-stitch-product="true"] section.min-h-screen {
    overflow: visible !important;
  }
  body[data-stitch-product="true"] section.min-h-screen .relative.z-10 {
    overflow: visible !important;
  }
  body[data-stitch-product="true"] .stitch-product-hero-title {
    display: flex;
    flex-direction: column;
    align-items: center;
    font-style: normal !important;
    line-height: 1.12 !important;
    padding-top: 0.08em;
    padding-bottom: 0.4em;
    overflow: visible !important;
  }
  body[data-stitch-product="true"] .stitch-product-hero-line {
    display: block;
    font-style: normal !important;
    transform: skewX(-10deg);
    transform-origin: center;
    overflow: visible !important;
  }
  body[data-stitch-product="true"] .stitch-product-hero-line--first {
    margin-bottom: 0.04em;
  }
  body[data-stitch-product="true"] .stitch-product-hero-line--second {
    display: inline-block;
    line-height: 1.18 !important;
    margin-top: 0;
    padding-bottom: 0.12em;
  }
`;

const TEAM_STYLE = [
  ".stitch-team-chip{display:inline-flex;align-items:center;gap:8px;padding:8px 14px;border-radius:999px;background:rgba(29,32,39,0.65);border:1px solid rgba(194,198,219,0.15);color:#E0E2ED;font-size:11px;letter-spacing:.4em;text-transform:uppercase;}",
  ".stitch-team-chip::before{content:\"\";width:6px;height:6px;border-radius:999px;background:#4DE082;box-shadow:0 0 12px rgba(77,224,130,.6);}",
  ".stitch-team-row{display:flex;justify-content:flex-start;}",
  ".stitch-hero-badge{margin-top:12px;display:inline-flex;align-items:center;gap:8px;padding:8px 18px;border-radius:999px;background:rgba(29,32,39,0.7);border:1px solid rgba(194,198,219,0.2);color:#C2C6DB;font-size:11px;letter-spacing:.3em;text-transform:uppercase;box-shadow:0 18px 40px rgba(0,0,0,0.35);}",
  ".stitch-hero-badge::before{content:\"\";width:6px;height:6px;border-radius:999px;background:#4DE082;box-shadow:0 0 10px rgba(77,224,130,.6);}",
].join(" ");

function stripTags(html: string) {
  return html.replace(/<[^>]+>/g, "").trim();
}

function trimAfterHtmlEnd(html: string) {
  const endTag = "</html>";
  const endIndex = html.toLowerCase().indexOf(endTag);
  if (endIndex === -1) {
    return html;
  }
  return html.slice(0, endIndex + endTag.length);
}

function replaceAnchorHrefsByText(html: string) {
  return html.replace(
    /<a([^>]*?)href="#([^"]*?)"([^>]*)>([\s\S]*?)<\/a>/g,
    (match, before, _href, after, inner) => {
      const text = stripTags(inner);
      const rule = LINK_RULES.find((item) => item.matcher.test(text));
      if (!rule) {
        return match;
      }
      return `<a${before}href="${rule.href}" target="_top"${after}>${inner}</a>`;
    },
  );
}

function replaceButtonsWithLinks(html: string) {
  return html.replace(
    /<button([^>]*)>([\s\S]*?)<\/button>/g,
    (match, attrs, inner) => {
      const text = stripTags(inner);
      if (DEMO_BUTTON_RULES.some((rule) => rule.test(text))) {
        return `<a${attrs} href="/login" target="_top">\u540e\u53f0\u5165\u53e3</a>`;
      }
      if (BACKSTAGE_BUTTON_RULES.some((rule) => rule.test(text))) {
        return `<a${attrs} href="/login" target="_top">\u540e\u53f0\u5165\u53e3</a>`;
      }
      if (START_JOURNEY_RULES.some((rule) => rule.test(text))) {
        return `<a${attrs} href="/product" target="_top">${inner}</a>`;
      }
      return `<a${attrs} href="/demo" target="_top">${inner}</a>`;
    },
  );
}

function replaceAnchorText(html: string) {
  return html.replace(
    /<a([^>]*?)href="([^"]*)"([^>]*)>([\s\S]*?)<\/a>/g,
    (match, before, href, after, inner) => {
      const text = stripTags(inner);
      if (DEMO_BUTTON_RULES.some((rule) => rule.test(text))) {
        return `<a${before}href="/login" target="_top"${after}>\u540e\u53f0\u5165\u53e3</a>`;
      }
      if (!href || href === "#") {
        return match;
      }
      return `<a${before}href="${href}" target="_top"${after}>${inner}</a>`;
    },
  );
}

function removeBottomCtaSection(html: string, template: StitchTemplate) {
  if (template === "technology" || template === "about") {
    return html.replace(/<!--\s*Call to Action\s*-->[\s\S]*?<\/section>/i, "");
  }
  return html;
}

function removeBottomCtaControls(html: string, template: StitchTemplate) {
  if (template !== "product" && template !== "technology" && template !== "about") {
    return html;
  }

  let next = html.replace(
    /<button[^>]*bg-primary text-on-primary[^>]*px-12[^>]*py-5[^>]*>[\s\S]*?<\/button>/gi,
    "",
  );
  next = next.replace(
    /<a[^>]*bg-primary text-on-primary[^>]*px-12[^>]*py-5[^>]*>[\s\S]*?<\/a>/gi,
    "",
  );
  next = next.replace(
    /<button[^>]*px-12[^>]*py-5[^>]*rounded-full[^>]*>[\s\S]*?<\/button>/gi,
    "",
  );
  next = next.replace(
    /<a[^>]*px-12[^>]*py-5[^>]*rounded-full[^>]*>[\s\S]*?<\/a>/gi,
    "",
  );
  return next;
}

function injectBaseTarget(html: string) {
  if (/<base\s/i.test(html)) {
    return html;
  }
  return html.replace(/<head>/i, "<head><base target=\"_top\" />");
}

function replaceProductImages(html: string, template: StitchTemplate) {
  if (template !== "product") {
    return html;
  }
  const withPersonIcon = html.replace(
    /<span class="material-symbols-outlined text-6xl text-primary mb-4">[^<]*<\/span>/i,
    '<span class="material-symbols-outlined text-6xl text-primary mb-4">person</span>',
  );
  return withPersonIcon.replace(
    /<h1 class="font-headline italic text-6xl md:text-8xl tracking-tight mb-8 leading-tight">[\s\S]*?<\/h1>/i,
    '<h1 class="font-headline text-6xl md:text-8xl tracking-tight mb-8 leading-tight stitch-product-hero-title"><span class="stitch-product-hero-line stitch-product-hero-line--first">\u6bcf\u4e00\u6b21\u5fc3\u8df3\u7684</span><span class="stitch-product-hero-line stitch-product-hero-line--second gradient-text">\u6df1\u5ea6\u65c5\u7a0b</span></h1>',
  );
}

function addCardClass(html: string) {
  let next = html.replace(
    /class="([^"]*\bglass-panel\b[^"]*)"/g,
    'class="$1 stitch-card"',
  );
  next = next.replace(
    /class="([^"]*\bbg-surface-container(?:-(?:low|lowest|high|highest))?\b[^"]*)"/g,
    'class="$1 stitch-card"',
  );
  return next;
}

function injectEnhancements(html: string) {
  if (html.includes("stitch-enhance")) {
    return html;
  }
  return html.replace(
    /<\/head>/i,
    `<style id="stitch-enhance">${ENHANCEMENT_STYLE}${TEAM_STYLE}${PRODUCT_HERO_FIX_STYLE}</style></head>`,
  );
}

function addProductBodyFlag(html: string, template: StitchTemplate) {
  if (template !== "product") {
    return html;
  }
  return html.replace(
    /<body([^>]*)>/i,
    (match, attrs) => {
      if (/data-stitch-product=/.test(attrs)) {
        return match;
      }
      return `<body${attrs} data-stitch-product="true">`;
    },
  );
}

function addTeamBadge(html: string, template: StitchTemplate) {
  if (template !== "home") {
    return html;
  }
  const badgeMarkup =
    "<div class=\"stitch-team-row\"><span class=\"stitch-team-chip\">\u591c\u5de1\u8005</span></div>";
  return html.replace(
    /<section class="py-32 bg-surface[^"]*">/i,
    (match) => `${match}${badgeMarkup}`,
  );
}

function stripHomeNavbar(html: string, template: StitchTemplate) {
  if (template !== "home") {
    return html;
  }
  return html.replace(/<!--\s*TopNavBar\s*-->\s*<nav[\s\S]*?<\/nav>/i, "");
}

function tweakHomeHeroCopy(html: string, template: StitchTemplate) {
  if (template !== "home") {
    return html;
  }
  const heroCopy =
    "\u878d\u5408\u7cbe\u5bc6\u4f20\u611f AI \u6df1\u5ea6\u7406\u89e3 \u4e0e\u4e34\u5e8a\u7ea7\u5e72\u9884 \u4e3a\u60a8\u6784\u5efa\u6c38\u4e0d\u505c\u6b47\u7684\u5065\u5eb7\u8fdb\u5316\u95ed\u73af";
  const badgeCopy =
    "\u591c\u5de1\u8005 \u00b7 \u7b2c\u5341\u4e5d\u5c4a\u5168\u56fd\u5927\u5b66\u751f\u8f6f\u4ef6\u521b\u65b0\u5927\u8d5b";
  const withBadge = html.replace(
    /<div class="flex justify-center gap-6">[\s\S]*?<\/div>/i,
    (match) => `${match}<div class="stitch-hero-badge">${badgeCopy}</div>`,
  );
  return withBadge.replace(
    /<p class="font-manrope[^"]*mb-12">[\s\S]*?<\/p>/i,
    `<p class="font-manrope font-light text-lg md:text-xl text-on-surface-variant max-w-3xl mx-auto leading-relaxed mb-12 tracking-wide whitespace-nowrap">${heroCopy}</p>`,
  );
}

function removeHomeSubline(html: string, template: StitchTemplate) {
  if (template !== "home") {
    return html;
  }
  return html.replace(
    /<p class="font-manrope[^"]*text-on-surface-variant[^"]*">\u52a0\u5165\u5148\u950b\u4f53\u9a8c\u8ba1\u5212\uff0c\u9996\u6279\u540d\u989d\u73b0\u5df2\u5f00\u653e\u7533\u8bf7<\/p>/i,
    "",
  );
}

function updateFooterYear(html: string) {
  return html.replace(/2024/g, "2026");
}

function normalizeFooterTextFixed(html: string) {
  const cleanCopy =
    "\u00a9 2026 VesperO\uff08\u957f\u5e9a\u73af\uff09 \u7cbe\u7814\u4fee\u590d\u7ea7\u5954\u534e\u5065\u5eb7\u3002";
  let next = html.replace(
    />[^<]*2026[^<]*VESPERO[^<]*</gi,
    `>${cleanCopy}<`,
  );
  next = next.replace(
    /<span class="material-symbols-outlined[^"]*text-\[#C2C6DB\]\/40[^"]*">[^<]*<\/span>/g,
    '<span class="material-symbols-outlined text-[#C2C6DB]/40 cursor-pointer hover:text-tertiary transition-colors">rss_feed</span>',
  );
  return next;
}

function readTemplate(template: StitchTemplate): StitchTemplateData {
  const filePath = path.join(TEMPLATE_DIR, `${template}.html`);
  const fileHtml = fs.readFileSync(filePath, "utf8");
  const html = trimAfterHtmlEnd(fileHtml);

  const ctaStrippedHtml = removeBottomCtaSection(html, template);
  const linkedHtml = replaceAnchorHrefsByText(ctaStrippedHtml);
  const buttonHtml = replaceButtonsWithLinks(linkedHtml);
  const enhancedHtml = replaceAnchorText(buttonHtml);
  const trimmedHtml = removeBottomCtaControls(enhancedHtml, template);
  const imageHtml = replaceProductImages(trimmedHtml, template);
  const cardHtml = addCardClass(imageHtml);
  const navStrippedHtml = stripHomeNavbar(cardHtml, template);
  const heroTunedHtml = tweakHomeHeroCopy(navStrippedHtml, template);
  const withoutSublineHtml = removeHomeSubline(heroTunedHtml, template);
  const withTeamHtml = addTeamBadge(withoutSublineHtml, template);
  const flaggedHtml = addProductBodyFlag(withTeamHtml, template);
  const baseHtml = injectBaseTarget(flaggedHtml);
  const yearUpdatedHtml = updateFooterYear(baseHtml);
  const normalizedFooterHtml = normalizeFooterTextFixed(yearUpdatedHtml);
  const finalHtml = injectEnhancements(normalizedFooterHtml);

  return { html: finalHtml };
}

export default function StitchPage({ template }: { template: StitchTemplate }) {
  const { html } = readTemplate(template);
  const showHomeButton = template !== "home";

  return (
    <div data-stitch-page={template}>
      <style
        dangerouslySetInnerHTML={{
          __html:
            ".stitch-frame{width:100%;height:100vh;border:0;display:block;background:#10131B;} .stitch-frame-shell{min-height:100vh;} .stitch-home-button{position:fixed;right:24px;bottom:24px;z-index:1000;padding:10px 16px;border-radius:999px;background:rgba(29,32,39,0.7);backdrop-filter:blur(18px);color:#E0E2ED;font-family:'Manrope',sans-serif;font-size:12px;letter-spacing:.2em;text-transform:uppercase;border:1px solid rgba(194,198,219,0.15);transition:transform .2s ease,opacity .2s ease;} .stitch-home-button:hover{transform:translateY(-2px);opacity:.9;}",
        }}
      />
      <div className="stitch-frame-shell">
        <iframe className="stitch-frame" title={`stitch-${template}`} srcDoc={html} />
      </div>
      {showHomeButton ? (
        <a className="stitch-home-button" href="/" target="_top">
          {"\u8fd4\u56de\u9996\u9875"}
        </a>
      ) : null}
    </div>
  );
}
