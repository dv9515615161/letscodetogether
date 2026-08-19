import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Product images in demo mode are inline SVG data URIs, so no remote hosts are
  // needed yet. When a live provider returns real CDN image URLs, add its
  // hostname here (see README > Connecting live providers).
  images: {
    remotePatterns: [],
  },
};

export default nextConfig;
