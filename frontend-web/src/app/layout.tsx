import type { Metadata } from "next";
import { SocialProvider } from "@/components/social-provider";
import { AppShell } from "@/components/app-shell";
import "./globals.css";
export const metadata: Metadata = {
  title: "VibeSocial — Modern Social Platform",
  description: "A place for friends, conversations and everyday discoveries.",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <SocialProvider>
          <AppShell>{children}</AppShell>
        </SocialProvider>
      </body>
    </html>
  );
}
