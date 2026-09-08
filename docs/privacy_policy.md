# SmartSpend Privacy Policy

**Last updated:** September 8, 2026  
**Developer:** Harsh Rabadiya  
**Contact:** [harshr4834@gmail.com](mailto:harshr4834@gmail.com)

## 1. Who we are

SmartSpend is an Android expense-tracking application that helps users understand spending by reading and categorizing bank transaction SMS alerts.

## 2. Data we collect

With SMS permission, SmartSpend processes transactional bank SMS messages to extract the amount, transaction type, merchant or payee, masked account identifier, and date. Raw SMS text may be stored with the transaction to support categorization and transaction details.

SmartSpend stores account email, display name, and a bcrypt password hash for password-based accounts. It stores transaction records, categories, merchants, dates, masked account identifiers, budgets, categorization mappings, and optional device notification tokens.

SmartSpend does not use financial or SMS data for advertising and does not sell it to third parties.

## 3. How we use data

We use data to authenticate users, ingest and categorize transactions, provide budgets and spending insights, sync data across devices, send opted-in service notifications, and respond to support requests.

## 4. Storage and security

Data is stored in a PostgreSQL database and accessed through the SmartSpend backend API. Data in transit is protected with HTTPS/TLS. Passwords are hashed with bcrypt. Authentication uses JWT bearer tokens. Full account numbers are not stored; only masked identifiers or the last four digits are retained.

## 5. Data sharing

We do not share personal or financial data for marketing or advertising. Data may be disclosed when required by law, to prevent fraud or abuse, or to service providers required to operate the service, such as cloud hosting and notification providers.

## 6. Your controls

You can view, edit, recategorize, and delete your transactions in the app. You can permanently delete your account from **Profile -> Delete My Account**. Account deletion removes the account, transactions, budgets, notification records, and merchant categorization mappings from our systems.

If you cannot access the app, request deletion at [harshr4834@gmail.com](mailto:harshr4834@gmail.com) from the email address associated with your account. We may retain limited information only where required for fraud prevention, security, or legal compliance, and will delete it when that requirement ends.

## 7. Permissions

SmartSpend requests SMS read and receive permissions to detect bank transaction alerts. Internet access is required to authenticate and sync data. SMS permission can be denied; manual transaction entry remains available.

## 8. Children's privacy

SmartSpend is not directed to children under 13, or the applicable minimum age in the user's jurisdiction, and does not knowingly collect data from children.

## 9. Changes to this policy

Material changes will be reflected here with an updated date and may be communicated in the app.

## 10. Contact

For privacy questions or data requests, contact [harshr4834@gmail.com](mailto:harshr4834@gmail.com).
