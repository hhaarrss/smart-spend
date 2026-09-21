const BANK_SENDER_IDS = [
  'HDFCBK', 'ICICIB', 'SBIPSG', 'SBIINB', 'AXISBK', 'KOTAKB', 'YESBNK',
  'PNBSMS', 'BOBSMS', 'CANBNK', 'INDBNK', 'IDFCFB', 'RBLBNK', 'CITIBK',
  'AMEXIN', 'ONECRD', 'FEDBNK', 'UNIONB', 'PAYTMB', 'GPAY', 'BHIM',
];

const SPAM_KEYWORDS = [
  'save rs', 'earn up to', 'cashback every', 'apply now', 'pre-approved',
  'pre approved', 'loan offer', 'get up to', 'win up to', 'lifetime free',
  'at no extra charge', 'pro pass', 'voucher', 'coupon', 'discount on',
  'mandate collect request', 'request for blocking of funds',
  'otp', 'verification code', 'do not share', 'claim now', 'offer ends',
  'congratulations', 'credit card limit', 'personal loan',
];

const ACTION_KEYWORDS = [
  'debited', 'debitted', 'dr.', 'dr ',
  'credited', 'creditted', 'cr.', 'cr ',
  'transferred', 'spent', 'paid', 'withdrawn',
  'deposited', 'sent to', 'received from', 'received rs', 'credited with', 'refund',
  'auto debit', 'auto-debit', 'emi deducted', 'emi paid', 'purchase of', 'purchase at', 'pos txn',
  'money received', 'salary credited'
];

const DEBIT_KEYWORDS = ['debited', 'debitted', 'dr.', 'dr ', 'spent', 'paid', 'withdrawn',
  'auto debit', 'auto-debit', 'emi deducted', 'emi paid', 'purchase of', 'purchase at', 'pos txn'];
const CREDIT_KEYWORDS = ['credited', 'creditted', 'cr.', 'cr ', 'deposited', 'received from',
  'received rs', 'credited with', 'refund', 'money received', 'salary credited'];

const isTransactionalSender = (sender) => {
  const normalized = sender.toUpperCase();
  return BANK_SENDER_IDS.some((id) => normalized.includes(id));
};

const normalizeSmsDate = (rawDate) => {
  if (!rawDate) {
    return new Date().toISOString();
  }

  const normalized = rawDate.replaceAll('/', '-');
  const monthNames = {
    jan: 0, feb: 1, mar: 2, apr: 3, may: 4, jun: 5,
    jul: 6, aug: 7, sep: 8, oct: 9, nov: 10, dec: 11,
  };
  const parts = normalized.split('-');

  if (parts.length === 3) {
    if (/^\d{4}$/.test(parts[0])) {
      return new Date(Date.UTC(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))).toISOString();
    }

    const year = parts[2].length === 2 ? Number(`20${parts[2]}`) : Number(parts[2]);
    const month = Number.isNaN(Number(parts[1]))
      ? monthNames[parts[1].slice(0, 3).toLowerCase()]
      : Number(parts[1]) - 1;

    if (month !== undefined && !Number.isNaN(year)) {
      return new Date(Date.UTC(year, month, Number(parts[0]))).toISOString();
    }
  }

  const parsed = new Date(rawDate);
  return Number.isNaN(parsed.getTime()) ? new Date().toISOString() : parsed.toISOString();
};

export const parseSmsLocally = (rawSms, sender) => {
  const sms = rawSms.replace(/\s+/g, ' ').trim();
  const lower = sms.toLowerCase();

  if (!isTransactionalSender(sender)) {
    return null;
  }
  if (SPAM_KEYWORDS.some((keyword) => lower.includes(keyword))) {
    return null;
  }
  if (!/(rs\.?|inr|₹)/i.test(sms) || !ACTION_KEYWORDS.some((keyword) => lower.includes(keyword))) {
    return null;
  }

  const amountMatch = sms.match(/(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.\d{1,2})?)/i)
    || sms.match(/([0-9,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr)/i);
  const amount = amountMatch ? Number(amountMatch[1].replaceAll(',', '')) : NaN;
  if (!Number.isFinite(amount) || amount <= 0) {
    return null;
  }

  const transactionType = CREDIT_KEYWORDS.some((keyword) => lower.includes(keyword)) && !DEBIT_KEYWORDS.some((keyword) => lower.includes(keyword))
    ? 'credit'
    : 'debit';
  const merchantMatch = sms.match(/\b(?:at|to|by|from)\s+([A-Za-z0-9 .&@_-]{2,80}?)(?=\s+(?:on|via|upi|ref|rrn|a\/c|acct|card|avl|available|if\b)|[.;,]|$)/i)
    || sms.match(/\binfo[:\s]+([A-Za-z0-9 .&@_-]{2,80}?)(?=\s+(?:on|via|upi|ref|rrn|avl|available|if\b)|[.;,]|$)/i);
  const accountMatch = sms.match(/(?:a\/c|acct|account|card|ending)\s*(?:no\.?)?\s*[xX*#]{0,10}(\d{4})(?!\d)/i)
    || sms.match(/(?:x{2,}|\*{2,})(\d{4})/i);
  const dateMatch = sms.match(/\b(\d{1,2}[-/][A-Za-z]{3}[-/]\d{2,4})\b/i)
    || sms.match(/\b(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b/)
    || sms.match(/\b(\d{4}[-/]\d{1,2}[-/]\d{1,2})\b/);
  // UPI/IMPS/RRN ref — catches 8-22 chars including 12-digit IMPS/RRN
  const upiRefMatch = sms.match(/\b(?:upi\s*(?:ref(?:erence)?(?:\s*no\.?)?|id|no\.?)?|imps\s*(?:ref(?:erence)?(?:\s*no\.?)?)?|rrn|ref(?:erence)?(?:\s*no\.?)?)\s*[:\s-]*([A-Z0-9]{8,22})\b/i);

  return {
    amount,
    transaction_type: transactionType,
    merchant_raw: merchantMatch?.[1]?.trim().replace(/[.,;]+$/, '') || null,
    bank_sender_id: sender.slice(0, 50),
    account_last4: accountMatch?.[1] || null,
    date: normalizeSmsDate(dateMatch?.[1]),
    upi_ref: upiRefMatch?.[1]?.slice(0, 100) || null,
  };
};
