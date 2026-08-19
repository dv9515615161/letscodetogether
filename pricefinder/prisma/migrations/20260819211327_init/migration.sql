-- CreateEnum
CREATE TYPE "ProviderId" AS ENUM ('blinkit', 'zepto', 'amazon', 'flipkart', 'bigbasket', 'instamart');

-- CreateEnum
CREATE TYPE "DataSource" AS ENUM ('live', 'demo');

-- CreateEnum
CREATE TYPE "Availability" AS ENUM ('available', 'out_of_stock', 'unknown');

-- CreateTable
CREATE TABLE "Provider" (
    "id" "ProviderId" NOT NULL,
    "name" TEXT NOT NULL,
    "websiteUrl" TEXT,
    "logoUrl" TEXT,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "dataSource" "DataSource" NOT NULL DEFAULT 'demo',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Provider_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Product" (
    "id" TEXT NOT NULL,
    "providerId" "ProviderId" NOT NULL,
    "externalId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "normalizedTitle" TEXT NOT NULL,
    "matchKey" TEXT NOT NULL,
    "brand" TEXT,
    "description" TEXT,
    "imageUrl" TEXT,
    "productUrl" TEXT,
    "quantity" DOUBLE PRECISION,
    "unit" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Product_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SearchQuery" (
    "id" TEXT NOT NULL,
    "query" TEXT NOT NULL,
    "normalizedQuery" TEXT NOT NULL,
    "pincode" TEXT,
    "latitude" DOUBLE PRECISION,
    "longitude" DOUBLE PRECISION,
    "providersSearched" INTEGER NOT NULL DEFAULT 0,
    "productsFound" INTEGER NOT NULL DEFAULT 0,
    "lowestPrice" DOUBLE PRECISION,
    "durationMs" INTEGER,
    "cacheHit" BOOLEAN NOT NULL DEFAULT false,
    "userId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SearchQuery_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SearchResult" (
    "id" TEXT NOT NULL,
    "searchQueryId" TEXT NOT NULL,
    "providerId" "ProviderId" NOT NULL,
    "productId" TEXT,
    "title" TEXT NOT NULL,
    "price" DOUBLE PRECISION NOT NULL,
    "originalPrice" DOUBLE PRECISION,
    "currency" TEXT NOT NULL DEFAULT 'INR',
    "quantity" DOUBLE PRECISION,
    "unit" TEXT,
    "pricePerUnit" DOUBLE PRECISION,
    "unitBasis" TEXT,
    "availability" "Availability" NOT NULL DEFAULT 'unknown',
    "deliveryTimeMinutes" INTEGER,
    "deliveryFee" DOUBLE PRECISION,
    "rating" DOUBLE PRECISION,
    "reviewCount" INTEGER,
    "productUrl" TEXT,
    "imageUrl" TEXT,
    "matchKey" TEXT NOT NULL,
    "dataSource" "DataSource" NOT NULL DEFAULT 'demo',
    "fetchedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SearchResult_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PriceHistory" (
    "id" TEXT NOT NULL,
    "productId" TEXT NOT NULL,
    "providerId" "ProviderId" NOT NULL,
    "price" DOUBLE PRECISION NOT NULL,
    "pricePerUnit" DOUBLE PRECISION,
    "unitBasis" TEXT,
    "currency" TEXT NOT NULL DEFAULT 'INR',
    "pincode" TEXT,
    "recordedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PriceHistory_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CacheEntry" (
    "key" TEXT NOT NULL,
    "value" JSONB NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CacheEntry_pkey" PRIMARY KEY ("key")
);

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "name" TEXT,
    "pincode" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Basket" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL DEFAULT 'My basket',
    "userId" TEXT,
    "pincode" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Basket_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "BasketItem" (
    "id" TEXT NOT NULL,
    "basketId" TEXT NOT NULL,
    "query" TEXT NOT NULL,
    "quantity" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BasketItem_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Product_matchKey_idx" ON "Product"("matchKey");

-- CreateIndex
CREATE INDEX "Product_normalizedTitle_idx" ON "Product"("normalizedTitle");

-- CreateIndex
CREATE UNIQUE INDEX "Product_providerId_externalId_key" ON "Product"("providerId", "externalId");

-- CreateIndex
CREATE INDEX "SearchQuery_normalizedQuery_pincode_idx" ON "SearchQuery"("normalizedQuery", "pincode");

-- CreateIndex
CREATE INDEX "SearchQuery_createdAt_idx" ON "SearchQuery"("createdAt");

-- CreateIndex
CREATE INDEX "SearchResult_searchQueryId_idx" ON "SearchResult"("searchQueryId");

-- CreateIndex
CREATE INDEX "SearchResult_matchKey_idx" ON "SearchResult"("matchKey");

-- CreateIndex
CREATE INDEX "PriceHistory_productId_recordedAt_idx" ON "PriceHistory"("productId", "recordedAt");

-- CreateIndex
CREATE INDEX "PriceHistory_providerId_recordedAt_idx" ON "PriceHistory"("providerId", "recordedAt");

-- CreateIndex
CREATE INDEX "CacheEntry_expiresAt_idx" ON "CacheEntry"("expiresAt");

-- CreateIndex
CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

-- CreateIndex
CREATE INDEX "BasketItem_basketId_idx" ON "BasketItem"("basketId");

-- AddForeignKey
ALTER TABLE "Product" ADD CONSTRAINT "Product_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "Provider"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SearchQuery" ADD CONSTRAINT "SearchQuery_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SearchResult" ADD CONSTRAINT "SearchResult_searchQueryId_fkey" FOREIGN KEY ("searchQueryId") REFERENCES "SearchQuery"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SearchResult" ADD CONSTRAINT "SearchResult_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "Provider"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SearchResult" ADD CONSTRAINT "SearchResult_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PriceHistory" ADD CONSTRAINT "PriceHistory_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PriceHistory" ADD CONSTRAINT "PriceHistory_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "Provider"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Basket" ADD CONSTRAINT "Basket_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "BasketItem" ADD CONSTRAINT "BasketItem_basketId_fkey" FOREIGN KEY ("basketId") REFERENCES "Basket"("id") ON DELETE CASCADE ON UPDATE CASCADE;
