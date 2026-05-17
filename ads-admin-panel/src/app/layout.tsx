import type { Metadata } from "next";
import { Roboto_Condensed, Geist_Mono } from "next/font/google";
import "./globals.css";
import { ThemeProvider } from "next-themes";
import { Toaster } from "sonner";
import { SettingsProvider } from "@/context/SettingsContext";
import { AiRouteProvider } from "@/context/AiRouteContext";
import { ClientLayoutWrapper } from "@/components/ClientLayoutWrapper";

const robotoCondensed = Roboto_Condensed({
  variable: "--font-roboto-condensed",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "ADS - Sismik Ağ Yönetim Merkezi",
  description: "Acil Durum Sistemi ve Enkaz Yönetim Paneli",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="tr" suppressHydrationWarning>
      <head>
        {/* Theme blocking script */}
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  var theme = localStorage.getItem('theme');
                  var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                  if (theme === 'dark' || (!theme && prefersDark)) {
                    document.documentElement.classList.add('dark');
                  } else {
                    document.documentElement.classList.remove('dark');
                  }
                } catch(e) {}
              })();
            `,
          }}
        />
      </head>
      <body
        className={`${robotoCondensed.variable} ${geistMono.variable} font-sans antialiased min-h-screen bg-background text-foreground overflow-x-hidden`}
      >
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
          <SettingsProvider>
            <AiRouteProvider>
              <ClientLayoutWrapper>
                {children}
              </ClientLayoutWrapper>
              <Toaster position="top-right" theme="system" />
            </AiRouteProvider>
          </SettingsProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
