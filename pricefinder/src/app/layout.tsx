import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'PriceFinder — Compare Prices Across Online Stores',
  description: 'Search and compare product prices across multiple shopping platforms.',
  applicationName: 'PriceFinder',
  keywords: ['price comparison', 'grocery prices', 'Blinkit', 'Zepto', 'BigBasket', 'Amazon', 'Flipkart', 'Instamart'],
  openGraph: {
    title: 'PriceFinder — Compare Prices Across Online Stores',
    description: 'Search and compare product prices across multiple shopping platforms.',
    type: 'website',
    siteName: 'PriceFinder',
  },
  twitter: {
    card: 'summary',
    title: 'PriceFinder — Compare Prices Across Online Stores',
    description: 'Search and compare product prices across multiple shopping platforms.',
  },
  robots: { index: true, follow: true },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#f6f7f9' },
    { media: '(prefers-color-scheme: dark)', color: '#0c0f14' },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-dvh antialiased">{children}</body>
    </html>
  );
}
