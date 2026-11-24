# Offer package updates

Branch `feat/offer-package-admin-purchase-feed`

## Plan and progress

- [x] Inspect the existing admin editor, student purchase feed, and original database configuration.
- [x] Add a manual discount percentage while preserving existing package prices. Verified server-side calculation, rounding, and the published badge.
- [x] Show a live student card beside the package form, including poster, prices, discount, and places. Verified unsaved field changes and a temporary poster upload in Chrome.
- [x] Let admins duplicate an existing package into an editable draft with its poster. Verified the copy is independent and starts with zero purchases and enquiries.
- [x] Show six recent purchases at a time and load the full history automatically on scroll. Frontend tests reached all 300 records in both the inline list and View all.
- [ ] Run the automated tests and verify the updated app against the original database.

Each completed feature will have its own commit. Package creation, duplication, and publication will be tested with isolated data.
