/** Display helpers shared by server and client components. */

export function formatRupees(value: number): string {
  const rounded = Math.round(value * 100) / 100;
  const hasPaise = Math.abs(rounded % 1) > 0.001;
  return `₹${rounded.toLocaleString('en-IN', {
    minimumFractionDigits: hasPaise ? 2 : 0,
    maximumFractionDigits: 2,
  })}`;
}

export function formatDeliveryTime(minutes: number | undefined): string | undefined {
  if (minutes === undefined) return undefined;
  if (minutes < 60) return `~${minutes} min`;
  if (minutes < 60 * 24) {
    const hours = Math.round(minutes / 60);
    return `~${hours} hr${hours === 1 ? '' : 's'}`;
  }
  const days = Math.round(minutes / (60 * 24));
  return `~${days} day${days === 1 ? '' : 's'}`;
}

export function formatQuantity(quantity: number | undefined, unit: string | undefined): string | undefined {
  if (quantity === undefined || !unit) return undefined;
  const value = Number.isInteger(quantity) ? quantity : Math.round(quantity * 100) / 100;
  return `${value} ${unit}`;
}

export function formatDiscount(price: number, originalPrice: number | undefined): number | undefined {
  if (!originalPrice || originalPrice <= price) return undefined;
  return Math.round(((originalPrice - price) / originalPrice) * 100);
}
