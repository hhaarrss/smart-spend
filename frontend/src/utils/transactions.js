/**
 * Sort transactions latest-first: date DESC, then created_at DESC.
 */
export function sortTransactionsLatestFirst(transactions = []) {
  return [...transactions].sort((a, b) => {
    const dateDiff = new Date(b.date).getTime() - new Date(a.date).getTime();
    if (dateDiff !== 0) return dateDiff;
    const createdA = new Date(a.created_at || a.date).getTime();
    const createdB = new Date(b.created_at || b.date).getTime();
    return createdB - createdA;
  });
}

/**
 * True when a transaction was created within the last hour.
 */
export function isNewTransaction(tx, now = Date.now()) {
  const created = tx?.created_at || tx?.date;
  if (!created) return false;
  const ts = new Date(created).getTime();
  if (Number.isNaN(ts)) return false;
  return now - ts <= 60 * 60 * 1000;
}

/**
 * Fetches every page of transactions for a month (API max page size is 50).
 */
export async function fetchAllMonthTransactions(listTransactions, month, year) {
  const all = [];
  let page = 1;
  let hasMore = true;

  while (hasMore) {
    const res = await listTransactions({ month, year, page, limit: 50 });
    const batch = Array.isArray(res) ? res : (res?.transactions || res?.data || []);
    all.push(...batch);

    if (Array.isArray(res)) {
      hasMore = false;
    } else {
      hasMore = Boolean(res?.has_more);
    }

    page += 1;
    if (batch.length === 0 || page > 40) break;
  }

  return sortTransactionsLatestFirst(all);
}
