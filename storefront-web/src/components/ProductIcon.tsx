import type { ReactNode } from "react";
import type { Product } from "../products";

const icons: Record<Product["icon"], ReactNode> = {
  card: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-8 w-8">
      <rect x="2" y="5" width="20" height="14" rx="2" />
      <path d="M2 10h20" />
    </svg>
  ),
  shield: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-8 w-8">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  ),
  wallet: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-8 w-8">
      <rect x="2" y="6" width="20" height="14" rx="2" />
      <path d="M2 10h20" />
      <circle cx="17" cy="15" r="1.5" />
    </svg>
  ),
  bolt: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-8 w-8">
      <path d="M13 2 4.1 12.9a.5.5 0 0 0 .4.8H12l-1 8.3 8.9-11a.5.5 0 0 0-.4-.8H12l1-8.2Z" />
    </svg>
  ),
  code: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-8 w-8">
      <polyline points="16 18 22 12 16 6" />
      <polyline points="8 6 2 12 8 18" />
      <line x1="14" y1="4" x2="10" y2="20" />
    </svg>
  ),
  rocket: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="h-8 w-8">
      <path d="M4.5 16.5c-1.5 1.3-2 3.5-2 3.5s2.2-.5 3.5-2c.7-.8.7-2.1-.1-2.9a2.1 2.1 0 0 0-3.4 1.4Z" />
      <path d="M12 15l-3-3 5.6-5.6a7.6 7.6 0 0 1 5.7-2.2l.6.1-.1-.6a7.6 7.6 0 0 0-2.2 5.7L12 15Z" />
      <path d="M9 12H4s.6-3.2 4-4m7 7v5s3.2-.6 4-4" />
    </svg>
  ),
};

export default function ProductIcon({ icon }: { icon: Product["icon"] }) {
  return icons[icon];
}
