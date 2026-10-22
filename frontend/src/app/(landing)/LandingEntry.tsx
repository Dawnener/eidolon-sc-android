import { APP_DOWNLOAD_URL } from "@/lib/appDownload";

/**
 * 落地页的入口：访客直接下载 Android App；已登录的用户（只在 `/about` 出现）是「继续」，
 * 回到对话列表。对话列表在应用的根 layout 下，用普通的 <a> 整页跳过去。
 *
 * 外观是 shadcn Button 的 `buttonVariants`，类名由服务端算好传进来（`className`）：首屏不带
 * Base UI 的 Button 和 cn，落地页自身的首屏 JS 才守得住预算（ADR-0016 修订）。
 */
export function LandingEntry({
  isSignedIn,
  label,
  className,
}: {
  isSignedIn: boolean;
  label: string;
  className: string;
}) {
  const href = isSignedIn ? "/conversations" : APP_DOWNLOAD_URL;
  return (
    <a href={href} className={className} download={isSignedIn ? undefined : ""}>
      {label}
    </a>
  );
}
