export default function StubPage({ title }: { title: string }) {
  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <h1 className="mb-2 text-2xl font-bold text-slate-900 dark:text-white">
        {title}
      </h1>
      <p className="text-sm text-slate-400">
        Detailed {title.toLowerCase()} drill-down ships in Phase 5.
      </p>
    </div>
  );
}