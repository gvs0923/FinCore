import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { fetchOrders } from "../api/adminClient";
import { formatMoney } from "../../lib/money";

export default function OrdersPage() {
  const [page, setPage] = useState(0);
  const orders = useQuery({ queryKey: ["orders", page], queryFn: () => fetchOrders(page) });
  return <section className="mx-auto max-w-7xl space-y-4 px-6 py-8">
    <h1 className="text-2xl font-bold">Orders</h1>
    {orders.isPending && <p role="status">Loading orders…</p>}
    {orders.isError && <p role="alert">{orders.error.message}</p>}
    {orders.data?.length === 0 && <p>No orders on this page.</p>}
    <table className="w-full text-left text-sm">
      <thead><tr><th>Order</th><th>Status</th><th>Amount</th><th>Created</th></tr></thead>
      <tbody>{orders.data?.map(order => <tr key={order.id} className="border-b">
        <td className="py-3"><Link className="text-indigo-500 underline" to={`/payments/${order.id}`}>{order.merchantRef}</Link></td>
        <td>{order.status}</td><td>{formatMoney(order.amount, order.currency)}</td>
        <td>{new Date(order.createdAt).toLocaleString()}</td>
      </tr>)}</tbody>
    </table>
    <div className="flex gap-4">
      <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
      <span>Page {page + 1}</span>
      <button disabled={orders.isPending || orders.isError || (orders.data?.length ?? 0) < 50}
        onClick={() => setPage(page + 1)}>Next</button>
    </div>
  </section>;
}
