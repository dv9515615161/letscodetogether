/**
 * Seeds the provider table.
 *
 * Optional: the app upserts providers lazily on the first search that touches
 * the database. This exists so a freshly migrated database is inspectable
 * before anyone runs a search.
 *
 * Run with: npm run db:seed
 */

import { PrismaClient } from '@prisma/client';
import { describeProviders } from '../src/providers/registry';

const prisma = new PrismaClient();

async function main() {
  if (!process.env.DATABASE_URL) {
    console.log('DATABASE_URL is not set — nothing to seed. The app runs fine without a database.');
    return;
  }

  for (const provider of describeProviders()) {
    await prisma.provider.upsert({
      where: { id: provider.id },
      create: {
        id: provider.id,
        name: provider.name,
        websiteUrl: provider.websiteUrl,
        enabled: provider.enabled,
        dataSource: provider.dataSource,
      },
      update: {
        name: provider.name,
        websiteUrl: provider.websiteUrl,
        enabled: provider.enabled,
        dataSource: provider.dataSource,
      },
    });
    console.log(`seeded ${provider.name} (${provider.dataSource})`);
  }
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
